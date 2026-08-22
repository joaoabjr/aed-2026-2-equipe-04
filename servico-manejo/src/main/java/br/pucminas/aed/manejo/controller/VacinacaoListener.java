package br.pucminas.aed.manejo.controller;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import br.pucminas.aed.manejo.domain.VacinacaoRegistradaEvent;
import br.pucminas.aed.manejo.service.HistoricoVacinacaoService;

@Component
public class VacinacaoListener {

    private static final String CABECALHO_ID = "ce_id";

    private final HistoricoVacinacaoService historicoVacinacaoService;

    public VacinacaoListener(HistoricoVacinacaoService historicoVacinacaoService) {
        this.historicoVacinacaoService = historicoVacinacaoService;
    }

    @KafkaListener(topics = "${demo.topico}", groupId = "manejo")
    public void aoRegistrarVacinacao(ConsumerRecord<String, VacinacaoRegistradaEvent> registro,
                                   Acknowledgment ack) {

        String eventoId = lerCabecalho(registro, CABECALHO_ID);

        historicoVacinacaoService.processar(eventoId, registro.value());

        ack.acknowledge(); // confirma DEPOIS do commit da transacao
    }

    private String lerCabecalho(ConsumerRecord<String, VacinacaoRegistradaEvent> registro, String nome) {
        Header cabecalho = registro.headers().lastHeader(nome);
        if (cabecalho == null) {
            return null;
        }
        return new String(cabecalho.value(), StandardCharsets.UTF_8);
    }
}
