package br.pucminas.aed.manejo;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import br.pucminas.aed.manejo.domain.PesagemRegistradaEvent;
import br.pucminas.aed.manejo.domain.VacinacaoRegistradaEvent;
import br.pucminas.aed.manejo.domain.LoteFormadoEvent;
import br.pucminas.aed.manejo.domain.LoteMovidoDePastoEvent;
import br.pucminas.aed.manejo.domain.AnimalRejeitadoNoEmbarqueEvent;

/**
 * Mesma logica do PesagemConfig do lado publisher, espelhada aqui: o
 * ObjectMapper com JavaTimeModule garante que Instant seja lido como texto
 * ISO-8601, nao como numero.
 *
 * CONSUMIDOR TOLERANTE: o JsonDeserializer aponta para a classe deste
 * servico (br.pucminas.aed.manejo.domain.PesagemRegistradaEvent), que declara
 * MENOS campos do que o publisher publica (falta metodoDePesagem, de
 * proposito). Campos desconhecidos sao ignorados por @JsonIgnoreProperties
 * na propria classe de dominio.
 *
 * ack-mode MANUAL no application.yml + ContainerProperties.AckMode.MANUAL
 * aqui: a confirmacao do offset fica sob controle do listener, que so chama
 * ack.acknowledge() DEPOIS que o commit da transacao terminou.
 *
 * TRES ConsumerFactory/ContainerFactory distintos, um por group.id:
 *   - "manejo" (kafkaListenerContainerFactory): PesagemListener, efeito de
 *     negocio + dedup, ack manual apos commit.
 *   - "manejo-vacinacao" (vacinacaoKafkaListenerContainerFactory):
 *     VacinacaoListener. Precisa ser um group.id PROPRIO — reaproveitar
 *     "manejo" no mesmo topico faria os dois listeners disputarem as
 *     particoes de UM topico dentro do MESMO grupo, tirando particoes do
 *     PesagemListener que ja funciona (etapa 1).
 *   - "pesagem-agregador" (pesagemAgregadoKafkaListenerContainerFactory):
 *     PesagemAgregadaPorMinutoListener. Group.id proprio para receber o
 *     stream inteiro do topico de pesagem, independente do que "manejo" ja
 *     consome — dois grupos diferentes no mesmo topico nao dividem
 *     particoes entre si, cada grupo le tudo.
 */
