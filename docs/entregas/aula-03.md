# Entrega — Aula 03

## O que foi feito nesta etapa

- **Parte A:** contrato do evento `VacinacaoRegistrada` documentado em [`docs/contrato.md`](../contrato.md) — campos, obrigatoriedade, formato de data, chave de partição e regra de compatibilidade (BACKWARD).
- **Parte B:** segundo agregador do tópico `gado.animal.pesagem-registrada.v1` — `PesagemAgregadaPorMinutoListener`, em `servico-manejo/controller`, com `group.id = pesagem-agregador` próprio, rodando ao lado do `PesagemListener` da etapa 1 sem competir por partições.
- **Correção de bug:** o `VacinacaoListener` (adicionado entre a aula 02 e esta etapa) estava com `group.id` igual ao do `PesagemListener` ("manejo") e resolvendo o mesmo `${demo.topico}` de pesagem — os dois listeners disputariam as 3 partições do tópico de pesagem dentro do mesmo grupo, roubando partições do consumidor que já funcionava. Corrigido com `group.id = manejo-vacinacao` e tópico próprio (`${demo.topico-vacinacao}`), cada um com seu `ConsumerFactory`/`ConcurrentKafkaListenerContainerFactory`.
- **Nota no ADR-002:** esclarecendo que vacinação como pré-requisito de embarque é um evento do mesmo domínio (venda/embarque), não o "processo de vacinação isolado" já recusado nas alternativas.
- **Registro de uso de IA:** [`docs/IA.md`](../IA.md), seção `## Aula 03`, com uma recusa registrada.

## As quatro perguntas

### 1. Qual pergunta de negócio a agregação responde?

Peso médio do rebanho por janela de 1 minuto — para acompanhar a variação/ganho de peso do rebanho ao longo do dia. Não é uma métrica de infraestrutura ("quantos eventos chegaram por minuto"): o número que sai do agregador é `pesoMedioKg`, algo que zootecnia/manejo usa para decidir formação de lote e dieta, o mesmo tipo de decisão que já motivou o ADR-002 a tratar `PesagemRegistrada` como evento central do domínio.

### 2. Qual relógio foi escolhido — ocorrência ou chegada — e por quê?

**Hora de ocorrência** (`ocorridoEm`, event time), não hora de chegada ao consumidor.

A pesagem é lançada pela balança eletrônica próxima do instante real em que acontece, então `ocorridoEm` já reflete bem quando o animal foi de fato pesado. Usar hora de chegada faria a janela representar "quando o consumidor processou", que muda dependendo de reprocessamento, filas, restart do serviço ou lentidão de rede — nada disso é uma propriedade do animal, é uma propriedade da infraestrutura do dia. Uma curva de peso do rebanho que muda de forma só porque o consumidor ficou fora do ar por alguns minutos não seria confiável para decisão de negócio.

O custo dessa escolha é ter que lidar explicitamente com atraso (pergunta 3) — um problema que hora de chegada não teria, mas ao custo de o número "peso médio às 14:32" poder significar coisas diferentes a cada execução.

### 3. O que acontece com um evento que chega atrasado?

Depende de quão atrasado:

- Se a janela do evento (`ocorridoEm` truncado para o minuto) ainda não foi fechada, o evento entra normalmente no acumulador daquela janela — pequenos atrasos de rede ou de processamento não perdem dado.
- Cada janela só é fechada `TOLERANCIA_FECHAMENTO` (15s) depois do seu fim "de direito" pelo relógio, dando uma margem extra antes de publicar o resultado.
- Se o evento chega depois que a janela **já foi fechada e publicada no log**, ele é descartado do agregado — não reabre a janela. Fica um log de aviso (`pesagem atrasada descartada do agregado`, com partição/offset/`ocorridoEm`/janela esperada) para tornar essa perda visível, em vez de descartar em silêncio.

A alternativa (reabrir a janela e publicar de novo) foi descartada de propósito: geraria dois valores diferentes de `pesoMedioKg` para o mesmo minuto no log, o que é pior para quem consome esse log do que aceitar a perda de uma amostra chegada tarde demais.

### 4. Se o fluxo fosse reprocessado do zero amanhã, o resultado seria o mesmo? Por que isso é aceitável?

**Não necessariamente o mesmo, e isso é aceitável — com uma ressalva.**

O agregado depende de duas coisas: (a) o conteúdo dos eventos, que não muda num replay (o tópico é a fonte de verdade); e (b) o **relógio de parede real** no momento do processamento, porque o fechamento de cada janela usa `Instant.now()` comparado contra `ocorridoEm + 1min + TOLERANCIA_FECHAMENTO`. Num replay feito "amanhã", `Instant.now()` está sempre muito à frente do fim de qualquer janela antiga — então, na prática, cada janela fecharia quase imediatamente após o primeiro evento seguinte chegar, mas o **conjunto de eventos que caiu dentro de cada janela de 1 minuto seria o mesmo**, porque isso é decidido por `ocorridoEm` (dado do evento, determinístico), não por quando o processamento aconteceu.

