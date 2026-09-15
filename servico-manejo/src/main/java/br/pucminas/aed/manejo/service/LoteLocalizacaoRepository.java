package br.pucminas.aed.manejo.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * A projecao descartavel do agregado Lote (localizacao) — ADR-005: onde
 * cada lote esta agora. Nunca e' escrita por decisao de negocio direta —
 * so pelo fold dos eventos em LoteLocalizacaoService, seja incrementalmente
 * (evento novo chegando pelo Kafka) ou por replay completo do event store.
 *
 * UPDATE-e-se-nao-existir-INSERT em vez de um MERGE/UPSERT de banco: mais
 * portatil entre Postgres (runtime) e H2 (testes), sem depender de sintaxe
 * especifica de dialeto.
 */
@Repository
public class LoteLocalizacaoRepository {

    private final JdbcTemplate jdbcTemplate;

    public LoteLocalizacaoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void registrarFormacao(String loteId, long versao, Instant atualizadoEm) {
        upsert(loteId, null, versao, atualizadoEm);
    }

    public void registrarMovimentacao(String loteId, String pastoDestinoId, long versao, Instant atualizadoEm) {
        upsert(loteId, pastoDestinoId, versao, atualizadoEm);
    }

    public Optional<String> buscarPastoAtual(String loteId) {
        List<String> resultado = jdbcTemplate.queryForList(
                "SELECT pasto_atual_id FROM lote_localizacao_atual WHERE lote_id = ?", String.class, loteId);
        if (resultado.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(resultado.get(0));
    }

    public boolean existe(String loteId) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lote_localizacao_atual WHERE lote_id = ?", Integer.class, loteId);
        return total != null && total > 0;
    }

    public void remover(String loteId) {
        jdbcTemplate.update("DELETE FROM lote_localizacao_atual WHERE lote_id = ?", loteId);
    }

    public void limparTudo() {
        jdbcTemplate.update("DELETE FROM lote_localizacao_atual");
    }

    private void upsert(String loteId, String pastoAtualId, long versao, Instant atualizadoEm) {
        int linhasAtualizadas = jdbcTemplate.update(
                "UPDATE lote_localizacao_atual SET pasto_atual_id = ?, versao_da_projecao = ?, atualizado_em = ? " +
                        "WHERE lote_id = ?",
                pastoAtualId, versao, Timestamp.from(atualizadoEm), loteId);
        if (linhasAtualizadas == 0) {
            jdbcTemplate.update(
                    "INSERT INTO lote_localizacao_atual (lote_id, pasto_atual_id, versao_da_projecao, atualizado_em) " +
                            "VALUES (?, ?, ?, ?)",
                    loteId, pastoAtualId, versao, Timestamp.from(atualizadoEm));
        }
    }
}
