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
