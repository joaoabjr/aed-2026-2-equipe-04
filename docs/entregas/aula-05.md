# Entrega — Aula 05

## O que foi feito nesta etapa

- **ADR-005** ([`docs/adr/ADR-005-event-sourcing.md`](../adr/ADR-005-event-sourcing.md)): agregado escolhido — `Lote`, no recorte da sua localização de pasto —, com as consequências aceitas.
- **Event store:** tabela `lote_evento_store`, append-only, chave `(lote_id, versao)` — a própria restrição de unicidade detecta concorrência.
- **Projeção descartável:** tabela `lote_localizacao_atual`, reconstruída dobrando (`fold`) os eventos `LoteFormado` + `LoteMovidoDePasto` em ordem.
- **Consumidor novo:** `LoteLocalizacaoListener`, `group.id = lote-localizacao` — primeiro consumidor dos tópicos `gado.lote.formado.v1` e `gado.lote.movido-de-pasto.v1`, que até esta etapa eram publicados e nunca lidos.
- **Teste que prova o replay:** `LoteLocalizacaoServiceTest` — apaga a projeção de um lote, reconstrói lendo só o event store, e verifica que o resultado é idêntico ao que o fold incremental original tinha produzido.

## Por que este agregado (resumo do ADR-005)

Não existia, em lugar nenhum do sistema, uma resposta para "em qual pasto está o lote X agora" — o `ManejoPastoService` só validava e publicava o evento, sem persistir a localização. É o único agregado do sistema em que esse ganho é concreto: `Animal` já tem histórico de peso e vacinação persistidos de outra forma; para "onde está o lote", não havia alternativa.

## Como rodar

Com a infraestrutura no ar (`docker compose up -d`) e o `servico-manejo` rodando (ver README, seção "Como rodar o servico-manejo"):

```bash
# 1. Cadastrar uma fazenda e um lote
curl -X POST http://localhost:8083/api/fazendas -H "Content-Type: application/json" \
  -d '{"id":"FZ-001","nome":"Fazenda Exemplo"}'

curl -X POST http://localhost:8083/api/lotes -H "Content-Type: application/json" \
  -d '{"id":"LOTE-001","numeracao":1,"fazenda":{"id":"FZ-001"}}'

# 2. Formar o lote — publica LoteFormadoEvent (versao 1 no event store)
curl -X POST http://localhost:8083/api/lotes/formacoes -H "Content-Type: application/json" \
  -d '{"eventoId":"evt-lf-001","ocorridoEm":"2026-09-15T10:00:00Z","loteId":"LOTE-001","animalIds":[],"criterioDeFormacao":"peso e idade semelhantes"}'

# 3. Mover o lote de pasto — publica LoteMovidoDePasto (versao 2)
curl -X POST http://localhost:8083/api/pastos/movimentacoes -H "Content-Type: application/json" \
  -d '{"eventoId":"evt-lm-001","ocorridoEm":"2026-09-15T14:00:00Z","loteId":"LOTE-001","pastoOrigemId":"PASTO-A","pastoDestinoId":"PASTO-B"}'

# 4. Consultar a localizacao atual (a projecao)
curl http://localhost:8083/api/lotes/LOTE-001/localizacao
# -> "PASTO-B"

# 5. Reconstruir a projecao por replay (caminho manual de reprocessamento)
curl -X POST http://localhost:8083/api/lotes/LOTE-001/localizacao/reconstruir
curl http://localhost:8083/api/lotes/LOTE-001/localizacao
# -> "PASTO-B" outra vez — mesmo resultado, reconstruido do zero
```

Testes (sem Docker, com H2):

```bash
mvn -f servico-manejo/pom.xml test
# LoteLocalizacaoServiceTest — inclui o teste de replay
```

## A defasagem tolerada, por tela, e por quê

**Tela:** `GET /api/lotes/{id}/localizacao` — a consulta de onde um lote está agora.

**Defasagem tolerada: alguns minutos.** A projeção é atualizada de forma assíncrona pelo `LoteLocalizacaoListener`; existe uma janela real entre o evento `LoteMovidoDePasto` ser publicado e a projeção refletir isso — sob operação normal essa janela é de segundos (o consumidor processa a mensagem assim que ela chega), mas pode crescer se o `servico-manejo` estiver reiniciando ou reprocessando um lote de mensagens acumuladas.

A justificativa: nenhuma decisão automática do sistema reage a essa tela hoje — ela existe para consulta humana (alguém checando onde um lote está antes de ir até lá, ou auditando o histórico de movimentação), não para disparar uma regra de negócio no instante em que muda. Um lote se move algumas vezes ao longo do confinamento, não continuamente, e quem move fisicamente o lote sabe que acabou de fazer isso — não depende da tela para saber a localização mais recente no primeiro minuto. Se essa projeção algum dia alimentar uma decisão automática (por exemplo, calcular suplementação por área com base na localização), essa tolerância precisaria ser revisitada — hoje ela não alimenta.

## Onde está cada coisa

| O quê | Onde |
|---|---|
| ADR-005 (event sourcing) | [`docs/adr/ADR-005-event-sourcing.md`](../adr/ADR-005-event-sourcing.md) |
| Event store + projeção (schema) | [`servico-manejo/src/main/resources/schema.sql`](../../servico-manejo/src/main/resources/schema.sql) |
| O fold (incremental e replay) | [`servico-manejo/.../service/LoteLocalizacaoService.java`](../../servico-manejo/src/main/java/br/pucminas/aed/manejo/service/LoteLocalizacaoService.java) |
| O consumidor | [`servico-manejo/.../controller/LoteLocalizacaoListener.java`](../../servico-manejo/src/main/java/br/pucminas/aed/manejo/controller/LoteLocalizacaoListener.java) |
| Leitura + reconstrução manual | [`servico-manejo/.../controller/LoteLocalizacaoController.java`](../../servico-manejo/src/main/java/br/pucminas/aed/manejo/controller/LoteLocalizacaoController.java) |
| O teste do replay | [`servico-manejo/.../service/LoteLocalizacaoServiceTest.java`](../../servico-manejo/src/test/java/br/pucminas/aed/manejo/service/LoteLocalizacaoServiceTest.java) |
| Registro de uso de IA | [`docs/IA.md`](../IA.md), seção `## Aula 05` |

## Nota sobre o prazo

Esta entrega estava prevista para 06/09 e está sendo produzida em 15/09, com autorização do professor para entrega tardia — mesma situação já registrada em [`docs/entregas/aula-04.md`](aula-04.md).
