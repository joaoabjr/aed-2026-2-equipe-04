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


---

# Contrato do evento — `gado.animal.rejeitado-no-embarque.v1`

## Identificação

- **Tipo (CloudEvents `type`):** `gado.animal.rejeitado-no-embarque.v1` -- grafia identica a constante `TYPE` em [`RejeicaoEmbarqueService`](../servico-expedicao/src/main/java/br/pucminas/aed/expedicao/service/RejeicaoEmbarqueService.java) e ao topico Kafka (`demo.topico-rejeicao`).
- **Classe do publisher:** [`br.pucminas.aed.expedicao.domain.AnimalRejeitadoNoEmbarqueEvent`](../servico-expedicao/src/main/java/br/pucminas/aed/expedicao/domain/AnimalRejeitadoNoEmbarqueEvent.java).
- **`source`:** `/fazenda-corte/expedicao-service`.
- **Envelope:** CloudEvents 1.0, modo binario -- mesmo padrao dos demais eventos do projeto: `ce_specversion`, `ce_id`, `ce_source`, `ce_type`, `ce_time` (igual a `ocorridoEm`), `ce_subject` (`animal/{animalId}`), `ce_datacontenttype` (`application/json`).

E' o evento de compensacao da Parte B do projeto final: o caminho de excecao do dominio, ja nomeado desde o ADR-002 -- o Sistema do Frigorifico recusou o animal na triagem de recebimento. A Expedicao registra a recusa de forma definitiva (nunca apagada nem reenviada), e e' a partir deste fato que o `servico-manejo` executa a compensacao (retorno ao lote de origem, reavaliacao de dieta, encerramento da venda que nao se concretizou) -- ver ADR-006, secao "A Saga de compensacao: coreografia".

## Campos

| Campo | Tipo | Obrigatorio | Significado |
|---|---|---|---|
| `eventoId` | `String` | Sim | Identificador unico deste evento. Chave de deduplicacao do lado do `servico-manejo` (mesma tabela `evento_processado` usada pelo historico de pesagem e vacinacao) -- nunca deduplica por `animalId`. |
| `ocorridoEm` | `Instant` (ISO-8601) | Sim | Instante da recusa na triagem do frigorifico (event time), nao quando o `servico-manejo` processou. Vai para o cabecalho `ce_time`. |
| `animalId` | `String` | Sim | Identifica o animal recusado. E' a chave de particao do topico -- ver secao propria abaixo. |
| `frigorificoDestino` | `String` | Sim | Frigorifico que recusou o animal na triagem. O `servico-manejo` NAO declara este campo na classe espelhada do consumidor -- a compensacao depende de qual animal e por que foi recusado, nao de qual frigorifico fez a triagem; esse dado interessa so' a auditoria da Expedicao. |
| `motivoRejeicao` | `String` | Sim | Motivo da recusa. Valores previstos hoje: `PESO_INSUFICIENTE`, `VACINACAO_VENCIDA`. E' String, nao enum fechado -- a Expedicao pode introduzir um motivo novo sem quebrar o contrato; o `servico-manejo` trata qualquer valor fora dos dois conhecidos como `OUTRO` antes de decidir a dieta de reavaliacao. |

## Datas

Mesmo padrao do resto do contrato: `Instant`, serializado como texto ISO-8601, nunca epoch. `ExpedicaoConfig` registra `JavaTimeModule` e desliga `WRITE_DATES_AS_TIMESTAMPS`, igual aos demais publishers do projeto.

## Chave de partição

`animalId`. Garante que a recusa de um animal seja processada respeitando a ordem de fatos daquele MESMO animal em relacao aos outros eventos de nivel-animal do sistema (`PesagemRegistrada`, `VacinacaoRegistrada`) -- ver ADR-003, que documenta essa escolha (chave por `animalId` para o nivel do animal, `loteId` para o nivel do lote) como decisao do sistema inteiro, nao so' deste evento.

## Regra de compatibilidade: **BACKWARD**

Mesma logica do contrato de `VacinacaoRegistrada` acima: o unico consumidor hoje (`RejeicaoEmbarqueListener`, em `servico-manejo`) e' interno a este repositorio e ja' le de forma tolerante (nao declara `frigorificoDestino`). BACKWARD protege o historico ja' publicado sem travar a evolucao do publisher, desde que mudancas sejam aditivas.

## Exemplo de carga (dados fictícios)

```json
{
  "eventoId": "evt-rej-2026-000045",
  "ocorridoEm": "2026-09-20T08:30:00Z",
  "animalId": "AN-004821",
  "frigorificoDestino": "Frigorifico Exemplo S.A.",
  "motivoRejeicao": "VACINACAO_VENCIDA"
}
```

Documentação completa da compensação executada pelo `servico-manejo` (os três efeitos, e o que fica fora de escopo) em [`docs/contrato-rejeicao-embarque.md`](contrato-rejeicao-embarque.md).