package br.pucminas.aed.manejo.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import br.pucminas.aed.manejo.domain.Venda;

@Repository
public class VendaRepository {

    private final JdbcTemplate jdbcTemplate;

    public VendaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Venda> VENDA_MAPPER = (rs, rowNum) -> {
        Venda venda = new Venda(
                rs.getString("id"),
                rs.getString("animal_id"),
                rs.getString("lote_id"),
                rs.getString("frigorifico"),
                rs.getObject("peso_minimo_kg", Double.class)
        );
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        if (createdAt != null) venda.setCreatedAt(createdAt.toInstant());
        if (updatedAt != null) venda.setUpdatedAt(updatedAt.toInstant());
        if (deletedAt != null) venda.setDeletedAt(deletedAt.toInstant());
        return venda;
    };

    public void salvar(Venda venda) {
        jdbcTemplate.update(
                "INSERT INTO venda (id, animal_id, lote_id, frigorifico, peso_minimo_kg, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                venda.getId(),
                venda.getAnimalId(),
                venda.getLoteId(),
                venda.getFrigorifico(),
                venda.getPesoMinimoKg(),
                Timestamp.from(venda.getCreatedAt()),
                Timestamp.from(venda.getUpdatedAt()));
    }

    public Optional<Venda> buscarPorId(String id) {
        return jdbcTemplate.query(
                "SELECT * FROM venda WHERE id = ? AND deleted_at IS NULL",
                VENDA_MAPPER,
                id).stream().findFirst();
    }

    public List<Venda> buscarPorAnimal(String animalId) {
        return jdbcTemplate.query(
                "SELECT * FROM venda WHERE animal_id = ? AND deleted_at IS NULL ORDER BY created_at",
                VENDA_MAPPER,
                animalId);
    }

    public List<Venda> buscarPorLote(String loteId) {
        return jdbcTemplate.query(
                "SELECT * FROM venda WHERE lote_id = ? AND deleted_at IS NULL ORDER BY created_at",
                VENDA_MAPPER,
                loteId);
    }

    public void atualizar(Venda venda) {
        venda.setUpdatedAt(Instant.now());
        jdbcTemplate.update(
                "UPDATE venda SET animal_id = ?, lote_id = ?, frigorifico = ?, peso_minimo_kg = ?, updated_at = ? WHERE id = ?",
                venda.getAnimalId(),
                venda.getLoteId(),
                venda.getFrigorifico(),
                venda.getPesoMinimoKg(),
                Timestamp.from(venda.getUpdatedAt()),
                venda.getId());
    }

    public void deletar(String id) {
        jdbcTemplate.update(
                "UPDATE venda SET deleted_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                id);
    }
}
