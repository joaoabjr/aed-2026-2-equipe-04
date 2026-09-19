package br.pucminas.aed.manejo.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.pucminas.aed.manejo.domain.EventoDlqVO;

/**
 * O registro PERMANENTE do que o consumidor nao conseguiu processar.
 *
 * Quando um evento de pesagem ou embarque falha (poison message que nao
 * desserializa, ou regra de negocio que lanca excecao), o consumidor nao
 * pode travar a particao reprocessando para sempre nem descartar em
 * silencio — este servico grava a falha na DLQ de forma permanente (tabela
 * evento_dlq, append-only) e so depois disso o offset do topico original e'
 * confirmado. O acumulo fica para auditoria e reprocessamento manual.
 *
 * A deduplicacao e' por (origem_topico, particao, deslocamento): a posicao
 * Kafka da mensagem. Se o processo morrer entre a gravacao aqui e o commit
 * do offset, a mesma mensagem chega de novo e o INSERT repete a posicao —
 * a UNIQUE da tabela aceita a primeira gravacao e descarta a segunda, sem
 * poluir o historico.
 */
@Service
public class DlqService {

    private static final Logger log = LoggerFactory.getLogger(DlqService.class);

    private static final int LIMITE_PAYLOAD = 4000;
    private static final int LIMITE_DETALHE = 4000;

    private final DlqRepository repositorio;

    public DlqService(DlqRepository repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * Grava um evento que falhou na DLQ permanente.
     *
     * @return true se gravado; false se aquela posicao Kafka ja estava na DLQ.
     */
    public boolean registrar(String origemTopico, int particao, long deslocamento,
                             String chave, String eventoId, String tipoEvento,
                             String payload, String motivo, String detalhe) {

        boolean gravado = repositorio.registrar(
                origemTopico, particao, deslocamento, chave, eventoId, tipoEvento,
                limitar(payload, LIMITE_PAYLOAD), motivo, limitar(detalhe, LIMITE_DETALHE));

        if (gravado) {
            log.warn("evento movido para a DLQ permanente  topico={}  particao={}  offset={}  " +
                            "chave={}  eventoId={}  tipoEvento={}  motivo={}",
                    origemTopico, particao, deslocamento, chave, eventoId, tipoEvento, motivo);
        } else {
            log.info("evento {} JA NA DLQ, descartando duplicata da posicao {}/{}",
                    eventoId, origemTopico, deslocamento);
        }
        return gravado;
    }

    public List<EventoDlqVO> listar() {
        return repositorio.listar();
    }

    public long contar() {
        return repositorio.contar();
    }

    private String limitar(String valor, int limite) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= limite ? valor : valor.substring(0, limite);
    }
}