package br.pucminas.aed.manejo.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import br.pucminas.aed.manejo.domain.Animal;
import br.pucminas.aed.manejo.domain.Fazenda;
import br.pucminas.aed.manejo.domain.Lote;

@Repository
public class AnimalRepository {

    private final JdbcTemplate jdbcTemplate;
    private final LoteRepository loteRepository;

    public AnimalRepository(JdbcTemplate jdbcTemplate, LoteRepository loteRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.loteRepository = loteRepository;
    }

    private static final RowMapper<Animal> ANIMAL_MAPPER = (rs, rowNum) -> {
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
                rs.getString("lote_id"),
                rs.getObject("lote_numeracao", Integer.class),
                fazenda
        );
        Timestamp lCreatedAt = rs.getTimestamp("lote_created_at");
        Timestamp lUpdatedAt = rs.getTimestamp("lote_updated_at");
        Timestamp lDeletedAt = rs.getTimestamp("lote_deleted_at");
        if (lCreatedAt != null) lote.setCreatedAt(lCreatedAt.toInstant());
        if (lUpdatedAt != null) lote.setUpdatedAt(lUpdatedAt.toInstant());
        if (lDeletedAt != null) lote.setDeletedAt(lDeletedAt.toInstant());

        Animal animal = new Animal(
                rs.getString("id"),
                rs.getString("nome_do_animal"),
                rs.getString("raca"),
                rs.getObject("idade", Integer.class),
                rs.getTimestamp("data_de_nascimento"),
                lote
        );
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        if (createdAt != null) animal.setCreatedAt(createdAt.toInstant());
        if (updatedAt != null) animal.setUpdatedAt(updatedAt.toInstant());
        if (deletedAt != null) animal.setDeletedAt(deletedAt.toInstant());
        return animal;
    };

    public void salvar(Animal animal) {
        jdbcTemplate.update(
                "INSERT INTO animal (id, nome_do_animal, raca, idade, data_de_nascimento, lote_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                animal.getId(),
                animal.getNomeDoAnimal(),
                animal.getRaca(),
                animal.getIdade(),
                animal.getDataDeNascimento(),
                animal.getLote().getId(),
                Timestamp.from(animal.getCreatedAt()),
                Timestamp.from(animal.getUpdatedAt()));
    }

    public Optional<Animal> buscarPorId(String id) {
        return jdbcTemplate.query(
                """
                SELECT a.*, l.id as lote_id, l.numeracao as lote_numeracao, l.created_at as lote_created_at,
                       l.updated_at as lote_updated_at, l.deleted_at as lote_deleted_at,
                       f.id as fazenda_id, f.nome as fazenda_nome, f.created_at as fazenda_created_at,
                       f.updated_at as fazenda_updated_at, f.deleted_at as fazenda_deleted_at
                FROM animal a
                JOIN lote l ON a.lote_id = l.id
                JOIN fazenda f ON l.fazenda_id = f.id
                WHERE a.id = ? AND a.deleted_at IS NULL AND l.deleted_at IS NULL AND f.deleted_at IS NULL
                """,
                ANIMAL_MAPPER,
                id).stream().findFirst();
    }

    public boolean existePorId(String id) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM animal WHERE id = ? AND deleted_at IS NULL",
                Long.class, id);
        return count != null && count > 0;
    }

    public void atualizar(Animal animal) {
        animal.setUpdatedAt(Instant.now());
        jdbcTemplate.update(
                "UPDATE animal SET nome_do_animal = ?, raca = ?, idade = ?, data_de_nascimento = ?, lote_id = ?, updated_at = ? WHERE id = ?",
                animal.getNomeDoAnimal(),
                animal.getRaca(),
                animal.getIdade(),
                animal.getDataDeNascimento(),
                animal.getLote().getId(),
                Timestamp.from(animal.getUpdatedAt()),
                animal.getId());
    }

    public void deletar(String id) {
        jdbcTemplate.update(
                "UPDATE animal SET deleted_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                id);
    }

    public List<Animal> buscarPorLote(String loteId) {
        return jdbcTemplate.query(
                """
                SELECT a.*, l.id as lote_id, l.numeracao as lote_numeracao, l.created_at as lote_created_at,
                       l.updated_at as lote_updated_at, l.deleted_at as lote_deleted_at,
                       f.id as fazenda_id, f.nome as fazenda_nome, f.created_at as fazenda_created_at,
                       f.updated_at as fazenda_updated_at, f.deleted_at as fazenda_deleted_at
                FROM animal a
                JOIN lote l ON a.lote_id = l.id
                JOIN fazenda f ON l.fazenda_id = f.id
                WHERE a.lote_id = ? AND a.deleted_at IS NULL AND l.deleted_at IS NULL AND f.deleted_at IS NULL
                """,
                ANIMAL_MAPPER,
                loteId);
    }
}
