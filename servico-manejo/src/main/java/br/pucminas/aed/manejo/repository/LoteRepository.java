package br.pucminas.aed.manejo.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import br.pucminas.aed.manejo.domain.Fazenda;
import br.pucminas.aed.manejo.domain.Lote;

@Repository
public class LoteRepository {

    private final JdbcTemplate jdbcTemplate;
    private final FazendaRepository fazendaRepository;

    public LoteRepository(JdbcTemplate jdbcTemplate, FazendaRepository fazendaRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.fazendaRepository = fazendaRepository;
    }

    private static final RowMapper<Lote> LOTE_MAPPER = (rs, rowNum) -> {
        Fazenda fazenda = new Fazenda(
                rs.getString("fazenda_id"),
                rs.getString("fazenda_nome")
        );
        Timestamp fCreatedAt = rs.getTimestamp("fazenda_created_at");
        Timestamp fUpdatedAt = rs.getTimestamp("fazenda_updated_at");
        Timestamp fDeletedAt = rs.getTimestamp("fazenda_deleted_at");
        if (fCreatedAt != null) fazenda.setCreatedAt(fCreatedAt.toInstant());
        if (fUpdatedAt != null) fazenda.setUpdatedAt(fUpdatedAt.toInstant());
        if (fDeletedAt != null) fazenda.setDeletedAt(fDeletedAt.toInstant());

        Lote lote = new Lote(
                rs.getString("id"),
                rs.getObject("numeracao", Integer.class),
                fazenda
        );
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        if (createdAt != null) lote.setCreatedAt(createdAt.toInstant());
        if (updatedAt != null) lote.setUpdatedAt(updatedAt.toInstant());
        if (deletedAt != null) lote.setDeletedAt(deletedAt.toInstant());
        return lote;
    };

    public void salvar(Lote lote) {
        jdbcTemplate.update(
                "INSERT INTO lote (id, numeracao, fazenda_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                lote.getId(),
                lote.getNumeracao(),
                lote.getFazenda().getId(),
                Timestamp.from(lote.getCreatedAt()),
                Timestamp.from(lote.getUpdatedAt()));
    }

    public Optional<Lote> buscarPorId(String id) {
        return jdbcTemplate.query(
                """
                SELECT l.*, f.nome as fazenda_nome, f.created_at as fazenda_created_at,
                       f.updated_at as fazenda_updated_at, f.deleted_at as fazenda_deleted_at
                FROM lote l
                JOIN fazenda f ON l.fazenda_id = f.id
                WHERE l.id = ? AND l.deleted_at IS NULL AND f.deleted_at IS NULL
                """,
                LOTE_MAPPER,
                id).stream().findFirst();
    }

    public List<Lote> buscarPorFazenda(String fazendaId) {
        return jdbcTemplate.query(
                """
                SELECT l.*, f.nome as fazenda_nome, f.created_at as fazenda_created_at,
                       f.updated_at as fazenda_updated_at, f.deleted_at as fazenda_deleted_at
                FROM lote l
                JOIN fazenda f ON l.fazenda_id = f.id
                WHERE l.fazenda_id = ? AND l.deleted_at IS NULL AND f.deleted_at IS NULL
                """,
                LOTE_MAPPER,
                fazendaId);
    }

    public void atualizar(Lote lote) {
        lote.setUpdatedAt(Instant.now());
        jdbcTemplate.update(
                "UPDATE lote SET numeracao = ?, fazenda_id = ?, updated_at = ? WHERE id = ?",
                lote.getNumeracao(),
                lote.getFazenda().getId(),
                Timestamp.from(lote.getUpdatedAt()),
                lote.getId());
    }

    public void deletar(String id) {
        jdbcTemplate.update(
                "UPDATE lote SET deleted_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                id);
    }
}
