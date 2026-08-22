package br.pucminas.aed.manejo.service;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HistoricoVacinacaoRepository {

    private final JdbcTemplate jdbcTemplate;

    public HistoricoVacinacaoRepository(JdbcTemplate jdbcTemplate) {
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

    public void registrarVacinacao(String animalId, String vacina, String metodoDeVacinacao, Instant registradoEm) {
        jdbcTemplate.update(
                "INSERT INTO historico_vacinacao (animal_id, vacina, metodo_de_vacinacao, registrado_em) VALUES (?, ?, ?, ?)",
                animalId, vacina, metodoDeVacinacao, registradoEm);
    }

    public long contarRegistrosDoAnimal(String animalId) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM historico_vacinacao WHERE animal_id = ?",
                Long.class, animalId);
        return total == null ? 0L : total;
    }

    public List<String> historicoDeVacinacao(String animalId) {
        return jdbcTemplate.queryForList(
                "SELECT vacina FROM historico_vacinacao WHERE animal_id = ? ORDER BY registrado_em, id",
                String.class, animalId);
    }

    public void limparTudo() {
        jdbcTemplate.update("DELETE FROM historico_vacinacao");
        jdbcTemplate.update("DELETE FROM evento_processado");
    }
}