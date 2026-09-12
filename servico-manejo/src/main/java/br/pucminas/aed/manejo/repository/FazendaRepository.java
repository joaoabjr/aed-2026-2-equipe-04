package br.pucminas.aed.manejo.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import br.pucminas.aed.manejo.domain.Fazenda;

@Repository
public class FazendaRepository {

    private final JdbcTemplate jdbcTemplate;

    public FazendaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Fazenda> FAZENDA_MAPPER = (rs, rowNum) -> {
        Fazenda fazenda = new Fazenda(
                rs.getString("id"),
                rs.getString("nome")
        );
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        if (createdAt != null) fazenda.setCreatedAt(createdAt.toInstant());
        if (updatedAt != null) fazenda.setUpdatedAt(updatedAt.toInstant());
        if (deletedAt != null) fazenda.setDeletedAt(deletedAt.toInstant());
        return fazenda;
    };

    public void salvar(Fazenda fazenda) {
        jdbcTemplate.update(
                "INSERT INTO fazenda (id, nome, created_at, updated_at) VALUES (?, ?, ?, ?)",
                fazenda.getId(),
                fazenda.getNome(),
                Timestamp.from(fazenda.getCreatedAt()),
                Timestamp.from(fazenda.getUpdatedAt()));
    }

    public Optional<Fazenda> buscarPorId(String id) {
        return jdbcTemplate.query(
                "SELECT * FROM fazenda WHERE id = ? AND deleted_at IS NULL",
                FAZENDA_MAPPER,
                id).stream().findFirst();
    }

    public void atualizar(Fazenda fazenda) {
        fazenda.setUpdatedAt(Instant.now());
        jdbcTemplate.update(
                "UPDATE fazenda SET nome = ?, updated_at = ? WHERE id = ?",
                fazenda.getNome(),
                Timestamp.from(fazenda.getUpdatedAt()),
                fazenda.getId());
    }

    public void deletar(String id) {
        jdbcTemplate.update(
                "UPDATE fazenda SET deleted_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                id);
    }
}