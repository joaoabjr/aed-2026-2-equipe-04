package br.pucminas.aed.manejo.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.pucminas.aed.manejo.domain.EventoArmazenadoVO;

/**
 * O event store do agregado Lote (localizacao) — ADR-005.
 *
 * Append-only: nunca ha UPDATE nem DELETE numa linha ja gravada. A chave
 * (lote_id, versao) e' o mecanismo de deteccao de concorrencia — duas
 * tentativas de gravar a mesma versao do mesmo lote colidem na restricao de
 * unicidade da tabela, e so uma vence. A restricao UNIQUE em evento_id e'
 * o mecanismo de idempotencia — reentrega do mesmo evento nao gera uma
 * versao nova.
 */
@Repository
public class LoteEventoStoreRepository {

    private final JdbcTemplate jdbcTemplate;

    public LoteEventoStoreRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean jaProcessado(String eventoId) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lote_evento_store WHERE evento_id = ?", Integer.class, eventoId);
        return total != null && total > 0;
    }

    public long proximaVersao(String loteId) {
        Long maxima = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(versao), 0) FROM lote_evento_store WHERE lote_id = ?", Long.class, loteId);
        return (maxima == null ? 0L : maxima) + 1;
    }

    /**
     * @return true se o evento foi anexado; false se a versao ja estava
     *         ocupada — conflito de concorrencia detectado pela propria
     *         restricao de unicidade de (lote_id, versao).
     */
    public boolean anexar(String loteId, long versao, String eventoId, String tipoEvento,
                           Instant ocorridoEm, String payload) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO lote_evento_store (lote_id, versao, evento_id, tipo_evento, ocorrido_em, payload) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                    loteId, versao, eventoId, tipoEvento, Timestamp.from(ocorridoEm), payload);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public List<EventoArmazenadoVO> lerStream(String loteId) {
        return jdbcTemplate.query(
                "SELECT versao, tipo_evento, ocorrido_em, payload FROM lote_evento_store " +
                        "WHERE lote_id = ? ORDER BY versao",
                (rs, linha) -> new EventoArmazenadoVO(
                        rs.getLong("versao"),
                        rs.getString("tipo_evento"),
                        rs.getTimestamp("ocorrido_em").toInstant(),
                        rs.getString("payload")),
                loteId);
    }

    public void limparTudo() {
        jdbcTemplate.update("DELETE FROM lote_evento_store");
    }
}
