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

import br.pucminas.aed.manejo.domain.LoteMovidoDePastoEvent;

@Service
public class ManejoPastoService {

    private static final Logger log = LoggerFactory.getLogger(ManejoPastoService.class);
    private static final String TYPE = "gado.lote.movido-de-pasto.v1";
    private static final String SOURCE = "/fazenda-corte/manejo-pasto";

    private final LoteService loteService;
    private final KafkaTemplate<String, LoteMovidoDePastoEvent> kafkaTemplate;
    private final String topico;

    public ManejoPastoService(LoteService loteService,
                              KafkaTemplate<String, LoteMovidoDePastoEvent> kafkaTemplate,
                              @Value("${demo.topico-lote-movido-de-pasto}") String topico) {
        this.loteService = loteService;
        this.kafkaTemplate = kafkaTemplate;
        this.topico = topico;
    }

    @Transactional(readOnly = true)
    public void mover(LoteMovidoDePastoEvent evento) {
        loteService.buscarPorId(evento.getLoteId())
                .orElseThrow(() -> new IllegalArgumentException("Lote com ID " + evento.getLoteId() + " nao existe"));
        if (evento.getPastoOrigemId().equals(evento.getPastoDestinoId())) {
            throw new IllegalArgumentException("Pasto de origem e destino devem ser diferentes");
        }

        ProducerRecord<String, LoteMovidoDePastoEvent> registro =
                new ProducerRecord<>(topico, evento.getLoteId(), evento);
        adicionarCabecalho(registro, "ce_specversion", "1.0");
        adicionarCabecalho(registro, "ce_id", evento.getEventoId());
        adicionarCabecalho(registro, "ce_source", SOURCE);
        adicionarCabecalho(registro, "ce_type", TYPE);
        adicionarCabecalho(registro, "ce_time", evento.getOcorridoEm().toString());
        adicionarCabecalho(registro, "ce_subject", "lote/" + evento.getLoteId());
        adicionarCabecalho(registro, "ce_datacontenttype", "application/json");

        CompletableFuture<SendResult<String, LoteMovidoDePastoEvent>> resultado = kafkaTemplate.send(registro);
        resultado.whenComplete((envio, erro) -> {
            if (erro != null) {
                log.error("falha ao publicar movimento de pasto {}", evento.getEventoId(), erro);
                return;
            }
            log.info("lote movido de pasto publicado  evento={}  topico={}  particao={}  offset={}",
                    evento.getEventoId(), envio.getRecordMetadata().topic(), envio.getRecordMetadata().partition(),
                    envio.getRecordMetadata().offset());
        });
    }

    private void adicionarCabecalho(ProducerRecord<String, LoteMovidoDePastoEvent> registro,
                                    String nome, String valor) {
        registro.headers().add(new RecordHeader(nome, valor.getBytes(StandardCharsets.UTF_8)));
    }
}
