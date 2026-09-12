package br.pucminas.aed.manejo.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Date;
import jakarta.validation.constraints.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class Animal {
    @NotNull(message = "ID não pode ser nulo")
    private String id;

    @NotNull(message = "Nome do animal não pode ser nulo")
    @Size(min = 1, message = "Nome do animal não pode ser vazio")
    private String nomeDoAnimal;

    @NotNull(message = "Raça não pode ser nula")
    @Size(min = 1, message = "Raça não pode ser vazia")
    private String raca;

    @Min(value = 0, message = "Idade deve ser maior ou igual a 0")
    private Integer idade;

    @NotNull(message = "Data de nascimento não pode ser nula")
    private Date dataDeNascimento;

    @NotNull(message = "Lote não pode ser nulo")
    private Lote lote;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    @JsonCreator
    public Animal(
            @JsonProperty("id") String id,
            @JsonProperty("nomeDoAnimal") String nomeDoAnimal,
            @JsonProperty("raca") String raca,
            @JsonProperty("idade") Integer idade,
            @JsonProperty("dataDeNascimento") Date dataDeNascimento,
            @JsonProperty("lote") Lote lote) {
        this.id = id;
        this.nomeDoAnimal = nomeDoAnimal;
        this.raca = raca;
        this.idade = idade;
        this.dataDeNascimento = dataDeNascimento;
        this.lote = lote;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getNomeDoAnimal() { return nomeDoAnimal; }
    public String getRaca() { return raca; }
    public Integer getIdade() { return idade; }
    public Date getDataDeNascimento() { return dataDeNascimento; }
    public Lote getLote() { return lote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setLote(Lote lote) { this.lote = lote; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

}