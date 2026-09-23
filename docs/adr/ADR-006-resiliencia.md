#ADR-006 — Resiliência: DLQ permanente e a coreografia da compensação

## Status

Aceita · 2026-09-23 · Equipe 04

## Contexto

Em `servico-manejo`, os consumidores de pesagem (`PesagemListener`) e de embarque (`RejeicaoEmbarqueListener`) usam ack-mode MANUAL e confirmam o offset só depois do commit da transação — at-least-once com idempotência por `eventoId` (ADR-002). Isso resolve a *reentrega* de mensagens boas, mas deixa uma lacuna aberta para a mensagem que **nunca** vai conseguir processar:

- **poison message**: o JSON não desserializa (schema quebrado no fio, bytes corrompidos) — a exceção de desserialização se repete a cada entrega;
- **erro de regra de negócio persistente**: por exemplo, um `AnimalRejeitadoNoEmbarque` referenciando um animal que não existe — a compensação lança `IllegalArgumentException` sempre.

Sem um tratamento, esse record rejeitado é entregue de novo a cada `poll`/rebalance e depois de esgotadas as tentativas do handler o caminho padrão do Spring Kafka é descartá-lo **em silêncio** (ou, pior, segurar o consumo da partição). Os dois extremos violam o mesmo princípio do ADR-002: o sistema não corrige o passado nem perde informação confiável — "o que aconteceu" precisa continuar registrado em algum lugar.

Ao mesmo tempo, a Parte B introduz a primeira Saga do sistema: a recusa do animal no frigorífico (`AnimalRejeitadoNoEmbarque`) precisa desfazer um efeito já aplicado (retorno ao lote, reavaliação de dieta, encerramento da venda) sem nenhuma transação atravessando `servico-expedicao` e `servico-manejo`. As duas decisões — o que fazer quando um consumidor falha, e como a compensação se coordena — são resiliência do mesmo sistema, por isso vivem no mesmo ADR.

## Decisão — o caminho de falha: DLQ permanente

**Adotar uma DLQ (Dead Letter Queue) PERMANENTE, em PostgreSQL, para os dois fluxos citados.**

Quando o processamento falha e as tentativas configuradas se esgotam, o `DefaultErrorHandler` do container chama um recoverer próprio (`DlqRecoverer`), que grava a falha na tabela `evento_dlq` — append-only, uma linha por evento que deixou de ser processado, com o payload (ou os bytes originais, no caso de poison message), o **envelope CloudEvents inteiro** capturado dos headers do record (não só `ce_id`/`ce_type`: também `ce_specversion`, `ce_source`, `ce_time`, `ce_subject`, `ce_datacontenttype`, o que existir no record), a exceção raiz e a posição Kafka da mensagem. Só depois dessa gravação o container confirma o offset do tópico original (`ackAfterHandle` do `DefaultErrorHandler`, que vale mesmo com ack-mode MANUAL): a partição **não trava** e o registro **permanece** para auditoria e reprocessamento.

**O caminho de reprocessamento:** `POST /api/dlq/{id}/reprocessar` (`DlqService.reprocessar`) reenvia o payload gravado de volta ao **tópico original**, restaurando os cabeçalhos capturados — o mesmo consumidor que falhou da primeira vez recebe o evento de novo, pelo fluxo normal, sem precisar de um caminho de código separado só para reprocessamento. Não há automação: alguém decide, depois de corrigir a causa raiz, que aquele evento específico deve voltar. Reprocessar não altera nem apaga a linha de `evento_dlq` — cada tentativa vira uma linha nova em `dlq_reprocessamento` (append-only também), preservando o registro original intacto para auditoria. Reprocessar o mesmo evento mais de uma vez não duplica o efeito de negócio: a idempotência por `eventoId` em `evento_processado` (ADR-002) é quem garante isso — reprocessamento manual não é um caminho novo de efeito, é só mais uma forma de a mensagem chegar ao consumidor.

