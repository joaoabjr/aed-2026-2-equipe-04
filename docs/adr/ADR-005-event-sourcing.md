#ADR-005 — Event sourcing do agregado Lote (localização de pasto)

## Status

Aceita · 2026-09-15 · Equipe 04

## Contexto

Desde que os eventos `LoteFormado` e `LoteMovidoDePasto` foram introduzidos, eles são publicados no Kafka — mas nenhum consumidor os lê. O `ManejoPastoService.mover()` valida que o lote existe e que origem e destino são diferentes, publica o evento, e para por aí: **não existe, em lugar nenhum do sistema, uma tabela ou coluna que responda "em qual pasto está o lote X agora"**. Não é uma lacuna de bug — é uma pergunta que hoje literalmente não tem fonte de dado nenhuma além de reler manualmente o tópico Kafka do começo.

Isso é exatamente o tipo de situação para a qual event sourcing existe: um histórico de fatos ordenados (`LoteFormado` uma vez, `LoteMovidoDePasto` zero ou mais vezes) de onde o estado atual pode ser **derivado**, em vez de guardado e sobrescrito.

## Decisão

**Agregado escolhido: `Lote`, no recorte específico da sua localização de pasto** — não o `Lote` inteiro (numeração, fazenda, animais continuam sendo geridos como CRUD comum pelas tabelas `lote`/`animal` já existentes). O stream é identificado por `loteId`, a mesma chave de partição que o ADR-003 já usa para os eventos de nível de lote.

**Por que este agregado:** é o único, entre os candidatos do sistema, para o qual não existe hoje nenhuma outra fonte de verdade para o estado atual. `Animal` já tem histórico de peso e de vacinação em tabelas próprias (`historico_pesagem`, `historico_vacinacao`) — o ganho de reescrevê-los como event sourcing seria menor, porque o problema que event sourcing resolve (estado atual sem lugar nenhum para morar) já está resolvido ali de outra forma. Para "onde está o lote agora", não há alternativa hoje — o ganho é concreto, não hipotético.

**O event store:** tabela append-only `lote_evento_store`, com uma linha por evento do stream de um lote:

| Coluna | O que guarda |
|---|---|
| `lote_id` | identifica o stream (mesma chave de partição do tópico) |
| `versao` | posição do evento dentro do stream do lote, começando em 1 |
| `evento_id` | o `eventoId` do CloudEvents — idempotência, mesmo padrão dos outros consumidores |
| `tipo_evento` | `LoteFormado` ou `LoteMovidoDePasto` |
| `ocorrido_em` | `ocorridoEm` do evento |
| `payload` | o corpo do evento, serializado, para a projeção poder ser reconstruída sem depender do Kafka ainda ter retido a mensagem |

A chave primária é `(lote_id, versao)`. É essa restrição que detecta concorrência: para anexar o evento seguinte de um lote, o consumidor lê a versão mais alta já gravada para aquele `lote_id` e tenta inserir `versao + 1`; se duas escritas concorrentes partirem do mesmo estado e tentarem inserir a mesma versão, o banco aceita a primeira e rejeita a segunda por violação de chave — quem perde relê e tenta de novo com a versão atualizada. Não precisa de lock explícito nem de coluna de versão otimista separada: a própria numeração sequencial do stream já é o mecanismo.

**A projeção:** `lote_localizacao_atual`, uma linha por lote, com o pasto atual. É construída dobrando (fold) os eventos do stream em ordem de `versao`: `LoteFormado` cria a linha do lote sem pasto atribuído (ele existe, mas ainda não foi movido para lugar nenhum); cada `LoteMovidoDePasto` subsequente substitui o pasto atual pelo `pastoDestinoId` daquele evento. É descartável por definição — pode ser apagada e reconstruída inteira relendo o event store do início, sem perder informação, porque o event store (não a projeção) é a fonte da verdade.

## Alternativas consideradas

**Uma coluna `pasto_atual_id` mutável na tabela `lote` já existente, atualizada por `UPDATE` a cada movimentação.** Recusada. É o mesmo antipadrão que o projeto já evita em outro contexto (a Parte B do projeto final proíbe corrigir o passado com `UPDATE`/`DELETE` numa saga — aqui o princípio é o mesmo: sobrescrever apaga a única coisa confiável que se tinha, o histórico de por onde o lote passou). E é frágil: se a coluna corromper — bug, migração malfeita, escrita concorrente sem proteção — não haveria como saber o valor correto, porque nenhum histórico intermediário teria sido guardado em lugar nenhum além do tópico Kafka, cuja retenção não é infinita.

**Event sourcing do agregado `Animal` em vez de `Lote`.** Descartada por escopo: a aula pede UM agregado, UMA projeção, não o projeto inteiro reescrito. `Animal` já tem bastante lógica CRUD acoplada (`AnimalController`, `AnimalService`, vínculo com `Venda`), e — como já dito — já tem histórico de peso e vacinação persistidos de outra forma. `Lote`, no recorte de localização, é o candidato mais contido: hoje só dois tipos de evento (`LoteFormado`, `LoteMovidoDePasto`), nenhum consumidor concorrente para coordenar, event store pequeno o bastante para auditar de ponta a ponta.

## Consequências aceitas

**A leitura da localização de um lote deixa de ser um `SELECT` direto contra uma coluna sempre atualizada.** Passa a depender de uma projeção mantida de forma assíncrona por um consumidor Kafka — existe uma janela real (não instantânea) entre "o lote foi movido" e "a projeção reflete isso". Quem consultar a localização durante essa janela vê o pasto anterior. Aceitamos essa defasagem porque nenhuma decisão de manejo depende de saber a localização exata no milissegundo em que ela mudou — é informação de acompanhamento, não gatilho de uma regra automática (ver a defasagem tolerada, registrada por tela, em `docs/entregas/aula-05.md`).

**Mais uma tabela para operar, mais um caminho de falha.** Se o consumidor que grava no event store cair fora de sincronia com a projeção por qualquer motivo (bug, deploy no meio de um lote de eventos), a correção não é automática — alguém precisa rodar a reconstrução por replay manualmente. Não existe hoje alerta nem verificação periódica de que a projeção está em dia com o event store; é um gap conhecido, não resolvido nesta etapa.

**Sem snapshot — o replay sempre lê o stream inteiro do zero.** Para os volumes atuais (um lote muda de pasto algumas vezes ao longo do confinamento, não milhares), isso é irrelevante. Se o padrão de uso mudar — lotes movidos com muita frequência, streams longos —, reconstruir por replay fica proporcionalmente mais lento, e a decisão de adicionar snapshots teria que ser revisitada.
