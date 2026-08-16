package br.pucminas.aed.manejo.controller;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import br.pucminas.aed.manejo.domain.PesagemRegistradaEvent;
import br.pucminas.aed.manejo.service.HistoricoPesagemService;

/**
 * O consumidor que aguenta receber a mesma pesagem duas ou tres vezes.
 *
 * Ordem deliberada:
 *   1. processa dentro de uma transacao (no HistoricoPesagemService);
 *   2. so DEPOIS do commit e que confirma o offset (ack.acknowledge()).
 *
 * Confirmar depois de processar e o que caracteriza at-least-once — e e
 * por isso que a idempotencia e obrigatoria, nao op cional.
 *
 * Este listener nao decide nada: traduz ConsumerRecord (infraestrutura) em
 * conceitos de dominio e delega. Toda a regra fica no service.
 */
@Component
public class PesagemListener {

    private static final String CABECALHO_ID = "ce_id";

    private final HistoricoPesagemService historicoPesagemService;

    public PesagemListener(HistoricoPesagemService historicoPesagemService) {
        this.historicoPesagemService = historicoPesagemService;
    }

    @KafkaListener(topics = "${demo.topico}", groupId = "manejo")
    public void aoRegistrarPesagem(ConsumerRecord<String, PesagemRegistradaEvent> registro,
                                    Acknowledgment ack) {

        String eventoId = lerCabecalho(registro, CABECALHO_ID);

        historicoPesagemService.processar(eventoId, registro.value());

        ack.acknowledge(); // confirma DEPOIS do commit da transacao
    }

    private String lerCabecalho(ConsumerRecord<String, PesagemRegistradaEvent> registro, String nome) {
        Header cabecalho = registro.headers().lastHeader(nome);
        if (cabecalho == null) {
            return null;
        }
        return new String(cabecalho.value(), StandardCharsets.UTF_8);
    }
}
