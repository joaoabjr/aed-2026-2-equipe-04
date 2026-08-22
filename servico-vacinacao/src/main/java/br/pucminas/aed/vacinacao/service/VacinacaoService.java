package br.pucminas.aed.vacinacao.service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import br.pucminas.aed.vacinacao.domain.VacinacaoRegistradaEvent;

@Service
public class VacinacaoService {

    private static final String TYPE = "gado.animal.vacinacao-registrada.v1";
    private static final String SOURCE = "/fazenda-corte/vacinacao-service";

    private final KafkaTemplate<String, VacinacaoRegistradaEvent> kafkaTemplate;
    private final VacinacaoCallbackService callbackService;
    private final String topico;

    public VacinacaoService(KafkaTemplate<String, VacinacaoRegistradaEvent> kafkaTemplate,
                           VacinacaoCallbackService callbackService,
                           @Value("${demo.topico}") String topico) {
        this.kafkaTemplate = kafkaTemplate;
        this.callbackService = callbackService;
        this.topico = topico;
    }

    public void publicar(VacinacaoRegistradaEvent evento) {

        ProducerRecord<String, VacinacaoRegistradaEvent> registro =
                new ProducerRecord<String, VacinacaoRegistradaEvent>(topico, evento.getAnimalId(), evento);

        adicionarCabecalho(registro, "ce_specversion", "1.0");
        adicionarCabecalho(registro, "ce_id", evento.getEventoId());
        adicionarCabecalho(registro, "ce_source", SOURCE);
        adicionarCabecalho(registro, "ce_type", TYPE);
        adicionarCabecalho(registro, "ce_time", evento.getOcorridoEm().toString());
        adicionarCabecalho(registro, "ce_subject", "animal/" + evento.getAnimalId());
        adicionarCabecalho(registro, "ce_datacontenttype", "application/json");

        CompletableFuture<SendResult<String, VacinacaoRegistradaEvent>> resultadoFuturo =
                kafkaTemplate.send(registro);

        callbackService.tratar(resultadoFuturo, evento.getEventoId());
    }

    private void adicionarCabecalho(ProducerRecord<String, VacinacaoRegistradaEvent> registro,
                                     String nome, String valor) {
        registro.headers().add(new RecordHeader(nome, valor.getBytes(StandardCharsets.UTF_8)));
    }
}