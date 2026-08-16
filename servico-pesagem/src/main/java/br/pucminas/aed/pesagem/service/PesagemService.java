package br.pucminas.aed.manejo.domain;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A visao QUE ESTE SERVICO TEM do evento PesagemRegistrada.
 *
 * O publisher publica eventoId, ocorridoEm, animalId, pesoKg E
 * metodoDePesagem. Esta classe NAO declara metodoDePesagem: o servico de
 * manejo decide dieta e formacao de lote a partir do peso, nao de qual
 * balanca foi usada — essa informacao interessa a auditoria/qualidade, nao a
 * este consumidor. Campo desconhecido no JSON e ignorado
 * (@JsonIgnoreProperties), o que permite ao publisher acrescentar campos
 * novos sem quebrar este consumidor.
 *
 * Declare so o que voce usa. Cada campo declarado e uma dependencia sobre o
 * formato alheio.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PesagemRegistradaEvent {

    private final String eventoId;
    private final Instant ocorridoEm;
    private final String animalId;
    private final double pesoKg;

    @JsonCreator
    public PesagemRegistradaEvent(@JsonProperty("eventoId") String eventoId,
                                  @JsonProperty("ocorridoEm") Instant ocorridoEm,
                                  @JsonProperty("animalId") String animalId,
                                  @JsonProperty("pesoKg") double pesoKg) {

        this.eventoId = Objects.requireNonNull(eventoId, "eventoId e obrigatorio");
        this.ocorridoEm = Objects.requireNonNull(ocorridoEm, "ocorridoEm e obrigatorio");
        this.animalId = Objects.requireNonNull(animalId, "animalId e obrigatorio");
        this.pesoKg = pesoKg;
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

    @Override
    public String toString() {
        return "ManejoRegistradoEvent{eventoId=" + eventoId
                + ", animalId=" + animalId
                + ", manejo=" + pesoKg + "}";
    }
}