**Por que tabela, e não um tópico Kafka `.dlq`:** o destino das DLQs típicas (um tópico morto retido no broker) tem retenção finita e depende do broker para ser lido. A tabela é o registro literalmente "permanente" do ADR-002 — coexiste com o `event_store` do ADR-005 como fonte de verdade do que aconteceu —, é consultável diretamente no banco que o `servico-manejo` já opera e é testável com H2, sem broker, no mesmo espírito dos testes do serviço. O recoverer escreve do próprio container, e se essa escrita falhar ele propaga o erro: o offset não é confirmado e a posição volta a ser entregue — nunca se perde a mensagem em silêncio (ver "Consequências aceitas").

**Escopo:** somente os dois consumidores pedidos — pesagem (`kafkaListenerContainerFactory`, grupo `manejo`) e embarque (`rejeicaoEmbarqueKafkaListenerContainerFactory`, grupo `manejo-rejeicao-embarque`). O agregador de pesagem (observabilidade, não persiste efeito) e os consumidores de vacinação/event store não fazem parte desta decisão; o mecanismo é reutilizável por cima da mesma `evento_dlq`.

**Retry antes da DLQ:** `FixedBackOff(1000ms, 2)` — dois reagendamentos curtos absorvem falhas transitórias (ex. deadlock/transição de rede); o que falha de verdade vai para a DLQ, não vira reprocessamento infinito.

**Deduplicação da DLQ:** `UNIQUE (origem_topico, particao, deslocamento)`. A posição Kafka existe para qualquer mensagem — inclusive poison message sem `ce_id` aproveitável — e é estável entre reentregas: se o processo morrer entre a gravação na DLQ e o commit do offset, a reentrega insere a mesma posição e a UNIQUE aceita a primeira linha e descarta a segunda.

## Decisão — a Saga de compensação: coreografia

**A Saga do `AnimalRejeitadoNoEmbarque` é coreografada, não orquestrada — não existe nenhum componente central coordenando os passos.**

`servico-expedicao` detecta a recusa do frigorífico e publica `AnimalRejeitadoNoEmbarqueEvent`. Ele não sabe, e não precisa saber, que isso vai desencadear retorno ao lote, reavaliação de dieta e encerramento de venda — só sabe que um fato aconteceu e publica esse fato. `servico-manejo` reage de forma totalmente independente, consumindo o evento pelo seu próprio grupo (`manejo-rejeicao-embarque`) e decidindo, sozinho, os três efeitos da compensação (`CompensacaoEmbarqueService`). Nenhum dos dois serviços chama o outro diretamente, nem espera resposta síncrona: a saga inteira é "cada um reage ao que já aconteceu".

**O que se ganhou:** os dois serviços continuam publicáveis e testáveis de forma independente — `servico-expedicao` não precisa saber que `servico-manejo` existe, e vice-versa; se `servico-manejo` estiver fora do ar quando a recusa acontece, o evento fica retido no tópico e a compensação roda assim que o consumidor voltar, sem que `servico-expedicao` precise saber ou tratar isso. Não há um único ponto de falha coordenando a saga.

**O que se perdeu:** não existe nenhum lugar que enxergue "o progresso da saga como um todo" — para saber se a compensação de uma recusa específica já rodou, é preciso consultar o estado do `servico-manejo` diretamente (a dieta do animal, se a venda foi encerrada), não existe um "status da saga X". Também não há timeout nem cancelamento automático da saga: se `servico-manejo` nunca processar aquele evento (por exemplo, por estar em erro persistente — ver abaixo), não existe um relógio central que perceba isso e alerte alguém; quem percebe é quem for procurar.

