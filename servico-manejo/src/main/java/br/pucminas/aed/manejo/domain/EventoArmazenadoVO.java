package br.pucminas.aed.manejo.domain;

import java.time.Instant;

/**
 * Uma linha do event store do agregado Lote (ADR-005), lida de volta para
 * replay. Nao e' um evento de dominio publicado no Kafka — e' a
 * representacao de UMA linha ja gravada no lote_evento_store.
 */
public final class EventoArmazenadoVO {

    private final long versao;
    private final String tipoEvento;
    private final Instant ocorridoEm;
    private final String payload;

    public EventoArmazenadoVO(long versao, String tipoEvento, Instant ocorridoEm, String payload) {
        this.versao = versao;
        this.tipoEvento = tipoEvento;
        this.ocorridoEm = ocorridoEm;
        this.payload = payload;
    }

    public long getVersao() {
        return versao;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }

    public String getPayload() {
        return payload;
    }
}
