# Contrato do evento — `gado.animal.vacinacao-registrada.v1`

## Identificação

- **Tipo (CloudEvents `type`):** `gado.animal.vacinacao-registrada.v1` — grafia idêntica à constante `TYPE` em [`VacinacaoService`](../servico-vacinacao/src/main/java/br/pucminas/aed/vacinacao/service/VacinacaoService.java) e ao nome do tópico Kafka (`demo.topico` no `application.yml` do `servico-vacinacao`).
- **Classe do publisher:** [`br.pucminas.aed.vacinacao.domain.VacinacaoRegistradaEvent`](../servico-vacinacao/src/main/java/br/pucminas/aed/vacinacao/domain/VacinacaoRegistradaEvent.java).
- **`source`:** `/fazenda-corte/vacinacao-service`.
- **Envelope:** CloudEvents 1.0, modo binário — atributos `ce_*` nos cabeçalhos Kafka, corpo da mensagem só com os campos de negócio abaixo. Mesmo padrão do evento `PesagemRegistrada` da etapa 1: `ce_specversion`, `ce_id`, `ce_source`, `ce_type`, `ce_time` (igual a `ocorridoEm`), `ce_subject` (`animal/{animalId}`), `ce_datacontenttype` (`application/json`).

## Campos

| Campo | Tipo | Obrigatório | Significado |
|---|---|---|---|
| `eventoId` | `String` | Sim | Identificador único deste evento (não do animal). É a chave de deduplicação: um consumidor idempotente descarta em silêncio qualquer entrega repetida com o mesmo `eventoId`, nunca deduplica por `animalId`. |
| `ocorridoEm` | `Instant` (ISO-8601) | Sim | Instante em que a vacinação de fato aconteceu (event time), não quando o broker recebeu a mensagem. Vai para o cabeçalho `ce_time`. |
| `animalId` | `String` | Sim | Identifica o animal vacinado. É a chave de partição do tópico — ver seção própria abaixo. |
| `pesoKg` | `double` | Sim (tipo primitivo, sempre presente na carga) | Peso do animal registrado no momento da aplicação da vacina. É contexto clínico da dose (dosagem costuma variar por peso); não substitui nem se confunde com o histórico de peso mantido pelo evento `PesagemRegistrada` — são medições em momentos e com finalidades diferentes. |
| `metodoDeVacinacao` | `String` | Não (sem validação no construtor; pode chegar nulo) | Via de aplicação da dose (ex.: `subcutanea`, `intramuscular`). Serve auditoria e manejo sanitário; não participa da regra de negócio de liberação para embarque. |
| `vacina` | `String` | Sim | Identifica o imunobiológico aplicado (ex.: `"Febre Aftosa"`). Junto com `validade`, é o dado que sustenta a pergunta que importa para o embarque: "esse animal está com a carteira de vacinação em dia?". |
| `validade` | `Instant` (ISO-8601) | Sim | Data-limite até quando a proteção da dose aplicada vale — **não** é a data de aplicação (essa é `ocorridoEm`). É o campo central da regra "carteira de vacinação em dia": o frigorífico só aceita o animal se, na data do embarque, existir ao menos uma vacinação cuja `validade` ainda não tenha vencido. Um `validade` no passado não é um evento inválido — é um animal com vacinação vencida, uma condição de negócio legítima que o consumidor de embarque precisa saber tratar. |

## Datas

Todo campo de data/hora é `java.time.Instant`, serializado como texto ISO-8601 (`2026-08-20T09:15:00Z`), nunca como epoch millis. O `ObjectMapper` do publisher registra `JavaTimeModule` e desliga `WRITE_DATES_AS_TIMESTAMPS` explicitamente para garantir isso (`VacinacaoConfig`).

Esse ponto já motivou uma correção nesta etapa: o campo `validade` chegou à aula 03 declarado como `java.util.Date`, que não é coberto pelo `JavaTimeModule` e — mesmo com `WRITE_DATES_AS_TIMESTAMPS` desligado — serializa num formato ISO-8601 "torto" (`+0000` em vez de `Z`, sem o mesmo formatador usado pelos demais campos), inconsistente com o resto do contrato. Foi trocado para `Instant` no publisher e no consumidor, eliminando a inconsistência em vez de documentá-la como exceção.

## Chave de partição

`animalId`. Garante que todas as vacinações **do mesmo animal** cheguem ao consumidor na ordem em que ocorreram — necessário para responder corretamente "qual é a vacinação vigente deste animal agora" (a mais recente com `validade` não vencida), o que exige saber a ordem real de aplicação quando há mais de uma dose no histórico. Vacinações de animais diferentes podem ser processadas fora de ordem entre si sem problema, por isso o tópico não precisa de partição única.

## Regra de compatibilidade: **BACKWARD**

Hoje não existe nenhum consumidor externo dependendo do formato atual — o único consumidor deste evento (`VacinacaoListener`, em `servico-manejo`) é interno a este mesmo repositório e já segue o padrão de leitura tolerante do projeto (`@JsonIgnoreProperties(ignoreUnknown = true)`, declarando só os campos que usa). Isso dá liberdade para o publisher evoluir o schema (tipicamente adicionando campos), mas qualquer consumidor futuro — inclusive um que ainda não existe — vai precisar continuar lendo os eventos que já estão retidos no tópico. **BACKWARD** (todo schema novo consegue ler dado escrito com schema antigo) é a regra mínima coerente com esse cenário: protege o histórico já publicado sem travar a evolução do publisher, desde que mudanças sejam aditivas (campo novo opcional) ou de remoção de algo que nenhum consumidor tolerante declarava. Trocar o tipo de um campo existente (como aconteceu aqui com `validade`) ou tornar obrigatório um campo antes opcional quebra BACKWARD e exigiria uma nova versão do `type` (`.v2`).

## Exemplo de carga (dados fictícios)

```json
{
  "eventoId": "evt-vac-2026-000123",
  "ocorridoEm": "2026-08-20T09:15:00Z",
  "animalId": "AN-004821",
  "pesoKg": 398.5,
  "metodoDeVacinacao": "subcutanea",
  "vacina": "Febre Aftosa",
  "validade": "2027-02-20T23:59:59Z"
}
```

Cabeçalhos CloudEvents correspondentes (modo binário):

```
ce_specversion: 1.0
ce_id: evt-vac-2026-000123
ce_source: /fazenda-corte/vacinacao-service
ce_type: gado.animal.vacinacao-registrada.v1
ce_time: 2026-08-20T09:15:00Z
ce_subject: animal/AN-004821
ce_datacontenttype: application/json
```
