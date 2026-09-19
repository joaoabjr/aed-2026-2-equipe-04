package br.pucminas.aed.manejo.domain;

import java.time.Instant;

/**
 * Uma linha da DLQ (Dead Letter Queue) do servico-manejo, lida de volta
 * para auditoria. Nao e' um evento de dominio publicado no Kafka -- e' a
 * representacao de UMA linha ja gravada em evento_dlq: o evento de pesagem
 * ou embarque que o consumidor nao conseguiu processar e foi registrado de
 * forma permanente, em vez de travar a particao.
 */
public final class EventoDlqVO {

    private final long id;
    private final String origemTopico;
    private final int particao;
    private final long deslocamento;
    private final String chave;
    private final String eventoId;
    private final String tipoEvento;
    private final String payload;
    private final String motivo;
    private final String detalhe;
    private final Instant registradoEm;

    public EventoDlqVO(long id, String origemTopico, int particao, long deslocamento,
                       String chave, String eventoId, String tipoEvento, String payload,
                       String motivo, String detalhe, Instant registradoEm) {
        this.id = id;
        this.origemTopico = origemTopico;
        this.particao = particao;
        this.deslocamento = deslocamento;
        this.chave = chave;
        this.eventoId = eventoId;
        this.tipoEvento = tipoEvento;
        this.payload = payload;
        this.motivo = motivo;
        this.detalhe = detalhe;
        this.registradoEm = registradoEm;
    }

    public long getId() {
        return id;
    }

    public String getOrigemTopico() {
        return origemTopico;
    }

    public int getParticao() {
        return particao;
    }

    public long getDeslocamento() {
        return deslocamento;
    }

    public String getChave() {
        return chave;
    }

    public String getEventoId() {
        return eventoId;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public String getPayload() {
        return payload;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getDetalhe() {
        return detalhe;
    }

    public Instant getRegistradoEm() {
        return registradoEm;
    }
}