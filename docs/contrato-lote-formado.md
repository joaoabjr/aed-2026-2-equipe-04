# Contrato do evento `LoteFormado`

- **Tipo CloudEvents:** `gado.lote.formado.v1`
- **Agregado produtor:** Lote, no `servico-manejo`
- **Tópico:** `gado.lote.formado.v1`
- **Chave de partição:** `loteId`
- **Fonte:** `/fazenda-corte/manejo-service`

`LoteFormado` registra a composição de animais que receberão manejo conjunto por apresentarem peso e idade semelhantes. O evento não inclui a origem financeira dos animais: essa informação não pertence à decisão de formação do lote.

| Campo | Tipo | Obrigatório | Significado |
|---|---|---:|---|
| `eventoId` | String | Sim | Identificador único do fato; consumidores devem usá-lo para idempotência. |
| `ocorridoEm` | Instant ISO-8601 | Sim | Data e hora de formação. |
| `loteId` | String | Sim | Lote formado e chave de partição. |
| `animalIds` | Array de String | Sim | Animais que integram o lote; pode estar vazio logo após a formação. |
| `criterioDeFormacao` | String | Sim | Regra usada pela Expedição/Manejo, por exemplo `peso e idade semelhantes`. |

Nutrição e Manejo de Pasto são consumidores previstos. O produtor não depende deles nem precisa ser alterado quando novos consumidores surgirem.
