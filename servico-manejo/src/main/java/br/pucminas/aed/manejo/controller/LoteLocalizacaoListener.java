package br.pucminas.aed.manejo.controller;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import br.pucminas.aed.manejo.domain.LoteFormadoEvent;
import br.pucminas.aed.manejo.domain.LoteMovidoDePastoEvent;
import br.pucminas.aed.manejo.service.LoteLocalizacaoService;

/**
 * O consumidor do event store do agregado Lote (localizacao) — ADR-005.
 *
 * Group.id proprio ("lote-localizacao"), sem relacao com "manejo",
 * "manejo-vacinacao" ou "pesagem-agregador": nenhum consumidor lia os
 * topicos gado.lote.formado.v1 e gado.lote.movido-de-pasto.v1 antes desta
 * etapa — os eventos eram publicados e nunca consumidos.
 *
 * Ack MANUAL, como o PesagemListener: o event store e a projecao SAO a
 * fonte de verdade (nao e' so observabilidade, como o agregador de
 * pesagem), entao o offset so e' confirmado depois que o commit da
 * transacao (no service) terminou.
 */
@Component
public class LoteLocalizacaoListener {

    private final LoteLocalizacaoService loteLocalizacaoService;

    public LoteLocalizacaoListener(LoteLocalizacaoService loteLocalizacaoService) {
        this.loteLocalizacaoService = loteLocalizacaoService;
    }

    @KafkaListener(topics = "${demo.topico-lote-formado}", groupId = "${demo.grupo-lote-localizacao}",
            containerFactory = "loteFormadoKafkaListenerContainerFactory")
    public void aoFormarLote(ConsumerRecord<String, LoteFormadoEvent> registro, Acknowledgment ack) {
        loteLocalizacaoService.processarFormacao(registro.value());
        ack.acknowledge();
    }

    @KafkaListener(topics = "${demo.topico-lote-movido-de-pasto}", groupId = "${demo.grupo-lote-localizacao}",
            containerFactory = "loteMovidoDePastoKafkaListenerContainerFactory")
    public void aoMoverLote(ConsumerRecord<String, LoteMovidoDePastoEvent> registro, Acknowledgment ack) {
        loteLocalizacaoService.processarMovimentacao(registro.value());
        ack.acknowledge();
    }
}
