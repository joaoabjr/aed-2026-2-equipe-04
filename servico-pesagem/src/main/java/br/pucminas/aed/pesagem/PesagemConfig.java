package br.pucminas.aed.pesagem;

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

import br.pucminas.aed.pesagem.domain.PesagemRegistradaEvent;

/**
 * Fica tudo neste um lugar so, no pacote raiz (nao existe pacote "config" —
 * o enunciado so reconhece controller, domain e service como pacotes; classes
 * de configuracao moram na raiz, ao lado da Application).
 *
 * O DETALHE QUE MAIS IMPORTA AQUI: por que este ObjectMapper existe.
 * O JsonSerializer padrao do spring-kafka grava Instant como epoch (numero),
 * nao como texto ISO-8601. O contrato entre os dois servicos e o JSON no fio,
 * e ele nao pode depender do padrao de serializacao de uma biblioteca Java —
 * por isso o WRITE_DATES_AS_TIMESTAMPS e desligado explicitamente aqui.
 */
@Configuration
public class PesagemConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public ProducerFactory<String, PesagemRegistradaEvent> producerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            ObjectMapper objectMapper) {

        Map<String, Object> propriedades = new HashMap<String, Object>();
        propriedades.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        propriedades.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        JsonSerializer<PesagemRegistradaEvent> serializadorJson = new JsonSerializer<PesagemRegistradaEvent>(objectMapper);
        serializadorJson.setAddTypeInfo(false); // sem cabecalho __TypeId__: o contrato e so o JSON

        return new DefaultKafkaProducerFactory<String, PesagemRegistradaEvent>(
                propriedades, new StringSerializer(), serializadorJson);
    }

    @Bean
    public KafkaTemplate<String, PesagemRegistradaEvent> kafkaTemplate(
            ProducerFactory<String, PesagemRegistradaEvent> producerFactory) {
        return new KafkaTemplate<String, PesagemRegistradaEvent>(producerFactory);
    }

    /** Mesma decisao do demo: 3 particoes, criadas na subida do publisher. */
    @Bean
    public NewTopic topicoPesagemRegistrada(@Value("${demo.topico}") String nomeDoTopico) {
        return new NewTopic(nomeDoTopico, 3, (short) 1);
    }
}
