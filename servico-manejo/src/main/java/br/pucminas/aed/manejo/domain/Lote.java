package br.pucminas.aed.manejo.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import jakarta.validation.constraints.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class Lote {
    @NotNull(message = "ID não pode ser nulo")
    private String id;

    @NotNull(message = "Numeracao não pode ser nula")
    @Positive(message = "Numeracao deve ser maior que zero")
    private Integer numeracao;

    @NotNull(message = "Fazenda não pode ser nula")
    private Fazenda fazenda;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    @JsonCreator
    public Lote(
            @JsonProperty("id") String id,
            @JsonProperty("numeracao") Integer numeracao,
            @JsonProperty("fazenda") Fazenda fazenda) {
        this.id = id;
        this.numeracao = numeracao;
        this.fazenda = fazenda;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public Integer getNumeracao() { return numeracao; }
    public Fazenda getFazenda() { return fazenda; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setNumeracao(Integer numeracao) { this.numeracao = numeracao; }
    public void setFazenda(Fazenda fazenda) { this.fazenda = fazenda; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
