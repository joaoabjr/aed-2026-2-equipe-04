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
servico-pesagem ──PesagemRegistrada──> Kafka ──> servico-manejo ──> PostgreSQL
                                                    └──> média de peso por minuto (log)
servico-vacinacao ─VacinacaoRegistrada─> Kafka ──> servico-manejo ──> PostgreSQL
```

Os publishers enviam eventos CloudEvents 1.0 em modo binário. A chave de partição é o `animalId`, preservando a ordem dos eventos de cada animal. O `servico-manejo` persiste os históricos de pesagem e vacinação de forma idempotente, usando o `eventoId` para descartar entregas repetidas.

| Serviço | Responsabilidade | Porta |
|---|---|---:|
| `servico-pesagem` | API que publica `PesagemRegistrada` | `8080` |
| `servico-vacinacao` | API que publica `VacinacaoRegistrada` | `8085` |
| `servico-manejo` | Consome eventos, mantém históricos e expõe o cadastro de manejo | `8083` |
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

Em terminais separados, inicie o consumidor e os dois publishers:

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

Para gerar os JARs sem iniciar os serviços:

```bash
mvn -f servico-pesagem/pom.xml clean package
mvn -f servico-manejo/pom.xml clean package
mvn -f servico-vacinacao/pom.xml clean package
```

Ao terminar, use `docker compose down` para parar a infraestrutura. Use `docker compose down -v` somente se também quiser remover os dados locais do PostgreSQL e Kafka.

## Publicando eventos

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

## Serviço de manejo

Além dos consumidores Kafka, o serviço expõe endpoints REST para o cadastro da estrutura do rebanho:

| Recurso | Endpoints disponíveis |
|---|---|
| Fazendas | `POST /api/fazendas`, `GET /api/fazendas/{id}` |
| Lotes | `POST /api/lotes`, `GET /api/lotes/{id}`, `GET /api/lotes/fazenda/{fazendaId}` |
| Animais | `POST /api/animais`, `GET /api/animais/{id}`, `GET /api/animais/lote/{loteId}` |

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

O mesmo processo registra três consumidores independentes:

| Listener | Grupo Kafka | Tópico | Efeito |
|---|---|---|---|
| `PesagemListener` | `manejo` | `gado.animal.pesagem-registrada.v1` | Grava o histórico de peso. |
| `VacinacaoListener` | `manejo-vacinacao` | `gado.animal.vacinacao-registrada.v1` | Grava o histórico de vacinação. |
| `PesagemAgregadaPorMinutoListener` | `pesagem-agregador` | `gado.animal.pesagem-registrada.v1` | Registra no log o peso médio do rebanho por janela de um minuto. |

Os grupos distintos recebem o fluxo completo do tópico; portanto, o agregador não compete com o consumidor que persiste o histórico. A janela é calculada a partir de `ocorridoEm` e fechada cerca de 15 segundos após seu término para aceitar pequenos atrasos.

## Testes

Execute os testes do serviço de manejo com:

```bash
mvn -f servico-manejo/pom.xml test
```

O conjunto inclui o teste de idempotência do histórico de pesagens: a entrega repetida do mesmo evento deve produzir apenas um efeito persistido.

## Estrutura do repositório

```text
.
├── docker-compose.yml
├── docs/
│   ├── adr/                         # decisões arquiteturais
│   ├── entregas/                    # documentação das entregas
│   ├── contrato.md                  # contrato de VacinacaoRegistrada
│   └── IA.md                        # registro de uso de IA
├── servico-pesagem/                 # publisher de pesagens
├── servico-vacinacao/               # publisher de vacinações
└── servico-manejo/                  # consumidores, persistência e API de manejo
```

## Documentação complementar

| Assunto | Documento |
|---|---|
| Decisão do domínio | [ADR-002](docs/adr/ADR-002-dominio-do-projeto.md) |
| Contrato de `VacinacaoRegistrada` | [docs/contrato.md](docs/contrato.md) |
| Entrega da aula 02 | [docs/entregas/aula-02.md](docs/entregas/aula-02.md) |
| Entrega da aula 03 | [docs/entregas/aula-03.md](docs/entregas/aula-03.md) |
| Registro de uso de IA | [docs/IA.md](docs/IA.md) |