**O que acontece se a própria compensação falhar:** a compensação (`CompensacaoEmbarqueService`, rodando em `RejeicaoEmbarqueListener`) está dentro do MESMO escopo da DLQ descrita acima. Se a compensação lançar uma exceção persistente (ex. animal referenciado não existe em `servico-manejo`), o record esgota o retry e cai na `evento_dlq` — exatamente como uma falha de pesagem. Isso significa que a saga fica **incompleta e visível**: a recusa foi registrada por `servico-expedicao`, mas o retorno ao lote/reavaliação de dieta/encerramento de venda não aconteceu, e há uma linha na DLQ dizendo exatamente isso, com o evento inteiro preservado para reprocessar depois que a causa for corrigida. Não escolhemos um mecanismo de compensação-da-compensação separado: reaproveitar a mesma DLQ evita inventar um segundo conceito para o mesmo problema (evento que não pôde ser processado).

## Alternativas consideradas

**DLQ como tópico Kafka (`<topico>.dlq`) via `DeadLetterPublishingRecoverer`.** É o padrão canônico do Spring Kafka. Recusada pelo critério acima: retenção finita do broker, leitura dependente de outra ferramenta e, no caso de poison message, a publicação morta depende de o broker aceitar a re-serialização dos bytes originais — aqui o "registro permanente" não deve depender de mais um tópico nem da política de retenção.

**Try/catch no corpo de cada listener, gravando a DLQ e confirmando o ack.** Descartada porque não cobre poison message: em erro de desserialização o método do listener nem chega a ser invocado — a exceção é tratada pelo error handler do container de qualquer forma. O recoverer único cobre os dois casos sem duplicar a lógica de extração de payload em cada listener.

**`evento_processado` como DLQ (reutilizar a memória de idempotência).** Refutada: `evento_processado` é só uma PK de `evento_id`; não guarda payload nem motivo, e "marcar como processado" algo que falhou corromperia o significado da idempotência (o evento voltaria a ser silenciosamente ignorado sem nunca ter produzido o efeito de negócio).

**Orquestração da Saga** (um coordenador central que chama `servico-expedicao` e `servico-manejo` em sequência, sabendo o estado de cada passo). Recusada: o case tem só dois passos e um caminho de compensação (não uma cadeia longa com múltiplas decisões condicionais), então o ganho de visibilidade centralizada não paga o custo de introduzir um componente novo, com seu próprio estado e seu próprio caminho de falha, só para coordenar dois participantes que já se comunicam bem por evento. Se a saga crescer (mais passos, mais compensações encadeadas), essa decisão precisaria ser revisitada — é exatamente o tipo de "o que se perdeu" nomeado acima.

## Consequências aceitas

- **Mais uma tabela e mais um caminho de falha.** Se a DLQ falhar ao gravar, a posição não é confirmada e é reentregue — sem perda silenciosa, mas com CPU/IO de retry. Como a DLQ é a mesma fonte de dados do efeito de negócio (PostgreSQL do `servico-manejo`), uma queda que inviabiliza a DLQ derruba o serviço do mesmo jeito; a DLQ não é o elo novo mais fraco.
- **A DLQ acumula e não auto-reprocessa.** Existe um caminho manual (`POST /api/dlq/{id}/reprocessar`), mas nada dispara sozinho — quem corrige a causa raiz decide, evento a evento, que aquilo deve voltar. Apagar é decisão humana que este sistema nem sequer expõe; a DLQ só cresce.
- **O payload de erro de regra é a visão do `servico-manejo`.** Para poison message gravamos os bytes originais do fio; para erro de regra resserializamos o objeto desserializado, sem os campos que este consumidor tolerante não declara (ex. `metodoDePesagem`). É suficiente para reproduzir e corrigir a falha, não para perder o evento por completo.
- **A coreografia não dá visibilidade única do progresso da saga.** Como descrito acima: descobrir se uma compensação específica já rodou exige olhar o estado de `servico-manejo` diretamente, não existe um painel de "sagas em andamento". Aceitamos isso porque o volume e a complexidade de hoje (uma saga, dois passos) não justificam o custo de um orquestrador — mas é uma consequência real, não hipotética, e fica documentada aqui para quando o cenário mudar.