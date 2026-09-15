package br.pucminas.aed.manejo.service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.aed.manejo.domain.Animal;
import br.pucminas.aed.manejo.domain.LoteFormadoEvent;

@Service
public class LoteFormacaoService {

    private static final Logger log = LoggerFactory.getLogger(LoteFormacaoService.class);
    private static final String TYPE = "gado.lote.formado.v1";
    private static final String SOURCE = "/fazenda-corte/manejo-service";

    private final LoteService loteService;
    private final AnimalService animalService;
    private final KafkaTemplate<String, LoteFormadoEvent> kafkaTemplate;
    private final String topico;

    public LoteFormacaoService(LoteService loteService, AnimalService animalService,
                                KafkaTemplate<String, LoteFormadoEvent> kafkaTemplate,
                                @Value("${demo.topico-lote-formado}") String topico) {
        this.loteService = loteService;
        this.animalService = animalService;
        this.kafkaTemplate = kafkaTemplate;
        this.topico = topico;
    }

    @Transactional(readOnly = true)
    public void formar(LoteFormadoEvent evento) {
        loteService.buscarPorId(evento.getLoteId())
                .orElseThrow(() -> new IllegalArgumentException("Lote com ID " + evento.getLoteId() + " nao existe"));

        for (String animalId : evento.getAnimalIds()) {
            Animal animal = animalService.buscarPorId(animalId)
                    .orElseThrow(() -> new IllegalArgumentException("Animal com ID " + animalId + " nao existe"));
            if (!evento.getLoteId().equals(animal.getLote().getId())) {
                throw new IllegalArgumentException("Animal com ID " + animalId + " nao pertence ao lote " + evento.getLoteId());
            }
        }

        ProducerRecord<String, LoteFormadoEvent> registro = new ProducerRecord<>(topico, evento.getLoteId(), evento);
        adicionarCabecalho(registro, "ce_specversion", "1.0");
        adicionarCabecalho(registro, "ce_id", evento.getEventoId());
        adicionarCabecalho(registro, "ce_source", SOURCE);
        adicionarCabecalho(registro, "ce_type", TYPE);
        adicionarCabecalho(registro, "ce_time", evento.getOcorridoEm().toString());
        adicionarCabecalho(registro, "ce_subject", "lote/" + evento.getLoteId());
        adicionarCabecalho(registro, "ce_datacontenttype", "application/json");

        CompletableFuture<SendResult<String, LoteFormadoEvent>> resultado = kafkaTemplate.send(registro);
        resultado.whenComplete((envio, erro) -> {
            if (erro != null) {
                log.error("falha ao publicar lote formado {}", evento.getEventoId(), erro);
                return;
            }
            log.info("lote formado publicado  evento={}  topico={}  particao={}  offset={}",
                    evento.getEventoId(), envio.getRecordMetadata().topic(), envio.getRecordMetadata().partition(),
                    envio.getRecordMetadata().offset());
        });
    }

    private void adicionarCabecalho(ProducerRecord<String, LoteFormadoEvent> registro, String nome, String valor) {
        registro.headers().add(new RecordHeader(nome, valor.getBytes(StandardCharsets.UTF_8)));
    }
}
