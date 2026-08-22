package br.pucminas.aed.vacinacao;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import br.pucminas.aed.vacinacao.domain.VacinacaoRegistradaEvent;


@Configuration
public class VacinacaoConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public ProducerFactory<String, VacinacaoRegistradaEvent> producerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            ObjectMapper objectMapper) {

        Map<String, Object> propriedades = new HashMap<String, Object>();
        propriedades.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        propriedades.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        JsonSerializer<VacinacaoRegistradaEvent> serializadorJson = new JsonSerializer<VacinacaoRegistradaEvent>(objectMapper);
        serializadorJson.setAddTypeInfo(false); // sem cabecalho __TypeId__: o contrato e so o JSON

        return new DefaultKafkaProducerFactory<String, VacinacaoRegistradaEvent>(
                propriedades, new StringSerializer(), serializadorJson);
    }

    @Bean
    public KafkaTemplate<String, VacinacaoRegistradaEvent> kafkaTemplate(
            ProducerFactory<String, VacinacaoRegistradaEvent> producerFactory) {
        return new KafkaTemplate<String, VacinacaoRegistradaEvent>(producerFactory);
    }

    /** Mesma decisao do demo: 3 particoes, criadas na subida do publisher. */
    @Bean
    public NewTopic topicoPesagemRegistrada(@Value("${demo.topico}") String nomeDoTopico) {
        return new NewTopic(nomeDoTopico, 3, (short) 1);
    }
}
