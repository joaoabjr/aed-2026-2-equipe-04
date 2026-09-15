# Contrato do evento — `gado.animal.pesagem-registrada.v1`

## Identificação

- **Tipo (CloudEvents `type`):** `gado.animal.pesagem-registrada.v1` — grafia idêntica à constante `TYPE` em [`PesagemService`](../servico-pesagem/src/main/java/br/pucminas/aed/pesagem/service/PesagemService.java) e ao nome do tópico Kafka (`demo.topico` no `application.yml` do `servico-pesagem`).
- **Classe do publisher:** [`br.pucminas.aed.pesagem.domain.PesagemRegistradaEvent`](../servico-pesagem/src/main/java/br/pucminas/aed/pesagem/domain/PesagemRegistradaEvent.java).
- **`source`:** `/fazenda-corte/pesagem-service`.
- **Envelope:** CloudEvents 1.0, modo binário — atributos `ce_*` nos cabeçalhos Kafka, corpo da mensagem só com os campos de negócio abaixo. Construído em `PesagemService.publicar`: `ce_specversion`, `ce_id` (iguale `eventoId`), `ce_source`, `ce_type`, `ce_time` (igual a `ocorridoEm`), `ce_subject` (`animal/{animalId}`), `ce_datacontenttype` (`application/json`).

## Campos

| Campo | Tipo | Obrigatório | Significado |
|---|---|---|---|
| `eventoId` | `String` | Sim | Identificador único deste evento (não do animal). É a chave de deduplicação: um consumidor idempotente descarta em silêncio qualquer entrega repetida com o mesmo `eventoId`, nunca deduplica por `animalId`. |
| `ocorridoEm` | `Instant` (ISO-8601) | Sim | Instante em que a pesagem de fato aconteceu (event time), não quando o broker recebeu a mensagem. Vai para o cabeçalho `ce_time`. É o campo que o agregador da Parte B usa para decidir a que janela de 1 minuto a leitura pertence. |
| `animalId` | `String` | Sim | Identifica o animal pesado. É a chave de partição do tópico — ver seção própria abaixo. |
| `pesoKg` | `double` | Sim (tipo primitivo, sempre presente na carga) | Peso registrado pela balança no momento da pesagem, em quilogramas. É o campo central da regra de negócio: formação de lote, dieta e a decisão de embarque (peso-alvo) partem dele. |
| `metodoDePesagem` | `String` | Não (sem validação no construtor; pode chegar nulo) | Balança/instrumento usado na aferição (ex.: `balanca-eletronica-curral`). Serve auditoria e qualidade; não participa da regra de negócio. O consumidor de manejo propositalmente não declara esse campo na classe espelhada (ver regra de compatibilidade). |

## Datas

Todo campo de data/hora é `java.time.Instant`, serializado como texto ISO-8601 (`2026-08-16T14:32:07Z`), nunca como epoch millis. O `ObjectMapper` do publisher registra `JavaTimeModule` e desliga `WRITE_DATES_AS_TIMESTAMPS` explicitamente para garantir isso (`PesagemConfig`) — o `JsonSerializer` padrão do spring-kafka gravaria `Instant` como número, o que quebraria o contrato de JSON no fio.

## Chave de partição

`animalId`. Garante que todas as pesagens **do mesmo animal** cheguem ao consumidor na ordem em que ocorreram — necessário para a curva de peso do animal (a média e a tendência dependem da ordem real das leituras). Pesagens de animais diferentes podem ser processadas fora de ordem entre si sem problema, por isso o tópico usa 3 partições.

## Regra de compatibilidade: **BACKWARD**

Não existe consumidor externo dependendo do formato atual — os dois consumidores deste evento (`PesagemListener` e `PesagemAgregadaPorMinutoListener`, ambos em `servico-manejo`) são internos a este mesmo repositório e seguem o padrão de leitura tolerante do projeto (`@JsonIgnoreProperties(ignoreUnknown = true)`, declarando só os campos que usam). O `PesagemListener`/`HistoricoPesagemService` declara `eventoId`, `ocorridoEm`, `animalId` e `pesoKg`; o agregador da Parte B usa apenas `ocorridoEm` e `pesoKg`. A classe espelhada em `servico-manejo` nem sequer declara `metodoDePesagem`.

Isso dá liberdade para o publisher evoluir o schema (tipicamente adicionando campos), mas qualquer consumidor futuro vai precisar continuar lendo os eventos que já estão retidos no tópico. **BACKWARD** (todo schema novo consegue ler dado escrito com schema antigo) é a regra mínima coerente com esse cenário: protege o histórico já publicado sem travar a evolução do publisher, desde que mudanças sejam aditivas (campo novo opcional) ou de remoção de algo que nenhum consumidor tolerante declarava. Trocar o tipo de um campo existente ou tornar obrigatório um campo antes opcional quebra BACKWARD e exigiria uma nova versão do `type` (`.v2`).

## Exemplo de carga (dados fictícios)

```json
{
  "eventoId": "11111111-1111-1111-1111-111111111111",
  "ocorridoEm": "2026-08-16T14:32:07Z",
  "animalId": "AN-004821",
  "pesoKg": 412.6,
  "metodoDePesagem": "balanca-eletronica-curral"
}
```

Cabeçalhos CloudEvents correspondentes (modo binário):

```
ce_specversion: 1.0
ce_id: 11111111-1111-1111-1111-111111111111
ce_source: /fazenda-corte/pesagem-service
ce_type: gado.animal.pesagem-registrada.v1
ce_time: 2026-08-16T14:32:07Z
ce_subject: animal/AN-004821
ce_datacontenttype: application/json
```