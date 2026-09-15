package br.pucminas.aed.expedicao;

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

import br.pucminas.aed.expedicao.domain.AnimalEmbarcadoParaAbateEvent;

@Configuration
public class ExpedicaoConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public ProducerFactory<String, AnimalEmbarcadoParaAbateEvent> producerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            ObjectMapper objectMapper) {
        Map<String, Object> propriedades = new HashMap<>();
        propriedades.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        propriedades.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        JsonSerializer<AnimalEmbarcadoParaAbateEvent> serializador = new JsonSerializer<>(objectMapper);
        serializador.setAddTypeInfo(false);
        return new DefaultKafkaProducerFactory<>(propriedades, new StringSerializer(), serializador);
    }

    @Bean
    public KafkaTemplate<String, AnimalEmbarcadoParaAbateEvent> kafkaTemplate(
            ProducerFactory<String, AnimalEmbarcadoParaAbateEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public NewTopic topicoEmbarque(@Value("${demo.topico}") String nomeDoTopico) {
        return new NewTopic(nomeDoTopico, 3, (short) 1);
    }
}
