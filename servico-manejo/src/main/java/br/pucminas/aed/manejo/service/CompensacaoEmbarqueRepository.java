package br.pucminas.aed.manejo.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * So a memoria de dedup da compensacao de AnimalRejeitadoNoEmbarque
 * (ADR-002) — o efeito de negocio em si mora em AnimalRepository
 * (dieta_atual) e VendaRepository (encerramento da venda), tabelas de
 * cadastro que ja tinham repositorio proprio. Nao ha historico
 * append-only novo aqui, so' o registro de "ja vi esse eventoId", mesma
 * chave evento_id de evento_processado usada por
 * HistoricoPesagemRepository e HistoricoVacinacaoRepository.
 */
@Repository
public class CompensacaoEmbarqueRepository {

    private final JdbcTemplate jdbcTemplate;

    public CompensacaoEmbarqueRepository(JdbcTemplate jdbcTemplate) {
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
}
