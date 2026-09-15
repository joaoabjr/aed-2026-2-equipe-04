package br.pucminas.aed.expedicao.service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import br.pucminas.aed.expedicao.domain.AnimalEmbarcadoParaAbateEvent;

@Service
public class ExpedicaoService {

    private static final String TYPE = "gado.animal.embarcado-para-abate.v1";
    private static final String SOURCE = "/fazenda-corte/expedicao-service";

    private final KafkaTemplate<String, AnimalEmbarcadoParaAbateEvent> kafkaTemplate;
    private final ExpedicaoCallbackService callbackService;
    private final String topico;

    public ExpedicaoService(KafkaTemplate<String, AnimalEmbarcadoParaAbateEvent> kafkaTemplate,
                            ExpedicaoCallbackService callbackService,
                            @Value("${demo.topico}") String topico) {
        this.kafkaTemplate = kafkaTemplate;
        this.callbackService = callbackService;
        this.topico = topico;
    }

    public void publicar(AnimalEmbarcadoParaAbateEvent evento) {
        ProducerRecord<String, AnimalEmbarcadoParaAbateEvent> registro =
                new ProducerRecord<>(topico, evento.getAnimalId(), evento);
        adicionarCabecalho(registro, "ce_specversion", "1.0");
        adicionarCabecalho(registro, "ce_id", evento.getEventoId());
        adicionarCabecalho(registro, "ce_source", SOURCE);
        adicionarCabecalho(registro, "ce_type", TYPE);
        adicionarCabecalho(registro, "ce_time", evento.getOcorridoEm().toString());
        adicionarCabecalho(registro, "ce_subject", "animal/" + evento.getAnimalId());
        adicionarCabecalho(registro, "ce_datacontenttype", "application/json");

        CompletableFuture<SendResult<String, AnimalEmbarcadoParaAbateEvent>> resultado = kafkaTemplate.send(registro);
        callbackService.tratar(resultado, evento.getEventoId());
    }

    private void adicionarCabecalho(ProducerRecord<String, AnimalEmbarcadoParaAbateEvent> registro,
                                    String nome, String valor) {
        registro.headers().add(new RecordHeader(nome, valor.getBytes(StandardCharsets.UTF_8)));
    }
}
