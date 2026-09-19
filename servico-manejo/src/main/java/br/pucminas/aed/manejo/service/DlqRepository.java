package br.pucminas.aed.manejo.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import br.pucminas.aed.manejo.domain.EventoDlqVO;

/**
 * A DLQ (Dead Letter Queue) do servico-manejo — a porta de saida de
 * evento_dlq: registros PERMANENTES do que o consumidor nao conseguiu
 * processar nos fluxos de pesagem e embarque. Append-only: nada e' alterado
 * nem apagado pelo fluxo normal; a auditoria le e reprocessa manualmente.
 *
 * registrar e' a chave da deduplicacao: tenta INSERT, e a UNIQUE
 * (origem_topico, particao, deslocamento) barra a duplicata via
 * DuplicateKeyException — a posicao Kafka da mensagem, nao o ce_id, porque
 * uma poison message pode nao ter cabecalho ce_id aproveitavel, mas sempre
 * tem topico/particao/offset.
 */
@Repository
public class DlqRepository {

    private static final String COLUNAS = "id, origem_topico, particao, deslocamento, chave, " +
            "evento_id, tipo_evento, payload, motivo, detalhe, registrado_em";

    private final JdbcTemplate jdbcTemplate;

    public DlqRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @return true se o registro e novo (INSERT vingou); false se aquela
     *         posicao Kafka (topico/particao/deslocamento) ja estava na DLQ.
     */
    public boolean registrar(String origemTopico, int particao, long deslocamento,
                             String chave, String eventoId, String tipoEvento,
                             String payload, String motivo, String detalhe) {
        try {
            jdbcTemplate.update("INSERT INTO evento_dlq " +
                            "(origem_topico, particao, deslocamento, chave, evento_id, " +
                            "tipo_evento, payload, motivo, detalhe) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    origemTopico, particao, deslocamento, chave, eventoId,
                    tipoEvento, payload, motivo, detalhe);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public List<EventoDlqVO> listar() {
        return jdbcTemplate.query("SELECT " + COLUNAS + " FROM evento_dlq ORDER BY id DESC",
                (rs, rowNum) -> mapear(rs));
    }

    public long contar() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM evento_dlq", Long.class);
        return total == null ? 0L : total;
    }

    public void limparTudo() {
        jdbcTemplate.update("DELETE FROM evento_dlq");
    }

    private EventoDlqVO mapear(ResultSet rs) throws SQLException {
        Timestamp registrado = rs.getTimestamp("registrado_em");
        return new EventoDlqVO(
                rs.getLong("id"),
                rs.getString("origem_topico"),
                rs.getInt("particao"),
                rs.getLong("deslocamento"),
                rs.getString("chave"),
                rs.getString("evento_id"),
                rs.getString("tipo_evento"),
                rs.getString("payload"),
                rs.getString("motivo"),
                rs.getString("detalhe"),
                registrado == null ? null : registrado.toInstant());
    }
}