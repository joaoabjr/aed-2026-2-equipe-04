# Entrega — Aula 02

## O que foi feito nesta etapa

- **Parte A:** domínio escolhido e registrado em [`docs/adr/ADR-002-dominio-do-projeto.md`](../adr/ADR-002-dominio-do-projeto.md) — processo de venda e embarque de gado de corte para abate.
- **Parte B:** publisher (`servico-pesagem`) e consumer idempotente (`servico-manejo`) do evento `PesagemRegistrada`, comunicando-se só por Kafka, com envelope CloudEvents binário e teste automatizado de idempotência.
- **Registro de uso de IA:** [`docs/IA.md`](../IA.md), seção `## Aula 02`.

## Onde está cada coisa

| O quê | Onde |
|---|---|
| Decisão do domínio | [`docs/adr/ADR-002-dominio-do-projeto.md`](../adr/ADR-002-dominio-do-projeto.md) |
| Publisher | [`servico-pesagem/`](../../servico-pesagem/) |
| Consumer idempotente | [`servico-manejo/`](../../servico-manejo/) |
| Teste de idempotência | [`servico-manejo/src/test/java/br/pucminas/aed/manejo/service/HistoricoPesagemServiceTest.java`](../../servico-manejo/src/test/java/br/pucminas/aed/manejo/service/HistoricoPesagemServiceTest.java) |
| Registro de uso de IA | [`docs/IA.md`](../IA.md) |

## Como rodar

Ver [`README.md`](../../README.md) na raiz — seção "Como subir o projeto numa máquina limpa".

Resumo:

```bash
docker compose up -d
mvn -f servico-pesagem/pom.xml clean package -DskipTests
mvn -f servico-manejo/pom.xml clean package -DskipTests
# terminal 1
java -jar servico-pesagem/target/servico-pesagem-1.0.jar
# terminal 2
java -jar servico-manejo/target/servico-manejo-1.0.jar
```

Testes (sem Docker):

```bash
mvn -f servico-manejo/pom.xml test
```

## Quem fez o quê

| Integrante | Contribuição nesta etapa |
|---|---|
| Paulo Cidrão Gomes Torres | ADR-002 (domínio, critérios, alternativas) |
| João Almeida Barbosa Júnior | Esqueleto do publisher e consumer, teste de idempotência |
| João Pedro Schlindwein | Controllers do publisher e do consumer |
| João Pedro Correia Barros | Domain — classes do evento (`PesagemRegistradaEvent`, publisher e consumer) |
| Matheus Chaves Ferreira | Services — envelope CloudEvents, dedup e efeito no mesmo commit |
| Rafael Corrêa Zart | Documentação — registro de uso de IA (`docs/IA.md`) e tag `entrega-aula-02`|

## Por onde começar a leitura

1. [`docs/adr/ADR-002-dominio-do-projeto.md`](../adr/ADR-002-dominio-do-projeto.md) — para entender o domínio e por que ele atende os quatro critérios.
2. [`servico-pesagem/src/main/java/br/pucminas/aed/pesagem/service/PesagemService.java`](../../servico-pesagem/src/main/java/br/pucminas/aed/pesagem/service/PesagemService.java) — o envelope CloudEvents e a chave de partição.
3. [`servico-manejo/src/main/java/br/pucminas/aed/manejo/service/HistoricoPesagemService.java`](../../servico-manejo/src/main/java/br/pucminas/aed/manejo/service/HistoricoPesagemService.java) — dedup e efeito no mesmo commit.
4. [`servico-manejo/.../HistoricoPesagemServiceTest.java`](../../servico-manejo/src/test/java/br/pucminas/aed/manejo/service/HistoricoPesagemServiceTest.java) — a prova de que reentrega não duplica o efeito.