@Configuration
public class ManejoConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public ProducerFactory<String, LoteFormadoEvent> loteFormadoProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            ObjectMapper objectMapper) {
        Map<String, Object> propriedades = new HashMap<>();
        propriedades.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        propriedades.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        JsonSerializer<LoteFormadoEvent> serializador = new JsonSerializer<>(objectMapper);
        serializador.setAddTypeInfo(false);
        return new DefaultKafkaProducerFactory<>(propriedades, new StringSerializer(), serializador);
    }

    @Bean
    public KafkaTemplate<String, LoteFormadoEvent> loteFormadoKafkaTemplate(
            ProducerFactory<String, LoteFormadoEvent> loteFormadoProducerFactory) {
        return new KafkaTemplate<>(loteFormadoProducerFactory);
    }

    @Bean
    public NewTopic topicoLoteFormado(@Value("${demo.topico-lote-formado}") String nomeDoTopico) {
        return new NewTopic(nomeDoTopico, 3, (short) 1);
    }

    @Bean
    public ProducerFactory<String, LoteMovidoDePastoEvent> loteMovidoDePastoProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            ObjectMapper objectMapper) {
        Map<String, Object> propriedades = new HashMap<>();
        propriedades.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        propriedades.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        JsonSerializer<LoteMovidoDePastoEvent> serializador = new JsonSerializer<>(objectMapper);
        serializador.setAddTypeInfo(false);
        return new DefaultKafkaProducerFactory<>(propriedades, new StringSerializer(), serializador);
    }

    @Bean
    public KafkaTemplate<String, LoteMovidoDePastoEvent> loteMovidoDePastoKafkaTemplate(
            ProducerFactory<String, LoteMovidoDePastoEvent> loteMovidoDePastoProducerFactory) {
        return new KafkaTemplate<>(loteMovidoDePastoProducerFactory);
    }

    @Bean
    public NewTopic topicoLoteMovidoDePasto(@Value("${demo.topico-lote-movido-de-pasto}") String nomeDoTopico) {
        return new NewTopic(nomeDoTopico, 3, (short) 1);
    }

    @Bean
    public ConsumerFactory<String, PesagemRegistradaEvent> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId,
            ObjectMapper objectMapper) {

        Map<String, Object> propriedades = new HashMap<String, Object>();
        propriedades.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        propriedades.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        propriedades.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<PesagemRegistradaEvent> deserializadorJson =
                new JsonDeserializer<PesagemRegistradaEvent>(PesagemRegistradaEvent.class, objectMapper);
        deserializadorJson.addTrustedPackages("br.pucminas.aed.manejo.domain");
        deserializadorJson.setUseTypeHeaders(false); // o contrato e o JSON, nao um header interno do Spring

        return new DefaultKafkaConsumerFactory<String, PesagemRegistradaEvent>(
                propriedades,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<PesagemRegistradaEvent>(deserializadorJson));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PesagemRegistradaEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, PesagemRegistradaEvent> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, PesagemRegistradaEvent> fabrica =
                new ConcurrentKafkaListenerContainerFactory<String, PesagemRegistradaEvent>();
        fabrica.setConsumerFactory(consumerFactory);
        fabrica.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return fabrica;
    }

    @Bean
    public ConsumerFactory<String, VacinacaoRegistradaEvent> vacinacaoConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${demo.grupo-vacinacao}") String groupId,
            ObjectMapper objectMapper) {

        Map<String, Object> propriedades = new HashMap<String, Object>();
        propriedades.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        propriedades.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        propriedades.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<VacinacaoRegistradaEvent> deserializadorJson =
                new JsonDeserializer<VacinacaoRegistradaEvent>(VacinacaoRegistradaEvent.class, objectMapper);
        deserializadorJson.addTrustedPackages("br.pucminas.aed.manejo.domain");
        deserializadorJson.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<String, VacinacaoRegistradaEvent>(
                propriedades,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<VacinacaoRegistradaEvent>(deserializadorJson));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, VacinacaoRegistradaEvent> vacinacaoKafkaListenerContainerFactory(
            ConsumerFactory<String, VacinacaoRegistradaEvent> vacinacaoConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, VacinacaoRegistradaEvent> fabrica =
                new ConcurrentKafkaListenerContainerFactory<String, VacinacaoRegistradaEvent>();
        fabrica.setConsumerFactory(vacinacaoConsumerFactory);
        fabrica.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return fabrica;
    }

    @Bean
    public ConsumerFactory<String, PesagemRegistradaEvent> pesagemAgregadoConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            ObjectMapper objectMapper) {

        Map<String, Object> propriedades = new HashMap<String, Object>();
        propriedades.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        propriedades.put(ConsumerConfig.GROUP_ID_CONFIG, "pesagem-agregador");
        propriedades.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<PesagemRegistradaEvent> deserializadorJson =
                new JsonDeserializer<PesagemRegistradaEvent>(PesagemRegistradaEvent.class, objectMapper);
        deserializadorJson.addTrustedPackages("br.pucminas.aed.manejo.domain");
        deserializadorJson.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<String, PesagemRegistradaEvent>(
                propriedades,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<PesagemRegistradaEvent>(deserializadorJson));
    }

    /**
     * Sem AckMode.MANUAL de proposito: a agregacao e so observabilidade
     * (nao decide nem grava nada que exija exactly-once), entao o commit
     * automatico de offset (default do container) e suficiente — nao ha
     * transacao para o ack esperar.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PesagemRegistradaEvent> pesagemAgregadoKafkaListenerContainerFactory(
            ConsumerFactory<String, PesagemRegistradaEvent> pesagemAgregadoConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, PesagemRegistradaEvent> fabrica =
                new ConcurrentKafkaListenerContainerFactory<String, PesagemRegistradaEvent>();
        fabrica.setConsumerFactory(pesagemAgregadoConsumerFactory);
        return fabrica;
    }

    /**
     * Consumidores do event store do agregado Lote (localizacao) — ADR-005.
     * Group.id proprio ("lote-localizacao"), ack MANUAL: o event store e a
     * projecao sao a fonte de verdade (nao so observabilidade), entao o
     * offset so confirma depois que a transacao do LoteLocalizacaoService
     * terminou — mesmo desenho do PesagemListener/VacinacaoListener.
     */
    @Bean
    public ConsumerFactory<String, LoteFormadoEvent> loteFormadoConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${demo.grupo-lote-localizacao}") String groupId,
            ObjectMapper objectMapper) {

        Map<String, Object> propriedades = new HashMap<>();
        propriedades.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        propriedades.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        propriedades.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<LoteFormadoEvent> deserializadorJson =
                new JsonDeserializer<>(LoteFormadoEvent.class, objectMapper);
        deserializadorJson.addTrustedPackages("br.pucminas.aed.manejo.domain");
        deserializadorJson.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(propriedades, new StringDeserializer(),
                new ErrorHandlingDeserializer<>(deserializadorJson));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, LoteFormadoEvent> loteFormadoKafkaListenerContainerFactory(
            ConsumerFactory<String, LoteFormadoEvent> loteFormadoConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, LoteFormadoEvent> fabrica =
                new ConcurrentKafkaListenerContainerFactory<>();
        fabrica.setConsumerFactory(loteFormadoConsumerFactory);
        fabrica.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return fabrica;
    }

    @Bean
    public ConsumerFactory<String, LoteMovidoDePastoEvent> loteMovidoDePastoConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${demo.grupo-lote-localizacao}") String groupId,
            ObjectMapper objectMapper) {

        Map<String, Object> propriedades = new HashMap<>();
        propriedades.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        propriedades.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        propriedades.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<LoteMovidoDePastoEvent> deserializadorJson =
                new JsonDeserializer<>(LoteMovidoDePastoEvent.class, objectMapper);
        deserializadorJson.addTrustedPackages("br.pucminas.aed.manejo.domain");
        deserializadorJson.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(propriedades, new StringDeserializer(),
                new ErrorHandlingDeserializer<>(deserializadorJson));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, LoteMovidoDePastoEvent> loteMovidoDePastoKafkaListenerContainerFactory(
            ConsumerFactory<String, LoteMovidoDePastoEvent> loteMovidoDePastoConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, LoteMovidoDePastoEvent> fabrica =
                new ConcurrentKafkaListenerContainerFactory<>();
        fabrica.setConsumerFactory(loteMovidoDePastoConsumerFactory);
        fabrica.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return fabrica;
    }

    /**
     * Consumidor do caminho de exceção AnimalRejeitadoNoEmbarque (ADR-002).
     * Group.id proprio ("manejo-rejeicao-embarque"), ack MANUAL: a
     * compensação (dieta + encerramento de venda) e' efeito de negocio
     * permanente, nao so' observabilidade — mesmo desenho de
     * PesagemListener/VacinacaoListener.
     */
    @Bean
    public ConsumerFactory<String, AnimalRejeitadoNoEmbarqueEvent> rejeicaoEmbarqueConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${demo.grupo-rejeicao-embarque}") String groupId,
            ObjectMapper objectMapper) {

        Map<String, Object> propriedades = new HashMap<>();
        propriedades.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        propriedades.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        propriedades.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<AnimalRejeitadoNoEmbarqueEvent> deserializadorJson =
                new JsonDeserializer<>(AnimalRejeitadoNoEmbarqueEvent.class, objectMapper);
        deserializadorJson.addTrustedPackages("br.pucminas.aed.manejo.domain");
        deserializadorJson.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(propriedades, new StringDeserializer(),
                new ErrorHandlingDeserializer<>(deserializadorJson));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AnimalRejeitadoNoEmbarqueEvent> rejeicaoEmbarqueKafkaListenerContainerFactory(
            ConsumerFactory<String, AnimalRejeitadoNoEmbarqueEvent> rejeicaoEmbarqueConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, AnimalRejeitadoNoEmbarqueEvent> fabrica =
                new ConcurrentKafkaListenerContainerFactory<>();
        fabrica.setConsumerFactory(rejeicaoEmbarqueConsumerFactory);
        fabrica.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return fabrica;
    }
}
