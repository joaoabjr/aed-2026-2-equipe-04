# Entrega — Aula 04

## O que foi feito nesta etapa

- **ADR-003** ([`docs/adr/ADR-003-chave-de-particao.md`](../adr/ADR-003-chave-de-particao.md)): formaliza uma decisão que já estava implícita no código desde a aula 02 — o sistema usa **duas** chaves de partição, `animalId` (nível do animal) e `loteId` (nível do lote) — e explica o que cada uma garante sem repartir, e o que nenhuma das duas garante sozinha.
- **Agregação:** nenhum código novo. Reaproveitamos o `PesagemAgregadaPorMinutoListener` (aula 03) — mesma janela, mesmo `group.id`. O que esta etapa acrescenta é a justificativa formal de por que essa chave (`animalId`) não atrapalha essa agregação, agora registrada no ADR-003.

## O que foi agregado, com qual janela, e por quê

**O que:** peso médio do rebanho, por janela de 1 minuto (mesma pergunta de negócio da aula 03: acompanhar a variação/ganho de peso do rebanho ao longo do dia).

**Por que essa janela, e não uma amarrada ao lote:** o ADR-003 mostra que o sistema tem duas chaves de partição — `animalId` (pesagem, vacinação, embarque) e `loteId` (formação de lote, movimentação de pasto). Peso médio do rebanho é, por natureza, uma pergunta que atravessa **todos** os animais, não um lote específico — então precisa ler o tópico inteiro (todas as partições de `animalId`), o que o `group.id` próprio (`pesagem-agregador`) já garante desde a aula 03.

A chave `animalId` não impede essa agregação porque a janela de 1 minuto agrupa por tempo de ocorrência (`ocorridoEm`), não pela partição de origem do evento — o agregador não depende de nenhuma ordem *entre* animais diferentes, só do conteúdo (`pesoKg`, `ocorridoEm`) de cada evento dentro da janela. É o caso concreto que a seção "Decisão" do ADR-003 cita como prova de que a chave escolhida não atrapalha esse tipo de pergunta.

**O limite que isso deixa em aberto:** se a pergunta fosse "peso médio **por lote**" em vez de "peso médio do rebanho", a chave relevante passaria a ser `loteId`, não `animalId` — e aí esbarraríamos exatamente na lacuna que a seção "Consequências aceitas" do ADR-003 documenta: hoje não há, sem repartir ou juntar os dois streams do lado do consumidor, como saber a quais lotes os animais pertenciam no momento exato de cada pesagem (`PesagemRegistrada` não carrega `loteId`). Fica registrado como próximo passo possível, não como pendência desta entrega — nenhuma pergunta de negócio hoje pede essa agregação por lote.

## Onde está cada coisa

| O quê | Onde |
|---|---|
| ADR-003 (chave de partição) | [`docs/adr/ADR-003-chave-de-particao.md`](../adr/ADR-003-chave-de-particao.md) |
| Agregador (peso médio por minuto) | [`servico-manejo/.../controller/PesagemAgregadaPorMinutoListener.java`](../../servico-manejo/src/main/java/br/pucminas/aed/manejo/controller/PesagemAgregadaPorMinutoListener.java) |
| Entrega da aula 03 (mesma agregação, primeira versão) | [`docs/entregas/aula-03.md`](aula-03.md) |
| Registro de uso de IA | [`docs/IA.md`](../IA.md), seção `## Aula 04` |

## Como rodar

Nenhum comando novo nesta etapa — ver [`README.md`](../../README.md), seção "Como observar o agregador de pesagem" (aula 03).

## Nota sobre o prazo

Esta entrega estava prevista para 30/08 e está sendo produzida em 15/09, com autorização do professor para entrega tardia (conversa direta, fora do fluxo de segunda chamada do Canvas, cuja janela de 7 dias já havia se encerrado quando a lacuna foi percebida).
