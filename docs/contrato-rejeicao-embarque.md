# Contrato do evento `AnimalRejeitadoNoEmbarque`

- **Tipo CloudEvents:** `gado.animal.rejeitado-no-embarque.v1`
- **Publisher:** `servico-expedicao`
- **Tópico:** `gado.animal.rejeitado-no-embarque.v1`
- **Chave de partição:** `animalId`
- **Fonte:** `/fazenda-corte/expedicao-service`

O caminho de exceção do domínio (ADR-002): o Sistema do Frigorífico recusou o animal na triagem de recebimento — por peso abaixo da meta ou por vacinação vencida. A Expedição registra a recusa de forma definitiva (não é apagada nem reenviada); é a partir deste evento que o `servico-manejo` executa a compensação permanente descrita no ADR.

| Campo | Tipo | Obrigatório | Significado |
|---|---:|---:|---|
| `eventoId` | String | Sim | Identificador único do fato; consumidores devem usá-lo para idempotência. |
| `ocorridoEm` | Instant ISO-8601 | Sim | Data e hora da recusa na triagem. |
| `animalId` | String | Sim | Animal recusado e chave de partição. |
| `frigorificoDestino` | String | Sim | Frigorífico que recusou o animal. |
| `motivoRejeicao` | String | Sim | Motivo da recusa. Valores previstos hoje: `PESO_INSUFICIENTE`, `VACINACAO_VENCIDA`. É String, não enum fechado — a Expedição pode introduzir um motivo novo sem quebrar o contrato; consumidores tratam qualquer valor fora dos dois conhecidos como `OUTRO`. |

O `servico-manejo` (`AnimalRejeitadoNoEmbarqueEvent` do consumidor) não declara `frigorificoDestino`: a compensação depende de qual animal e por que ele foi recusado, não de qual frigorífico fez a triagem — esse dado interessa à auditoria da Expedição.

## Compensação executada pelo `servico-manejo`

Consumida pelo grupo `manejo-rejeicao-embarque` (tópico próprio, sem concorrência de partição com os demais consumidores do serviço), dedup por `eventoId` na mesma tabela `evento_processado` usada pelo histórico de pesagem e vacinação (ADR-002 — nunca por `animalId`). Dentro da mesma transação do dedup:

1. **Retorno ao lote de origem.** Neste sistema, nenhum fluxo desassocia um animal individual do seu lote apenas por entrar em processo de venda — só o agregado `Lote` tem movimentação própria, e é de pasto (ADR-005). "Retornar ao lote de origem" é, portanto, a reafirmação de que o animal permanece no `lote_id` que já tinha; não há um `loteId` separado no evento porque a Expedição não tem essa informação — só o `servico-manejo` sabe a qual lote um animal pertence.
2. **Reavaliação de dieta.** Estado vigente novo, gravado em `animal.dieta_atual` (não é histórico append-only: é o valor corrente, sobrescrito na próxima reavaliação). Regra determinística e deliberadamente simples: `PESO_INSUFICIENTE` → `REFORCO_ENERGETICO`; qualquer outro motivo → `MANUTENCAO`. Envolver um zootecnista humano na decisão está fora do escopo desta etapa.
3. **Encerramento da venda.** Toda venda ainda aberta (`deleted_at IS NULL`) que referencia este `animalId` é encerrada (soft-delete) — "a tentativa de venda que não se concretizou". Vendas por lote (`loteId`) não são afetadas: o evento é por animal, não por lote.

## Fora do escopo desta etapa

- Notificar Comercial ou Financeiro da venda encerrada — hoje é um efeito silencioso no banco do `servico-manejo`, sem um evento próprio publicado.
- Uma regra de dieta mais sofisticada que dependa de histórico de peso/vacinação, ou aprovação humana antes de aplicar a reavaliação.
