package br.pucminas.aed.manejo.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import jakarta.validation.constraints.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class Venda {
    @NotNull(message = "ID não pode ser nulo")
    private String id;

    // animalId XOR loteId — exatamente um deve estar preenchido (validado no service).
    private String animalId;
    private String loteId;

    @NotNull(message = "Frigorifico não pode ser nulo")
    @Size(min = 1, message = "Frigorifico não pode ser vazio")
    private String frigorifico;

    @NotNull(message = "Peso minimo não pode ser nulo")
    @Positive(message = "Peso minimo deve ser maior que zero")
    private Double pesoMinimoKg;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    @JsonCreator
    public Venda(
            @JsonProperty("id") String id,
            @JsonProperty("animalId") String animalId,
            @JsonProperty("loteId") String loteId,
            @JsonProperty("frigorifico") String frigorifico,
            @JsonProperty("pesoMinimoKg") Double pesoMinimoKg) {
        this.id = id;
        this.animalId = animalId;
        this.loteId = loteId;
        this.frigorifico = frigorifico;
        this.pesoMinimoKg = pesoMinimoKg;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getAnimalId() { return animalId; }
    public String getLoteId() { return loteId; }
    public String getFrigorifico() { return frigorifico; }
    public Double getPesoMinimoKg() { return pesoMinimoKg; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setAnimalId(String animalId) { this.animalId = animalId; }
    public void setLoteId(String loteId) { this.loteId = loteId; }
    public void setFrigorifico(String frigorifico) { this.frigorifico = frigorifico; }
    public void setPesoMinimoKg(Double pesoMinimoKg) { this.pesoMinimoKg = pesoMinimoKg; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
