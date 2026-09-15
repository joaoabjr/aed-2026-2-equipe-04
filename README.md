# AED 2026/2 — Equipe 04

Projeto da disciplina de Arquitetura de Eventos Distribuídos (AED). O domínio é o processo de venda e embarque de gado de corte para abate: as pesagens e vacinações dos animais são publicadas como eventos e consumidas pelo serviço de manejo.

Os critérios de escolha do domínio estão registrados no [ADR-002](docs/adr/ADR-002-dominio-do-projeto.md).

## Integrantes

**Líder do projeto:** Paulo Cidrão Gomes Torres (258172)

| Nome completo | Matrícula | GitHub |
|---|---:|---|
| Paulo Cidrão Gomes Torres | 258172 | [1668392](https://github.com/1668392) |
| João Almeida Barbosa Júnior | 256355 | [joaoabjr](https://github.com/joaoabjr) |
| João Pedro Schlindwein | 255485 | [Joao-Schlindwein](https://github.com/Joao-Schlindwein) |
| João Pedro Correia Barros | 254580 | [joaobarros1](https://github.com/joaobarros1) |
| Matheus Chaves Ferreira | 258071 | [258071](https://github.com/258071) |
| Rafael Corrêa Zart | 255553 | [1665760](https://github.com/1665760) |

## Arquitetura

```text
servico-pesagem   ─PesagemRegistrada──> Kafka ──> servico-manejo               ──> PostgreSQL
                                                          └──> média de peso por minuto (log)
servico-vacinacao ─VacinacaoRegistrada─> Kafka ──> servico-manejo               ──> PostgreSQL
servico-expedicao ─AnimalEmbarcadoParaAbate─> Kafka ──> Financeiro / Rastreabilidade
servico-manejo    ─LoteFormado / LoteMovidoDePasto─> Kafka ──> Nutrição / Sanidade
```

Os publishers enviam eventos CloudEvents 1.0 em modo binário. A chave de partição é o `animalId`, preservando a ordem dos eventos de cada animal. O `servico-manejo` persiste os históricos de pesagem e vacinação de forma idempotente, usando o `eventoId` para descartar entregas repetidas. Os cadastros REST (`fazenda`, `lote`, `animal`, `venda`) também são idempotentes por `id`: reenviar o mesmo `id` retorna o registro existente (`200`) em vez de duplicar ou falhar — a primeira criação responde `201`.

| Serviço | Responsabilidade | Porta |
|---|---|---:|
| `servico-pesagem` | API que publica `PesagemRegistrada` | `8080` |
| `servico-vacinacao` | API que publica `VacinacaoRegistrada` | `8085` |
| `servico-expedicao` | API que publica `AnimalEmbarcadoParaAbate` | `8086` |
| `servico-manejo` | Consome eventos, mantém históricos, expõe o cadastro e publica eventos de lote/pasto | `8083` |
| Kafka | Broker de eventos | `19092` |
| PostgreSQL | Persistência do manejo | `15432` |
| Kafka UI | Inspeção de tópicos e mensagens | `8081` |

## Tecnologias

- Java 21 e Maven
- Spring Boot e Spring for Apache Kafka
- Apache Kafka (KRaft)
- PostgreSQL 16
- Docker Compose

## Pré-requisitos

- JDK 21
- Maven 3.9 ou compatível
- Docker e Docker Compose

## Como executar

Clone o repositório e suba a infraestrutura:

```bash
git clone https://github.com/joaoabjr/aed-2026-2-equipe-04.git
cd aed-2026-2-equipe-04
docker compose up -d
```

Confira a disponibilidade dos containers com `docker compose ps`. A interface do Kafka estará em <http://localhost:8081>.

Em terminais separados, inicie o consumidor e os três publishers:

```bash
# terminal 1 — consumidor e API de manejo
cd servico-manejo
mvn spring-boot:run
```

```bash
# terminal 2 — publisher de pesagens
cd servico-pesagem
mvn spring-boot:run
```

```bash
# terminal 3 — publisher de vacinações
cd servico-vacinacao
mvn spring-boot:run
```

```bash
# terminal 4 — publisher de embarques
cd servico-expedicao
mvn spring-boot:run
```

Para gerar os JARs sem iniciar os serviços:

```bash
mvn -f servico-pesagem/pom.xml clean package
mvn -f servico-manejo/pom.xml clean package
mvn -f servico-vacinacao/pom.xml clean package
mvn -f servico-expedicao/pom.xml clean package
```

Ao terminar, use `docker compose down` para parar a infraestrutura. Use `docker compose down -v` somente se também quiser remover os dados locais do PostgreSQL e Kafka.

## Publicando eventos

O contrato OpenAPI dos três endpoints de publicação está em [`docs/openapi-eventos.yaml`](docs/openapi-eventos.yaml). A API do serviço de manejo está em [`docs/openapi-manejo.yaml`](docs/openapi-manejo.yaml).

### Pesagem

```bash
curl -i -X POST http://localhost:8080/pesagens \
  -H 'Content-Type: application/json' \
  --data @servico-pesagem/pesagens-exemplo/pesagem-AN-004821.json
```

Resposta esperada: `202 Accepted`. O evento é publicado no tópico `gado.animal.pesagem-registrada.v1`.

### Vacinação

```bash
curl -i -X POST http://localhost:8085/vacinacao \
  -H 'Content-Type: application/json' \
  -d '{
    "eventoId": "evt-vac-2026-000123",
    "ocorridoEm": "2026-08-20T09:15:00Z",
    "animalId": "AN-004821",
    "pesoKg": 398.5,
    "metodoDeVacinacao": "subcutanea",
    "vacina": "Febre Aftosa",
    "validade": "2027-02-20T23:59:59Z"
  }'
```

Resposta esperada: `202 Accepted`. O evento é publicado no tópico `gado.animal.vacinacao-registrada.v1`.

### Embarque para abate

```bash
curl -i -X POST http://localhost:8086/embarques \
  -H 'Content-Type: application/json' \
  -d '{
    "eventoId": "evt-emb-2026-000001",
    "ocorridoEm": "2026-09-14T10:30:00Z",
    "animalId": "AN-004821",
    "frigorificoDestino": "Frigorífico Exemplo S.A.",
    "pesoDeEmbarqueKg": 480.5
  }'
```

Resposta esperada: `202 Accepted`. O evento é publicado no tópico `gado.animal.embarcado-para-abate.v1`.

## Serviço de manejo

Além dos consumidores Kafka, o serviço expõe endpoints REST para o cadastro da estrutura do rebanho (spec em [`docs/openapi-manejo.yaml`](docs/openapi-manejo.yaml)):

| Recurso | Endpoints disponíveis |
|---|---|
| Fazendas | `POST /api/fazendas`, `GET /api/fazendas/{id}` |
| Lotes | `POST /api/lotes`, `GET /api/lotes/{id}`, `GET /api/lotes/fazenda/{fazendaId}` |
| Animais | `POST /api/animais`, `GET /api/animais/{id}`, `GET /api/animais/lote/{loteId}` |
| Vendas | `POST /api/vendas`, `GET /api/vendas/{id}`, `GET /api/vendas/animal/{animalId}`, `GET /api/vendas/lote/{loteId}` |
| Formação de lote | `POST /api/lotes/formacoes` publica `LoteFormado` |
| Manejo de pasto | `POST /api/pastos/movimentacoes` publica `LoteMovidoDePasto` |

Os cadastros (`fazenda`, `lote`, `animal`, `venda`) são idempotentes por `id`: a primeira criação responde `201 Created`; reenviar o mesmo `id` não duplica nem falha — retorna o registro já existente com `200 OK` (regra transversal do [ADR-002](docs/adr/ADR-002-dominio-do-projeto.md)).

No recurso **lote**, o identificador de negócio é `numeracao`; o campo anterior `nome` não faz mais parte da carga. Exemplo de criação:

```json
{
  "id": "LT-001",
  "numeracao": 1,
  "fazenda": {
    "id": "FZ-001",
    "nome": "Fazenda Exemplo"
  }
}
```

O mesmo processo registra quatro consumidores independentes:

| Listener | Grupo Kafka | Tópico | Efeito |
|---|---|---|---|
| `PesagemListener` | `manejo` | `gado.animal.pesagem-registrada.v1` | Grava o histórico de peso. |
| `VacinacaoListener` | `manejo-vacinacao` | `gado.animal.vacinacao-registrada.v1` | Grava o histórico de vacinação. |
| `PesagemAgregadaPorMinutoListener` | `pesagem-agregador` | `gado.animal.pesagem-registrada.v1` | Registra no log o peso médio do rebanho por janela de um minuto. |
| `LoteLocalizacaoListener` | `lote-localizacao` | `gado.lote.formado.v1` + `gado.lote.movido-de-pasto.v1` | Anexa cada evento ao event store do lote (ADR-005) e atualiza a projeção de localização atual. |

Os grupos distintos recebem o fluxo completo do tópico; portanto, o agregador não compete com o consumidor que persiste o histórico. A janela é calculada a partir de `ocorridoEm` e fechada cerca de 15 segundos após seu término para aceitar pequenos atrasos.

O contrato de `PesagemRegistrada` (`gado.animal.pesagem-registrada.v1`) está em [`docs/contrato-pesagem.md`](docs/contrato-pesagem.md), e o de `VacinacaoRegistrada` em [`docs/contrato.md`](docs/contrato.md): os dois eventos que o serviço de manejo consome têm contrato escrito.

O serviço de manejo também publica `LoteFormado` em `gado.lote.formado.v1`. O contrato está em [`docs/contrato-lote-formado.md`](docs/contrato-lote-formado.md); Nutrição e Manejo de Pasto podem consumi-lo sem acoplamento ao produtor — e é também o primeiro evento do event store de localização do lote (ver `docs/adr/ADR-005-event-sourcing.md`).

O agregado Manejo de Pasto publica `LoteMovidoDePasto` em `gado.lote.movido-de-pasto.v1`. O contrato está em [`docs/contrato-lote-movido-de-pasto.md`](docs/contrato-lote-movido-de-pasto.md); Nutrição e Sanidade podem consumi-lo independentemente — e é o evento que atualiza a localização atual do lote.

### Localização atual do lote (event sourcing — ADR-005)

Depois de formar um lote e movê-lo de pasto (endpoints acima), a localização atual — reconstruída a partir do event store, não guardada numa coluna mutável — fica disponível em:

```bash
curl http://localhost:8083/api/lotes/LOTE-001/localizacao
```

Para reconstruir a projeção do zero, relendo só o event store (caminho manual de reprocessamento, sem automação):

```bash
curl -X POST http://localhost:8083/api/lotes/LOTE-001/localizacao/reconstruir
```

Detalhes da decisão (por que este agregado, o que o event store garante, a defasagem tolerada) em [`docs/adr/ADR-005-event-sourcing.md`](docs/adr/ADR-005-event-sourcing.md) e [`docs/entregas/aula-05.md`](docs/entregas/aula-05.md).

O caminho de exceção do domínio é a recusa do animal no frigorífico (`AnimalRejeitadoNoEmbarque`): registrada de forma definitiva pela expedição, ela dispara a compensação no manejo — o animal retorna ao lote de origem e a dieta é reavaliada, como efeito permanente (ver [ADR-002](docs/adr/ADR-002-dominio-do-projeto.md)).

## Testes

Execute os testes do serviço de manejo com:

```bash
mvn -f servico-manejo/pom.xml test
```

O conjunto inclui o teste de idempotência do histórico de pesagens — a entrega repetida do mesmo evento deve produzir apenas um efeito persistido — e os testes de reenvio idempotente dos cadastros de fazenda, lote, animal e venda.

## Estrutura do repositório

```text
.
├── docker-compose.yml
├── docs/
│   ├── adr/                         # decisões arquiteturais
│   ├── entregas/                    # documentação das entregas
│   ├── contrato.md                  # contrato de VacinacaoRegistrada
│   ├── contrato-pesagem.md          # contrato de PesagemRegistrada
│   ├── contrato-embarque.md         # contrato de AnimalEmbarcadoParaAbate
│   ├── contrato-lote-formado.md     # contrato de LoteFormado
│   ├── contrato-lote-movido-de-pasto.md  # contrato de LoteMovidoDePasto
│   ├── openapi-eventos.yaml         # OpenAPI dos publishers
│   ├── openapi-manejo.yaml          # OpenAPI da API de manejo
│   └── IA.md                        # registro de uso de IA
├── servico-pesagem/                 # publisher de pesagens
├── servico-vacinacao/               # publisher de vacinações
├── servico-expedicao/                # publisher de embarques para abate
└── servico-manejo/                  # consumidores, persistência e API de manejo
```

## Documentação complementar

| Assunto | Documento |
|---|---|
| Decisão do domínio | [ADR-002](docs/adr/ADR-002-dominio-do-projeto.md) |
| Contrato de `VacinacaoRegistrada` | [docs/contrato.md](docs/contrato.md) |
| Contrato de `PesagemRegistrada` | [docs/contrato-pesagem.md](docs/contrato-pesagem.md) |
| Contrato de `AnimalEmbarcadoParaAbate` | [docs/contrato-embarque.md](docs/contrato-embarque.md) |
| Contrato de `LoteFormado` | [docs/contrato-lote-formado.md](docs/contrato-lote-formado.md) |
| Contrato de `LoteMovidoDePasto` | [docs/contrato-lote-movido-de-pasto.md](docs/contrato-lote-movido-de-pasto.md) |
| OpenAPI dos publishers | [docs/openapi-eventos.yaml](docs/openapi-eventos.yaml) |
| OpenAPI da API de manejo | [docs/openapi-manejo.yaml](docs/openapi-manejo.yaml) |
| Entrega da aula 02 | [docs/entregas/aula-02.md](docs/entregas/aula-02.md) |
| Entrega da aula 03 | [docs/entregas/aula-03.md](docs/entregas/aula-03.md) |
| Registro de uso de IA | [docs/IA.md](docs/IA.md) |
