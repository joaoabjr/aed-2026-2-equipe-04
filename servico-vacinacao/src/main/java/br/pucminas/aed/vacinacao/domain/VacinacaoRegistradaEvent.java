package br.pucminas.aed.vacinacao.domain;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class VacinacaoRegistradaEvent {

    private final String eventoId;
    private final Instant ocorridoEm;
    private final String animalId;
    private final double pesoKg;
    private final String metodoDeVacinacao;
    private final String vacina;
    private final Date validade;

    @JsonCreator
    public VacinacaoRegistradaEvent(@JsonProperty("eventoId") String eventoId,
                                    @JsonProperty("ocorridoEm") Instant ocorridoEm,
                                    @JsonProperty("animalId") String animalId,
                                    @JsonProperty("pesoKg") double pesoKg,
                                    @JsonProperty("metodoDeVacinacao") String metodoDeVacinacao,
                                    @JsonProperty("vacina") String vacina,
                                    @JsonProperty("validade") Date validade) {

        this.eventoId = Objects.requireNonNull(eventoId, "eventoId e obrigatorio");
        this.ocorridoEm = Objects.requireNonNull(ocorridoEm, "ocorridoEm e obrigatorio");
        this.animalId = Objects.requireNonNull(animalId, "animalId e obrigatorio");
        this.pesoKg = pesoKg;
        this.metodoDeVacinacao = metodoDeVacinacao;
        this.vacina = Objects.requireNonNull(vacina, "Nome da vacina é obrigatorio");
        this.validade = validade;
    }

    public String getEventoId() {
        return eventoId;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }

    public String getAnimalId() {
        return animalId;
    }

    public double getPesoKg() {
        return pesoKg;
    }

    public String getMetodoDeVacinacao() {
        return metodoDeVacinacao;
    }

    public String getVacina() {
        return vacina;
    }

    public Date getValidade() {
        return validade;
    }

    @Override
    public String toString() {
        return "VacinacaoRegistradaEvent{eventoId=" + eventoId
                + ", ocorridoEm=" + ocorridoEm
                + ", animalId=" + animalId
                + ", pesoKg=" + pesoKg
                + ", metodoDeVacinacao=" + metodoDeVacinacao
                + ", vacina=" + vacina
                + ", validade=" + validade + "}";
    }
}
