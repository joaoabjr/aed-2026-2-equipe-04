# AED 2026/2 — Equipe 04

## Integrantes

**Líder do projeto:** Paulo Cidrão Gomes Torres (258172)

|           Nome completo           | Matrícula |  Usuário GitHub  |
|-----------------------------------|-----------|------------------|
| Paulo Cidrão Gomes Torres (líder) |   258172  |     1668392      |
| João Almeida Barbosa Júnior       |   256355  |     joaoabjr     |
| João Pedro Schlindwein            |   255485  | Joao-Schlindwein |
| João Pedro Correia Barros         |   254580  |     joaobarros1  |
| Matheus Chaves Ferreira           |   258071  |     258071       |
| Rafael Corrêa Zart                |   255553  |     1665760      |
| Nome Completo 7                   |   000000  |     0000000      |

## O domínio, em uma frase
Descrição do processo de negócio escolhido, em uma frase: <br>
- Processo de venda e embarque de gado de corte para abate. <br>
Detalhes, critérios de aceitação e alternativas recusadas estão em [`docs/adr/ADR-002-dominio-do-projeto.md`](docs/adr/ADR-002-dominio-do-projeto.md).

## Stack

- Java 21
- Spring Boot
- Apache Kafka
- Docker Compose

## Estrutura do repositório

```
aed-2026-2-equipe-04/
├── README.md                                  este arquivo
├── docs/
│   ├── adr/
│   │   └── ADR-002-dominio-do-projeto.md       decisão do domínio
│   ├── entregas/
│   │   └── aula-02.md                          folha de rosto desta entrega
│   └── IA.md                                   registro de uso de IA (## Aula 02, ## Aula 03, ...)
├── servico-pesagem/                           serviço produtor (publisher)
└── servico-manejo/                            serviço consumidor (consumer)
```

## Portas

| Porta | Serviço | Uso |
|-------|---------|-----|
| `19092` | Kafka (docker-compose) | broker — acesso a partir do host |
| `15432` | Postgres (docker-compose) | banco do servico-manejo |
| `8081` | Kafka UI (docker-compose) | inspeção de tópicos/partições/mensagens (`http://localhost:8081`) |
| `8080` | servico-pesagem | API REST do publisher (`POST /pesagens`) |
| — | servico-manejo | sem porta web de propósito: é consumidor |

## Como subir o projeto numa máquina limpa

Pré-requisitos: Docker e Docker Compose instalados, JDK 21, Maven.

```bash
# 1. Clonar o repositório
git clone https://github.com/joaoabjr/aed-2026-2-equipe-04.git
cd aed-2026-2-equipe-04

# 2. Subir a infraestrutura (Kafka KRaft, Postgres e Kafka UI)
docker compose up -d
```

## Como rodar o servico-pesagem (publisher)

```bash
# build
cd servico-pesagem && mvn clean package

# rodar
mvn spring-boot:run
```

Publica um evento de pesagem com um `curl`:

```bash
curl -i -X POST http://localhost:8080/pesagens \
  -H "Content-Type: application/json" \
  -d @servico-pesagem/pesagens-exemplo/pesagem-AN-004821.json
```

Resposta esperada: `202 Accepted`.

## Como rodar o servico-manejo (consumer)

```bash
# build
cd servico-manejo && mvn clean package

# rodar (consome o tópico e grava no Postgres via 15432)
mvn spring-boot:run
```

## Como testar

```bash
# roda o teste de idempotência: mesmo evento entregue 3x, efeito 1x
cd servico-manejo && mvn test
```

## Onde encontrar cada coisa

| O quê | Onde |
|---|---|
| Decisão do domínio (ADR-002) | [`docs/adr/ADR-002-dominio-do-projeto.md`](docs/adr/ADR-002-dominio-do-projeto.md) |
| Registro de uso de IA | [`docs/IA.md`](docs/IA.md) |
| Código do publisher | [`servico-pesagem/`](servico-pesagem/) |
| Código do consumer | [`servico-manejo/`](servico-manejo/) |