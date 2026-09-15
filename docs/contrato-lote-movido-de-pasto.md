# Contrato do evento `LoteMovidoDePasto`

- **Tipo CloudEvents:** `gado.lote.movido-de-pasto.v1`
- **Agregado produtor:** Manejo de Pasto, no `servico-manejo`
- **Tópico:** `gado.lote.movido-de-pasto.v1`
- **Chave de partição:** `loteId`
- **Fonte:** `/fazenda-corte/manejo-pasto`

`LoteMovidoDePasto` registra o rodízio de uma composição de animais entre áreas de pastagem. Ele permite que Nutrição ajuste a suplementação a campo e que Sanidade acompanhe o controle de parasitas. A quantidade de chuva não integra a carga, pois não é uma decisão do agregado de Manejo de Pasto.

| Campo | Tipo | Obrigatório | Significado |
|---|---|---:|---|
| `eventoId` | String | Sim | Identificador único do fato; consumidores devem usá-lo para idempotência. |
| `ocorridoEm` | Instant ISO-8601 | Sim | Data e hora em que o lote mudou de área. |
| `loteId` | String | Sim | Lote movimentado e chave de partição. |
| `pastoOrigemId` | String | Sim | Área da qual o lote saiu. |
| `pastoDestinoId` | String | Sim | Área para a qual o lote foi alocado. |

Origem e destino devem ser diferentes. Novos consumidores podem ser adicionados sem alteração no produtor.
