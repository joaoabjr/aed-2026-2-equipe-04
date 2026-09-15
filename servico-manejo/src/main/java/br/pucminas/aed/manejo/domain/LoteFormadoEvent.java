package br.pucminas.aed.manejo.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Fato que registra a composição de um lote para manejo conjunto. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class LoteFormadoEvent {

    @NotBlank
    private final String eventoId;
    @NotNull
    private final Instant ocorridoEm;
    @NotBlank
    private final String loteId;
    @NotNull
    private final List<String> animalIds;
    @NotBlank
    private final String criterioDeFormacao;

    @JsonCreator
    public LoteFormadoEvent(@JsonProperty("eventoId") String eventoId,
                             @JsonProperty("ocorridoEm") Instant ocorridoEm,
                             @JsonProperty("loteId") String loteId,
                             @JsonProperty("animalIds") List<String> animalIds,
                             @JsonProperty("criterioDeFormacao") String criterioDeFormacao) {
        this.eventoId = Objects.requireNonNull(eventoId, "eventoId e obrigatorio");
        this.ocorridoEm = Objects.requireNonNull(ocorridoEm, "ocorridoEm e obrigatorio");
        this.loteId = Objects.requireNonNull(loteId, "loteId e obrigatorio");
        this.animalIds = List.copyOf(Objects.requireNonNull(animalIds, "animalIds e obrigatorio"));
        this.criterioDeFormacao = Objects.requireNonNull(criterioDeFormacao, "criterioDeFormacao e obrigatorio");
    }

    public String getEventoId() { return eventoId; }
    public Instant getOcorridoEm() { return ocorridoEm; }
    public String getLoteId() { return loteId; }
    public List<String> getAnimalIds() { return animalIds; }
    public String getCriterioDeFormacao() { return criterioDeFormacao; }
}
