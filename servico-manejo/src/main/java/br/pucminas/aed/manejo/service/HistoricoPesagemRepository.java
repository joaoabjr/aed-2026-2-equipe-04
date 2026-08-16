package br.pucminas.aed.manejo.service;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * A porta de saida para as DUAS tabelas do schema.sql.
 *
 * evento_processado e a memoria da idempotencia: quem ja viu o evento (por
 * eventoId) nao e processado de novo. historico_pesagem e o efeito de
 * negocio, append-only — cada leitura de peso fica guardada, em ordem, para
 * sustentar a curva de ganho de peso.
 *
 * registrarEventoSeNovo e a chave da deduplicacao: tenta INSERT, e a
 * PRIMARY KEY de evento_processado (evento_id) e quem barra a duplicata via
 * DuplicateKeyException — sem SELECT primeiro (a corrida entre dois
 * consumidores da mesma particao ficaria visivel, e o ADR-002 pede dedup
 * por eventoId, nunca por animalId).
 */
@Repository
public class HistoricoPesagemRepository {

    private final JdbcTemplate jdbcTemplate;

    public HistoricoPesagemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @return true se o evento e novo (INSERT vingou); false se ja era conhecido.
     */
    public boolean registrarEventoSeNovo(String eventoId) {
        try {
            jdbcTemplate.update("INSERT INTO evento_processado (evento_id) VALUES (?)", eventoId);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public void registrarPesagem(String animalId, double pesoKg, Instant registradoEm) {
        jdbcTemplate.update(
                "INSERT INTO historico_pesagem (animal_id, peso_kg, registrado_em) VALUES (?, ?, ?)",
                animalId, pesoKg, registradoEm);
    }

    public long contarRegistrosDoAnimal(String animalId) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM historico_pesagem WHERE animal_id = ?",
                Long.class, animalId);
        return total == null ? 0L : total;
    }

    public List<Double> historicoDePeso(String animalId) {
        return jdbcTemplate.queryForList(
                "SELECT peso_kg FROM historico_pesagem WHERE animal_id = ? ORDER BY registrado_em, id",
                Double.class, animalId);
    }

    public void limparTudo() {
        jdbcTemplate.update("DELETE FROM historico_pesagem");
        jdbcTemplate.update("DELETE FROM evento_processado");
    }
}