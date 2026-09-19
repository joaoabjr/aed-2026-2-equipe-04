#ADR-006 — DLQ permanente para os fluxos de pesagem e embarque

## Status

Aceita · 2026-09-18 · Equipe 04

## Contexto

Em `servico-manejo`, os consumidores de pesagem (`PesagemListener`) e de embarque (`RejeicaoEmbarqueListener`) usam ack-mode MANUAL e confirmam o offset só depois do commit da transação — at-least-once com idempotência por `eventoId` (ADR-002). Isso resolve a *reentrega* de mensagens boas, mas deixa uma lacuna aberta para a mensagem que **nunca** vai conseguir processar:

- **poison message**: o JSON não desserializa (schema quebrado no fio, bytes corrompidos) — a exceção de desserialização se repete a cada entrega;
- **erro de regra de negócio persistente**: por exemplo, um `AnimalRejeitadoNoEmbarque` referenciando um animal que não existe — a compensação lança `IllegalArgumentException` sempre.

Sem um tratamento, esse record rejeitado é entregue de novo a cada `poll`/rebalance e depois de esgotadas as tentativas do handler o caminho padrão do Spring Kafka é descartá-lo **em silêncio** (ou, pior, segurar o consumo da partição). Os dois extremos violam o mesmo princípio do ADR-002: o sistema não corrige o passado nem perde informação confiável — "o que aconteceu" precisa continuar registrado em algum lugar.

## Decisão

**Adotar uma DLQ (Dead Letter Queue) PERMANENTE, em PostgreSQL, para os dois fluxos citados.**

Quando o processamento falha e as tentativas configuradas se esgotam, o `DefaultErrorHandler` do container chama um recoverer próprio (`DlqRecoverer`), que grava a falha na tabela `evento_dlq` — append-only, uma linha por evento que deixou de ser processado, com o payload (ou os bytes originais, no caso de poison message), os headers CloudEvents `ce_id`/`ce_type`, a exceção raiz e a posição Kafka da mensagem. Só depois dessa gravação o container confirma o offset do tópico original (`ackAfterHandle` do `DefaultErrorHandler`, que vale mesmo com ack-mode MANUAL): a partição **não trava** e o registro **permanece** para auditoria e reprocessamento manual. Não existe endpoint de escrita nem de limpeza da DLQ — só leitura (`GET /api/dlq`, ver `docs/openapi-manejo.yaml`).

**Por que tabela, e não um tópico Kafka `.dlq`:** o destino das DLQs típicas (um tópico morto retido no broker) tem retenção finita e depende do broker para ser lido. A tabela é o registro literalmente "permanente" do ADR-002 — coexiste com o `event_store` do ADR-005 como fonte de verdade do que aconteceu —, é consultável diretamente no banco que o `servico-manejo` já opera e é testável com H2, sem broker, no mesmo espírito dos testes do serviço. O recoverer escreve do próprio container, e se essa escrita falhar ele propaga o erro: o offset não é confirmado e a posição volta a ser entregue — nunca se perde a mensagem em silêncio (ver "Consequências aceitas").

**Escopo:** somente os dois consumidores pedidos — pesagem (`kafkaListenerContainerFactory`, grupo `manejo`) e embarque (`rejeicaoEmbarqueKafkaListenerContainerFactory`, grupo `manejo-rejeicao-embarque`). O agregador de pesagem (observabilidade, não persiste efeito) e os consumidores de vacinação/event store não fazem parte desta decisão; o mecanismo é reutilizável por cima da mesma `evento_dlq`.

**Retry antes da DLQ:** `FixedBackOff(1000ms, 2)` — dois reagendamentos curtos absorvem falhas transitórias (ex. deadlock/transição de rede); o que falha de verdade vai para a DLQ, não vira reprocessamento infinito.

**Deduplicação da DLQ:** `UNIQUE (origem_topico, particao, deslocamento)`. A posição Kafka existe para qualquer mensagem — inclusive poison message sem `ce_id` aproveitável — e é estável entre reentregas: se o processo morrer entre a gravação na DLQ e o commit do offset, a reentrega insere a mesma posição e a UNIQUE aceita a primeira linha e descarta a segunda.

## Alternativas consideradas

**DLQ como tópico Kafka (`<topico>.dlq`) via `DeadLetterPublishingRecoverer`.** É o padrão canônico do Spring Kafka. Recusada pelo critério acima: retenção finita do broker, leitura dependente de outra ferramenta e, no caso de poison message, a publicação morta depende de o broker aceitar a re-serialização dos bytes originais — aqui o "registro permanente" não deve depender de mais um tópico nem da política de retenção.

**Try/catch no corpo de cada listener, gravando a DLQ e confirmando o ack.** Descartada porque não cobre poison message: em erro de desserialização o método do listener nem chega a ser invocado — a exceção é tratada pelo error handler do container de qualquer forma. O recoverer único cobre os dois casos sem duplicar a lógica de extração de payload em cada listener.

**`evento_processado` como DLQ (reutilizar a memória de idempotência).** Refutada: `evento_processado` é só uma PK de `evento_id`; não guarda payload nem motivo, e "marcar como processado" algo que falhou corromperia o significado da idempotência (o evento voltaria a ser silenciosamente ignorado sem nunca ter produzido o efeito de negócio).

## Consequências aceitas

- **Mais uma tabela e mais um caminho de falha.** Se a DLQ falhar ao gravar, a posição não é confirmada e é reentregue — sem perda silenciosa, mas com CPU/IO de retry. Como a DLQ é a mesma fonte de dados do efeito de negócio (PostgreSQL do `servico-manejo`), uma queda que inviabiliza a DLQ derruba o serviço do mesmo jeito; a DLQ não é o elo novo mais fraco.
- **A DLQ acumula e não auto-reprocessa.** Registro é o papel dela; quem corrige a causa raiz reprocessa manualmente (sem automação nesta etapa — mesmo gap do replay do ADR-005). Apagar é decisão humana, não do fluxo normal.
- **O payload de erro de regra é a visão do `servico-manejo`.** Para poison message gravamos os bytes originais do fio; para erro de regra resserializamos o objeto desserializado, sem os campos que este consumidor tolerante não declara (ex. `metodoDePesagem`). É suficiente para reproduzir e corrigir a falha, não para perder o evento por completo.