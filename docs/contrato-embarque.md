# Contrato do evento `AnimalEmbarcadoParaAbate`

- **Tipo CloudEvents:** `gado.animal.embarcado-para-abate.v1`
- **Publisher:** `servico-expedicao`
- **Tópico:** `gado.animal.embarcado-para-abate.v1`
- **Chave de partição:** `animalId`
- **Fonte:** `/fazenda-corte/expedicao-service`

O evento é o fato final do ciclo de engorda: o animal foi aceito para embarque e a posse deixa a propriedade. A Expedição o publica após a decisão baseada no peso-alvo e na confirmação de recebimento pelo frigorífico.

| Campo | Tipo | Obrigatório | Significado |
|---|---|---:|---|
| `eventoId` | String | Sim | Identificador único do fato; consumidores devem usá-lo para idempotência. |
| `ocorridoEm` | Instant ISO-8601 | Sim | Data e hora efetiva do embarque. |
| `animalId` | String | Sim | Animal embarcado e chave de partição. |
| `frigorificoDestino` | String | Sim | Frigorífico que confirmou o recebimento. |
| `pesoDeEmbarqueKg` | double | Sim | Peso aferido no embarque, em quilogramas. |

Preço da arroba não integra a carga: a Expedição decide a saída física do animal, não o valor comercial. Financeiro e Rastreabilidade são consumidores previstos, mas o publisher não depende deles.
