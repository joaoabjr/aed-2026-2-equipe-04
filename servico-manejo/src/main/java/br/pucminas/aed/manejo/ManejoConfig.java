package br.pucminas.aed.manejo;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import br.pucminas.aed.manejo.domain.PesagemRegistradaEvent;

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
 */
@Configuration
public class ManejoConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
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
}
