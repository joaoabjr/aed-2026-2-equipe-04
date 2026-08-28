# Registro do uso de IA

Ferramenta usada: Claude (Anthropic), em conversa de apoio à modelagem e ao esqueleto de código.

## Aula 02

### Interação 1 — desenho da classe do evento

**Pedido:** montar a classe `PesagemRegistradaEvent`, seguindo o padrão do demo (`PedidoConfirmadoEvent`).

**Sugestão da IA:** usar um `record` Java para o evento, por ser mais enxuto (menos linhas, `equals`/`hashCode`/`toString` de graça).

**O que recusamos, e por quê:** recusamos o `record`. O enunciado da Seção B.1 exige "classe imutável explícita — campos `private final`, sem setter... Não use record: o objetivo é que os mecanismos fiquem à vista". Manter a classe explícita, mesmo sendo mais verbosa, é justamente o ponto pedagógico: quem lê o código vê o `Objects.requireNonNull` no construtor e a ausência de setter, em vez de confiar numa geração automática que esconde a decisão.

### Interação 2 — chave de partição

**Pedido:** qual campo usar como chave de partição do `ProducerRecord`.

**Sugestão da IA:** usar `animalId`, porque é a menor unidade cuja ordem o negócio exige — duas pesagens do mesmo animal precisam ser processadas na ordem em que ocorreram para a curva de peso fazer sentido; pesagens de animais diferentes podem ficar em partições diferentes sem problema.

**O que aceitamos, e por quê:** aceitamos, porque bate com o que o ADR-002 já tinha registrado sobre o critério "algo que vale reprocessar" — reconstruir a curva de peso de UM animal depende da ordem das leituras DAQUELE animal, não da ordem global do tópico.

### Interação 3 — o que o consumidor declara

**Pedido:** desenhar a classe tolerante do evento no `servico-manejo`.

**Sugestão da IA:** omitir `metodoDePesagem` da classe do consumidor, porque o serviço de manejo decide dieta e formação de lote a partir do peso, não de qual balança foi usada.

**O que aceitamos, e por quê:** aceitamos — é exatamente o padrão de consumidor tolerante do demo (`ItemDoPedidoVO` omitindo `preco`), e nos dá de graça a demonstração do checklist item "declare menos campos do que o produtor publica, de propósito".

### Interação 4 — revisão de um colega sobre persistência e nomenclatura

**Pedido:** um colega revisou o esqueleto e sugeriu, via IA, guardar `EventoProcessado` e `PesagemRegistro` como entidades JPA (`@Entity`, `@Table`) dentro do pacote `domain`, e renomear a classe que trata o retorno do `send()` para `ResultadoDePublicacao`.

**O que recusamos, e por quê:** recusamos as duas.

`@Entity`/`@Table` no `domain` contraria o enunciado diretamente — a Seção 12 diz que "domain importa só a biblioteca padrão e as anotações de serialização", e anotação JPA não é anotação de serialização, é acoplamento com um framework de persistência (o mesmo problema que o checklist item 13 verifica ao buscar `import org.springframework` dentro de `domain/`). Mantivemos a persistência em `HistoricoPesagemRepository`, via `JdbcTemplate`, fora do `domain` — o mesmo desenho do `EstoqueRepository` do demo.

`ResultadoDePublicacao`, sem sufixo, não está na lista fechada da Seção 12 (`Application, Config, Controller, Listener, Service, Repository, Event, VO`). A classe já existia com o nome `PesagemCallbackService` — mantivemos esse nome, que já descreve o papel (tratar o resultado do `send()`) com o sufixo correto.

**O que aceitamos, e por quê:** a parte boa da revisão sobreviveu — enriquecer o envelope CloudEvents com `ce_subject` (`animal/{animalId}`) e `ce_datacontenttype` (`application/json`), além dos quatro obrigatórios e do `ce_time`. São atributos opcionais do CloudEvents 1.0, baratos de incluir, e deixam o cabeçalho autossuficiente para quem só quer saber qual animal e qual formato de carga sem abrir o corpo da mensagem.

## Aula 03

### Interação 1 — como implementar a janela de 1 minuto

**Pedido:** implementar a agregação de peso médio do rebanho por janela de 1 minuto sobre o tópico `gado.animal.pesagem-registrada.v1`.

**Sugestão da IA:** usar Kafka Streams (`TimeWindows.of(Duration.ofMinutes(1))` sobre uma topology, com `KTable` de agregação), que já resolve fechamento de janela e late-arrival via watermark/grace period nativamente, em vez de reimplementar isso à mão.

**O que recusamos, e por quê:** recusamos introduzir uma dependência e um paradigma de processamento novo (topology do Kafka Streams) só para esta agregação. O enunciado pede "grupo de consumidores próprio", não uma stack de stream processing nova, e nenhum outro serviço do projeto usa Streams — adotar isso aqui quebraria a convenção de serviços simples e independentes na direção oposta (acoplaria o serviço a uma API mais pesada para resolver um problema pequeno). Implementamos a janela com `@KafkaListener` comum mais um acumulador em memória fechado por um `@Scheduled`, suficiente para o requisito e mais fácil de auditar linha a linha — o mesmo espírito que já tinha levado a recusar `record` na aula 02.

### Interação 2 — o que fazer com um evento atrasado

**Pedido:** decidir o que acontece quando um evento de pesagem chega depois que a janela de 1 minuto a que ele pertence já foi fechada e publicada no log.

**Sugestão da IA:** reabrir a janela, recalcular a média com a amostra atrasada e publicar um novo log de "correção" para aquele minuto — padrão comum em sistemas de streaming com dados atrasados (upsert do resultado).

**O que recusamos, e por quê:** recusamos. Reabrir e republicar geraria duas linhas de log com `pesoMedioKg` diferentes para o mesmo intervalo, e quem só acompanha o log (o resultado observável que o enunciado pede) não teria como saber qual das duas é a "final" sem comparar timestamps de publicação. Optamos por descartar o evento atrasado com um log de aviso explícito, aceitando perder uma amostra tardia em troca de cada janela aparecer exatamente uma vez no log — mais simples de verificar, e o enunciado pede para "descrever o que acontece" com o atraso, não para construir um pipeline de correção retroativa.

### Interação 3 — tipo do campo `validade`

**Pedido:** revisar o campo `validade` do evento de vacinação, declarado como `java.util.Date`, decidindo entre trocar para `Instant` ou manter `Date` documentando a conversão (as duas opções que o enunciado da aula 03 deixava em aberto).

**Sugestão da IA:** cogitar manter `Date` e só documentar no contrato como a serialização ISO-8601 acontece nesse caso (via o `StdDateFormat` padrão do Jackson, já que o campo compila e funciona hoje).

**O que recusamos, e por quê:** recusamos manter e só documentar. `Date` sem `JavaTimeModule` produz uma string ISO-8601 com formato diferente do resto do contrato (offset `+0000` em vez de `Z`), uma inconsistência que documentar não resolve — só a torna "oficial" em vez de corrigida. Como o campo ainda não tinha nenhum consumidor externo dependendo do formato antigo (a mesma razão que levou à regra de compatibilidade BACKWARD no contrato, não FULL), trocar para `Instant` agora — no publisher e na classe espelhada do consumidor — era o momento mais barato para eliminar o problema em vez de arrastá-lo.
