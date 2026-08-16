# AED 2026/2 — Equipe 04

## Integrantes

**Líder do projeto:** Paulo Cidrão Gomes Torres (258172)

| Nome completo                     | Matrícula | Usuário GitHub |
|-----------------------------------|---|---|
| Paulo Cidrão Gomes Torres (líder) | 258172 | 1668392 |
| João Almeida Barbosa Júnior       | 256355 | joaoabjr |
| João Pedro Correia Barros         | 254580 | joaobarros1 |
| Nome Completo 4                   | 0000000 | 0000000 |
| Nome Completo 5                   | 0000000 | 0000000 |
| Nome Completo 6                   | 0000000 | 0000000 |
| Nome Completo 7                   | 0000000 | 0000000 |

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
├── servico-publisher/                          nome real do serviço produtor
└── servico-consumer/                           nome real do serviço consumidor
```

## Como subir o projeto numa máquina limpa

Pré-requisitos: Docker e Docker Compose instalados, JDK 21, Maven.

```bash
# 1. Clonar o repositório
git clone https://github.com/joaoabjr/aed-2026-2-equipe-04.git
cd aed-2026-2-equipe-04

# 2. Subir a infraestrutura (Kafka, Zookeeper/KRaft, Kafka UI etc.)
docker compose up -d

# 3. Build dos serviços
cd servico-publisher && mvn clean package && cd ..
cd servico-consumer && mvn clean package && cd ..

# 4. Rodar o publisher
cd servico-publisher && mvn spring-boot:run

# 5. Em outro terminal, rodar o consumer
cd servico-consumer && mvn spring-boot:run
```

## Como testar

```bash
# roda o teste de idempotência: mesmo evento entregue 3x, efeito 1x
cd servico-consumer && mvn test
```

## Onde encontrar cada coisa

| O quê | Onde |
|---|---|
| Decisão do domínio (ADR-002) | [`docs/adr/ADR-002-dominio-do-projeto.md`](docs/adr/ADR-002-dominio-do-projeto.md) |
| Folha de rosto desta entrega | [`docs/entregas/aula-02.md`](docs/entregas/aula-02.md) |
| Registro de uso de IA | [`docs/IA.md`](docs/IA.md) |
| Código do publisher | [`servico-publisher/`](servico-publisher/) |
| Código do consumer | [`servico-consumer/`](servico-consumer/) |