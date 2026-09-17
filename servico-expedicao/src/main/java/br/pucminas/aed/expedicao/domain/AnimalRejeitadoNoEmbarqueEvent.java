package br.pucminas.aed.expedicao.domain;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * O caminho de exceção do domínio (ADR-002): o frigorífico recusou o animal
 * na triagem de recebimento. A Expedição registra a recusa de forma
 * definitiva — este evento não é apagado nem reenviado — e é a partir dele
 * que o servico-manejo dispara a compensação (retorno ao lote de origem,
 * reavaliação de dieta, encerramento da venda que não se concretizou).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AnimalRejeitadoNoEmbarqueEvent {

    private final String eventoId;
    private final Instant ocorridoEm;
    private final String animalId;
    private final String frigorificoDestino;
    private final String motivoRejeicao;

    @JsonCreator
    public AnimalRejeitadoNoEmbarqueEvent(
            @JsonProperty("eventoId") String eventoId,
            @JsonProperty("ocorridoEm") Instant ocorridoEm,
            @JsonProperty("animalId") String animalId,
            @JsonProperty("frigorificoDestino") String frigorificoDestino,
            @JsonProperty("motivoRejeicao") String motivoRejeicao) {
        this.eventoId = Objects.requireNonNull(eventoId, "eventoId e obrigatorio");
        this.ocorridoEm = Objects.requireNonNull(ocorridoEm, "ocorridoEm e obrigatorio");
        this.animalId = Objects.requireNonNull(animalId, "animalId e obrigatorio");
        this.frigorificoDestino = Objects.requireNonNull(frigorificoDestino, "frigorificoDestino e obrigatorio");
        this.motivoRejeicao = Objects.requireNonNull(motivoRejeicao, "motivoRejeicao e obrigatorio");
    }

    public String getEventoId() { return eventoId; }
    public Instant getOcorridoEm() { return ocorridoEm; }
    public String getAnimalId() { return animalId; }
    public String getFrigorificoDestino() { return frigorificoDestino; }
    public String getMotivoRejeicao() { return motivoRejeicao; }
}
