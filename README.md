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
│   │   ├── aula-02.md                          folha de rosto da aula 02
│   │   └── aula-03.md                          folha de rosto da aula 03
│   ├── contrato.md                             contrato do evento VacinacaoRegistrada
│   └── IA.md                                   registro de uso de IA (## Aula 02, ## Aula 03, ...)
├── servico-pesagem/                           serviço publisher (pesagem)
├── servico-manejo/                            serviço consumidor (historico de peso)
└── servico-vacinacao/                         serviço publisher (vacina)
```

## Portas

| Porta | Serviço | Uso |
|-------|---------|-----|
| `19092` | Kafka (docker-compose) | broker — acesso a partir do host |
| `15432` | Postgres (docker-compose) | banco do servico-manejo |
| `8081` | Kafka UI (docker-compose) | inspeção de tópicos/partições/mensagens (`http://localhost:8081`) |
| `8080` | servico-pesagem | API REST do publisher (`POST /pesagens`) |
| `8085` | servico-vacinacao | API REST do vaccination service (`POST /vacinacao`) |
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

Subir o `servico-manejo` sobe, no mesmo processo, **três** `@KafkaListener` independentes, cada um com seu próprio `group.id` (não dividem partições entre si — ver [`ManejoConfig`](servico-manejo/src/main/java/br/pucminas/aed/manejo/ManejoConfig.java)):

| Listener | `group.id` | Tópico | O que faz |
|---|---|---|---|
| `PesagemListener` | `manejo` | `gado.animal.pesagem-registrada.v1` | Histórico de peso, idempotente, grava no Postgres (etapa 1). |
| `VacinacaoListener` | `manejo-vacinacao` | `gado.animal.vacinacao-registrada.v1` | Histórico de vacinação, idempotente, grava no Postgres. |
| `PesagemAgregadaPorMinutoListener` | `pesagem-agregador` | `gado.animal.pesagem-registrada.v1` | Peso médio do rebanho por janela de 1 minuto — só log, não grava nada (aula 03, Parte B). |

## Como observar o agregador de pesagem (aula 03, Parte B)

Não precisa subir nada além do `servico-manejo` — o agregador é um `@KafkaListener` a mais, dentro do mesmo processo, com `group.id = pesagem-agregador` próprio. Publique algumas pesagens (seção acima, `POST /pesagens`) e acompanhe o log do `servico-manejo`:

```
peso medio do rebanho por minuto  janela=[2026-08-27T10:15:00Z, 2026-08-27T10:16:00Z)  amostras=3  pesoMedioKg=402.30  particao=0:offset=17
```

Cada janela fecha e é publicada cerca de 15s depois do seu fim (margem para eventos com pequeno atraso — ver [`docs/entregas/aula-03.md`](docs/entregas/aula-03.md), pergunta 3). Como o `PesagemListener` da etapa 1 continua rodando em paralelo com `group.id` diferente (`manejo`), os dois recebem o stream inteiro de pesagens — nenhum "rouba" partição do outro.

## Como testar

```bash
# roda o teste de idempotência: mesmo evento entregue 3x, efeito 1x
cd servico-manejo && mvn test
```

## Como rodar o servico-vacinacao

O serviço de vacinação roda na porta 8085 e publica eventos no tópico Kafka `gado.animal.vacinacao-registrada.v1`.

```bash
# build
cd servico-vacinacao && mvn clean package

# rodar (inicie o docker compose primeiro: docker compose up -d)
java -jar target/servico-vacinacao-1.0.jar
```

Publica um evento de vacinação com um `curl`:

```bash
curl -X POST http://localhost:8085/vacinacao \
  -H "Content-Type: application/json" \
  -d '{
    "eventoId": "evt-001",
    "animalId": "AN001",
    "ocorridoEm": "2026-08-21T10:30:00Z",
    "pesoKg": 550.0,
    "metodoDeVacinacao": "subcutanea",
    "vacina": "Febre Aftosa",
    "validade": "2026-12-31T23:59:59Z"
  }'
```

Resposta esperada: `202 Accepted`.

O evento publicado é consumido pelo `VacinacaoListener` do `servico-manejo` (`group.id = manejo-vacinacao`), que grava o histórico de vacinação no Postgres — ver tabela de listeners na seção do `servico-manejo` acima.

## Onde encontrar cada coisa

| O quê | Onde |
|---|---|
| Decisão do domínio (ADR-002) | [`docs/adr/ADR-002-dominio-do-projeto.md`](docs/adr/ADR-002-dominio-do-projeto.md) |
| Contrato do evento `VacinacaoRegistrada` | [`docs/contrato.md`](docs/contrato.md) |
| Folha de rosto da aula 03 | [`docs/entregas/aula-03.md`](docs/entregas/aula-03.md) |
| Registro de uso de IA | [`docs/IA.md`](docs/IA.md) |
| Código do publisher (pesagem) | [`servico-pesagem/`](servico-pesagem/) |
| Código do consumer (histórico + agregador) | [`servico-manejo/`](servico-manejo/) |
| Código do publisher (vacina) | [`servico-vacinacao/`](servico-vacinacao/) |