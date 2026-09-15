package br.pucminas.aed.expedicao.domain;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Fato final do ciclo de engorda: a propriedade transferiu o animal ao frigorífico. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AnimalEmbarcadoParaAbateEvent {

    private final String eventoId;
    private final Instant ocorridoEm;
    private final String animalId;
    private final String frigorificoDestino;
    private final double pesoDeEmbarqueKg;

    @JsonCreator
    public AnimalEmbarcadoParaAbateEvent(
            @JsonProperty("eventoId") String eventoId,
            @JsonProperty("ocorridoEm") Instant ocorridoEm,
            @JsonProperty("animalId") String animalId,
            @JsonProperty("frigorificoDestino") String frigorificoDestino,
            @JsonProperty("pesoDeEmbarqueKg") double pesoDeEmbarqueKg) {
        this.eventoId = Objects.requireNonNull(eventoId, "eventoId e obrigatorio");
        this.ocorridoEm = Objects.requireNonNull(ocorridoEm, "ocorridoEm e obrigatorio");
        this.animalId = Objects.requireNonNull(animalId, "animalId e obrigatorio");
        this.frigorificoDestino = Objects.requireNonNull(frigorificoDestino, "frigorificoDestino e obrigatorio");
        this.pesoDeEmbarqueKg = pesoDeEmbarqueKg;
    }

    public String getEventoId() { return eventoId; }
    public Instant getOcorridoEm() { return ocorridoEm; }
    public String getAnimalId() { return animalId; }
    public String getFrigorificoDestino() { return frigorificoDestino; }
    public double getPesoDeEmbarqueKg() { return pesoDeEmbarqueKg; }
}
