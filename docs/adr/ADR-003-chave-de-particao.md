#ADR-003 — Chave de partição

## Status

Aceita · 2026-09-15 · Equipe 04

## Contexto

Desde a aula 02, cada evento novo já nasce com uma chave de partição escolhida — está registrada, evento a evento, nos `docs/contrato*.md`. O que nunca foi escrito é a decisão *como um todo*: o sistema não usa uma chave de partição, usa **duas**, dependendo do nível do fato, e ninguém tinha ainda examinado, de propósito, o que cada uma garante e o que nenhuma das duas garante sozinha. Este ADR formaliza essa decisão retroativamente, e existe **antes** de qualquer novo código de agregação que dependa dela — é essa ordem que o `git log --reverse` desta etapa precisa mostrar.

Hoje o sistema publica em dois níveis de granularidade:

- **Nível do animal:** `PesagemRegistrada`, `VacinacaoRegistrada`, `AnimalEmbarcadoParaAbate` — cada um descreve um fato que acontece com UM animal específico.
- **Nível do lote:** `LoteFormado`, `LoteMovidoDePasto` — cada um descreve um fato que acontece com uma composição de vários animais tratados como unidade de manejo.

## Decisão

**Duas chaves de partição, uma por nível — não uma chave única para o sistema inteiro.**

| Nível | Eventos | Chave | Pergunta que responde sem repartir |
|---|---|---|---|
| Animal | `PesagemRegistrada`, `VacinacaoRegistrada`, `AnimalEmbarcadoParaAbate` | `animalId` | "Qual foi a sequência real de fatos deste animal?" — necessário para a curva de peso (a tendência depende da ordem das leituras), para saber qual é a vacinação vigente (a mais recente com validade não vencida) e para garantir que o embarque seja processado depois de qualquer pesagem que o precedeu. |
| Lote | `LoteFormado`, `LoteMovidoDePasto` | `loteId` | "Qual foi a sequência real de fatos deste lote?" — necessário para saber em qual pasto um lote está *agora* (a movimentação mais recente vence) e para não processar uma movimentação antes da formação do próprio lote. |

Animais diferentes (ou lotes diferentes) podem ser processados fora de ordem entre si sem problema — é por isso que `PesagemRegistrada` usa 3 partições em vez de uma só: paralelismo entre animais é livre, o que não pode variar é a ordem **dentro** de cada animal.

O agregador `PesagemAgregadaPorMinutoListener` (aula 03) é o caso que confirma que essa escolha não atrapalha tudo: peso médio do rebanho por minuto é uma pergunta que **não depende** da ordem entre animais diferentes, só do conteúdo de cada evento dentro de uma janela de tempo — por isso ele lê o tópico inteiro, com `animalId` como chave, sem precisar de nenhuma outra partição.

## Alternativas consideradas

**Uma chave única para o sistema inteiro (sempre `animalId`, inclusive nos eventos de lote).** Recusada: um evento de lote (`LoteFormado`, `LoteMovidoDePasto`) não tem um único `animalId` — tem uma lista de animais, ou nenhum, dependendo do momento. Forçar `animalId` como chave desses eventos exigiria publicar o mesmo fato uma vez por animal do lote, distorcendo o fato em N fatos que não são o que realmente aconteceu (o lote se moveu, não cada animal individualmente).

**Chave aleatória / round-robin nos tópicos de nível animal.** Recusada pelo mesmo motivo do ADR-002 original sobre `PesagemRegistrada`: sem uma chave estável, duas leituras de peso do mesmo animal podem ser processadas fora de ordem, e a curva de peso deixa de ser confiável.

## Consequências aceitas

**A pergunta que o sistema não responde sem repartir (ou sem aceitar ordem aproximada): a ordem causal exata *entre* um fato de lote e um fato de animal.**

Se um lote é movido de pasto (`LoteMovidoDePasto`, partição por `loteId`) no mesmo instante em que um dos seus animais está sendo pesado (`PesagemRegistrada`, partição por `animalId`), o Kafka garante a ordem dentro de cada um desses dois tópicos separadamente (mas **não garante nada sobre a ordem relativa entre os dois**), porque estão em tópicos diferentes, particionados por chaves diferentes, consumidos por grupos diferentes. Uma pergunta como "esse animal já tinha sido pesado no pasto de origem antes do lote ser movido, ou só depois de chegar no destino?" não tem resposta garantida pela ordem de entrega - só pela comparação de `ocorridoEm` entre os dois eventos, que é ordem por relógio de negócio, não ordem estrutural do broker.

Isso é aceito porque nenhuma decisão de negócio hoje depende dessa correlação fina entre os dois níveis. Decisões de dieta e formação de lote usam o histórico de peso por animal (que tem ordem garantida) e decisões de manejo de pasto usam o histórico de movimentação por lote (que também tem ordem garantida), cada um dentro do seu próprio nível. Se essa correlação vier a importar (por exemplo, para auditar se um animal foi pesado no pasto errado), a solução não é uma chave de partição nova — é uma projeção que junte os dois streams por `animalId` e `loteId` do lado do consumidor, aceitando a ordem por `ocorridoEm` em vez de ordem garantida pelo broker.
