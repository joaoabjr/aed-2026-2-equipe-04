package br.pucminas.aed.pesagem.domain;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * O fato: uma pesagem de um animal foi registrada na balanca eletronica do
 * curral.
 *
 * Nome no particípio, describing um fato ocorrido — nunca um comando
 * (RegistrarPesagem seria comando; PesagemRegistrada e o fato).
 *
 * IMUTAVEL EXPLICITA, DE PROPOSITO: campos private final, sem setter, sem
 * record. O objetivo do exercicio e que os mecanismos fiquem a vista — e foi
 * exatamente o primeiro ponto em que recusamos uma sugestao da IA (ver
 * docs/IA.md, Aula 02): ela sugeriu um record por ser mais enxuto, mas o
 * enunciado pede a classe explicita.
 *
 * IDENTIDADE PROPRIA: eventoId e distinto de animalId (a entidade de
 * negocio). E esse eventoId — nao o animalId — que vira a chave de
 * deduplicacao do lado do consumidor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PesagemRegistradaEvent {

    private final String eventoId;
    private final Instant ocorridoEm;
    private final String animalId;
    private final double pesoKg;
    private final String metodoDePesagem;

    @JsonCreator
    public PesagemRegistradaEvent(@JsonProperty("eventoId") String eventoId,
                                  @JsonProperty("ocorridoEm") Instant ocorridoEm,
                                  @JsonProperty("animalId") String animalId,
                                  @JsonProperty("pesoKg") double pesoKg,
                                  @JsonProperty("metodoDePesagem") String metodoDePesagem) {

        this.eventoId = Objects.requireNonNull(eventoId, "eventoId e obrigatorio");
        this.ocorridoEm = Objects.requireNonNull(ocorridoEm, "ocorridoEm e obrigatorio");
        this.animalId = Objects.requireNonNull(animalId, "animalId e obrigatorio");
        this.pesoKg = pesoKg;
        this.metodoDePesagem = metodoDePesagem;
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

    public String getMetodoDePesagem() {
        return metodoDePesagem;
    }

    @Override
    public String toString() {
        return "PesagemRegistradaEvent{eventoId=" + eventoId
                + ", animalId=" + animalId
                + ", pesoKg=" + pesoKg + "}";
    }
}
