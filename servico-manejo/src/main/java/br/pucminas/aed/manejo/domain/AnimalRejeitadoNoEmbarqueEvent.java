package br.pucminas.aed.manejo.domain;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A visao QUE ESTE SERVICO TEM do evento AnimalRejeitadoNoEmbarque.
 *
 * O publisher (servico-expedicao) publica eventoId, ocorridoEm, animalId,
 * frigorificoDestino E motivoRejeicao. Esta classe NAO declara
 * frigorificoDestino: a compensacao (ADR-002) — retorno ao lote de
 * origem e reavaliacao de dieta — depende de QUAL animal e POR QUE foi
 * recusado, nao de qual frigorifico fez a triagem; esse dado interessa a
 * auditoria da Expedicao, nao a este consumidor. Campo desconhecido no
 * JSON e ignorado (@JsonIgnoreProperties), o mesmo padrao tolerante de
 * PesagemRegistradaEvent.
 *
 * motivoRejeicao e String, nao enum: os valores hoje previstos pelo ADR-002
 * sao "PESO_INSUFICIENTE" e "VACINACAO_VENCIDA", mas um enum aqui
 * quebraria o consumidor no dia em que a Expedicao publicar um motivo
 * novo — o mesmo raciocinio de tolerancia do resto do contrato.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AnimalRejeitadoNoEmbarqueEvent {

    private final String eventoId;
    private final Instant ocorridoEm;
    private final String animalId;
    private final String motivoRejeicao;

    @JsonCreator
    public AnimalRejeitadoNoEmbarqueEvent(@JsonProperty("eventoId") String eventoId,
                                          @JsonProperty("ocorridoEm") Instant ocorridoEm,
                                          @JsonProperty("animalId") String animalId,
                                          @JsonProperty("motivoRejeicao") String motivoRejeicao) {
        this.eventoId = Objects.requireNonNull(eventoId, "eventoId e obrigatorio");
        this.ocorridoEm = Objects.requireNonNull(ocorridoEm, "ocorridoEm e obrigatorio");
        this.animalId = Objects.requireNonNull(animalId, "animalId e obrigatorio");
        this.motivoRejeicao = Objects.requireNonNull(motivoRejeicao, "motivoRejeicao e obrigatorio");
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

    public String getMotivoRejeicao() {
        return motivoRejeicao;
    }

    @Override
    public String toString() {
        return "AnimalRejeitadoNoEmbarqueEvent{eventoId=" + eventoId
                + ", animalId=" + animalId
                + ", motivoRejeicao=" + motivoRejeicao + "}";
    }
}