A ressalva é sobre eventos que hoje seriam classificados como "atrasados" (chegaram depois da janela fechar) e descartados: num replay linear e completo do tópico do zero, esse cenário não se repete — o consumidor lê os eventos na ordem em que foram publicados, então não há "atraso" independente se ninguém interromper o replay no meio. Atraso só acontece de verdade se o replay for parcial ou pausado no meio de uma janela. Nesse caso o resultado poderia, sim, divergir de uma execução anterior que teve um gap real de rede/processo.

Isso é aceitável porque a garantia que este agregador se propõe a dar não é "reprocessamento bit-a-bit idêntico sob qualquer interrupção" — é "responde, com base no relógio do negócio (`ocorridoEm`), qual foi o peso médio do rebanho a cada minuto, tolerando uma margem pequena e documentada de atraso". Diferente do `HistoricoPesagemService` (que precisa de exactly-once porque grava estado permanente e a duplicação corromperia o histórico), a agregação por minuto é observabilidade de negócio: um valor levemente diferente entre duas execuções interrompidas de formas diferentes é um trade-off aceito, não um bug — e é exatamente por isso que a ausência de "manual ack + transação" no `pesagemAgregadoKafkaListenerContainerFactory` (ao contrário do `PesagemListener`) é uma escolha deliberada, não um descuido.

## Onde está cada coisa

| O quê | Onde |
|---|---|
| Contrato do evento `VacinacaoRegistrada` | [`docs/contrato.md`](../contrato.md) |
| Agregador (Parte B) | [`servico-manejo/.../controller/PesagemAgregadaPorMinutoListener.java`](../../servico-manejo/src/main/java/br/pucminas/aed/manejo/controller/PesagemAgregadaPorMinutoListener.java) |
| Consumer factories / group.id por listener | [`servico-manejo/.../ManejoConfig.java`](../../servico-manejo/src/main/java/br/pucminas/aed/manejo/ManejoConfig.java) |
| Correção do `VacinacaoListener` | [`servico-manejo/.../controller/VacinacaoListener.java`](../../servico-manejo/src/main/java/br/pucminas/aed/manejo/controller/VacinacaoListener.java) |
| Nota de domínio (vacinação como pré-requisito de embarque) | [`docs/adr/ADR-002-dominio-do-projeto.md`](../adr/ADR-002-dominio-do-projeto.md) |
| Registro de uso de IA | [`docs/IA.md`](../IA.md) |

## Como rodar

Ver [`README.md`](../../README.md) — seções "Como rodar o servico-manejo" e "Como observar o agregador de pesagem".

Resumo:

```bash
docker compose up -d
mvn -f servico-pesagem/pom.xml clean package -DskipTests
mvn -f servico-manejo/pom.xml clean package -DskipTests
# terminal 1 — publisher
java -jar servico-pesagem/target/servico-pesagem-1.0.jar
# terminal 2 — os dois consumidores de pesagem (PesagemListener + agregador) sobem juntos, no mesmo processo
java -jar servico-manejo/target/servico-manejo-1.0.jar
```

O resultado do agregador aparece no log do `servico-manejo` como `peso medio do rebanho por minuto  janela=[...)  amostras=...  pesoMedioKg=...`.

## Quem fez o quê

| Integrante | Contribuição nesta etapa |
|---|---|
| João Almeida Barbosa Júnior | Contrato do evento (`docs/contrato.md`), agregador `PesagemAgregadaPorMinutoListener`, correção do `group.id`/tópico do `VacinacaoListener`, nota no ADR-002 |

## Por onde começar a leitura

1. [`docs/contrato.md`](../contrato.md) — o contrato do evento de vacinação.
2. [`servico-manejo/.../controller/PesagemAgregadaPorMinutoListener.java`](../../servico-manejo/src/main/java/br/pucminas/aed/manejo/controller/PesagemAgregadaPorMinutoListener.java) — a decisão de relógio (event time) e o tratamento de atraso, comentados na classe.
3. [`servico-manejo/.../ManejoConfig.java`](../../servico-manejo/src/main/java/br/pucminas/aed/manejo/ManejoConfig.java) — os três pares `ConsumerFactory`/`ContainerFactory`, um por `group.id`.
4. [`docs/adr/ADR-002-dominio-do-projeto.md`](../adr/ADR-002-dominio-do-projeto.md) — a nota ao final, distinguindo "vacinação como pré-requisito de embarque" do "processo de vacinação isolado" já recusado.
