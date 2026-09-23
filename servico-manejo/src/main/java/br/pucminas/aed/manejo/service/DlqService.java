package br.pucminas.aed.manejo.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.pucminas.aed.manejo.domain.EventoDlqVO;

/**
 * O registro PERMANENTE do que o consumidor nao conseguiu processar.
 *
 * Quando um evento de pesagem ou embarque falha (poison message que nao
 * desserializa, ou regra de negocio que lanca excecao), o consumidor nao
 * pode travar a particao reprocessando para sempre nem descartar em
 * silencio -- este servico grava a falha na DLQ de forma permanente (tabela
 * evento_dlq, append-only) e so depois disso o offset do topico original e'
 * confirmado. O acumulo fica para auditoria e reprocessamento manual.
 *
 * A deduplicacao e' por (origem_topico, particao, deslocamento): a posicao
 * Kafka da mensagem. Se o processo morrer entre a gravacao aqui e o commit
 * do offset, a mesma mensagem chega de novo e o INSERT repete a posicao --
 * a UNIQUE da tabela aceita a primeira gravacao e descarta a segunda, sem
 * poluir o historico.
 *
 * O REPROCESSAMENTO (reprocessar) e' o caminho manual que a Parte A do
 * projeto final exige: reenvia o payload gravado de volta ao TOPICO
 * ORIGINAL, com os cabecalhos originais restaurados -- o mesmo consumidor
 * que falhou da primeira vez recebe o evento de novo pelo fluxo normal. Nao
 * ha reprocessamento automatico nem agendado: alguem decide, depois de
 * corrigir a causa raiz, que aquele evento especifico deve voltar.
 */
@Service
public class DlqService {

    private static final Logger log = LoggerFactory.getLogger(DlqService.class);

    private static final int LIMITE_PAYLOAD = 4000;
    private static final int LIMITE_DETALHE = 4000;

    private final DlqRepository repositorio;
    private final KafkaTemplate<String, String> reprocessamentoKafkaTemplate;
    private final ObjectMapper objectMapper;

    public DlqService(DlqRepository repositorio,
                       KafkaTemplate<String, String> reprocessamentoKafkaTemplate,
                       ObjectMapper objectMapper) {
        this.repositorio = repositorio;
        this.reprocessamentoKafkaTemplate = reprocessamentoKafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Grava um evento que falhou na DLQ permanente.
     *
     * @return true se gravado; false se aquela posicao Kafka ja estava na DLQ.
     */
    public boolean registrar(String origemTopico, int particao, long deslocamento,
                             String chave, String eventoId, String tipoEvento,
                             String payload, String cabecalhos, String motivo, String detalhe) {

        boolean gravado = repositorio.registrar(
                origemTopico, particao, deslocamento, chave, eventoId, tipoEvento,
                limitar(payload, LIMITE_PAYLOAD), cabecalhos, motivo, limitar(detalhe, LIMITE_DETALHE));

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

    /**
     * Reenvia o evento de posicao {@code id} da DLQ ao topico original,
     * restaurando os cabecalhos gravados (ce_id inclusive -- a idempotencia
     * do lado do consumidor faz o resto: se a causa NAO foi corrigida, o
     * evento simplesmente volta a cair na DLQ, numa posicao nova; se dois
     * reprocessamentos do mesmo evento chegarem a processar com sucesso, o
     * dedup por eventoId em evento_processado garante efeito unico).
     *
     * evento_dlq nunca e' alterada aqui -- cada tentativa vira uma linha
     * nova em dlq_reprocessamento, o registro do que falhou permanece
     * intacto para auditoria.
     */
    public void reprocessar(long id) {
        EventoDlqVO evento = repositorio.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("evento " + id + " nao existe na DLQ"));

        ProducerRecord<String, String> registro =
                new ProducerRecord<>(evento.getOrigemTopico(), evento.getChave(), evento.getPayload());
        for (Map.Entry<String, String> cabecalho : desserializarCabecalhos(evento.getCabecalhos()).entrySet()) {
            registro.headers().add(new RecordHeader(cabecalho.getKey(),
                    cabecalho.getValue().getBytes(StandardCharsets.UTF_8)));
        }

        reprocessamentoKafkaTemplate.send(registro);
        repositorio.registrarReprocessamento(id);

        log.info("evento {} da DLQ reenviado para reprocessamento  topico={}  eventoId={}",
                id, evento.getOrigemTopico(), evento.getEventoId());
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> desserializarCabecalhos(String cabecalhosJson) {
        if (cabecalhosJson == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(cabecalhosJson, Map.class);
        } catch (Exception e) {
            log.warn("nao foi possivel desserializar os cabecalhos originais, reprocessando sem eles", e);
            return Map.of();
        }
    }

    private String limitar(String valor, int limite) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= limite ? valor : valor.substring(0, limite);
    }
}