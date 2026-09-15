package br.pucminas.aed.manejo.domain;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Fato de rodízio de pastagem decidido pelo agregado Manejo de Pasto. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class LoteMovidoDePastoEvent {

    @NotBlank
    private final String eventoId;
    @NotNull
    private final Instant ocorridoEm;
    @NotBlank
    private final String loteId;
    @NotBlank
    private final String pastoOrigemId;
    @NotBlank
    private final String pastoDestinoId;

    @JsonCreator
    public LoteMovidoDePastoEvent(@JsonProperty("eventoId") String eventoId,
                                   @JsonProperty("ocorridoEm") Instant ocorridoEm,
                                   @JsonProperty("loteId") String loteId,
                                   @JsonProperty("pastoOrigemId") String pastoOrigemId,
                                   @JsonProperty("pastoDestinoId") String pastoDestinoId) {
        this.eventoId = Objects.requireNonNull(eventoId, "eventoId e obrigatorio");
        this.ocorridoEm = Objects.requireNonNull(ocorridoEm, "ocorridoEm e obrigatorio");
        this.loteId = Objects.requireNonNull(loteId, "loteId e obrigatorio");
        this.pastoOrigemId = Objects.requireNonNull(pastoOrigemId, "pastoOrigemId e obrigatorio");
        this.pastoDestinoId = Objects.requireNonNull(pastoDestinoId, "pastoDestinoId e obrigatorio");
    }

    public String getEventoId() { return eventoId; }
    public Instant getOcorridoEm() { return ocorridoEm; }
    public String getLoteId() { return loteId; }
    public String getPastoOrigemId() { return pastoOrigemId; }
    public String getPastoDestinoId() { return pastoDestinoId; }
}
