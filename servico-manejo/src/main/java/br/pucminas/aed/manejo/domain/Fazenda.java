package br.pucminas.aed.manejo.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import jakarta.validation.constraints.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class Fazenda {
    @NotNull(message = "ID não pode ser nulo")
    private String id;

    @NotNull(message = "Nome não pode ser nulo")
    @Size(min = 1, message = "Nome não pode ser vazio")
    private String nome;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    @JsonCreator
    public Fazenda(
            @JsonProperty("id") String id,
            @JsonProperty("nome") String nome) {
        this.id = id;
        this.nome = nome;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getNome() { return nome; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setNome(String nome) { this.nome = nome; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}