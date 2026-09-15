package br.pucminas.aed.manejo.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.pucminas.aed.manejo.domain.EventoArmazenadoVO;
import br.pucminas.aed.manejo.domain.LoteFormadoEvent;
import br.pucminas.aed.manejo.domain.LoteMovidoDePastoEvent;

/**
 * O fold do event store do agregado Lote (localizacao) na projecao —
 * ADR-005. A mesma logica de dobrar os eventos em ordem serve tanto para
 * atualizar a projecao incrementalmente (evento novo chegando pelo Kafka)
 * quanto para reconstrui-la inteira por replay: os dois casos
 * (LoteFormado / LoteMovidoDePasto) sao os mesmos, so muda de onde vem o
 * dado — do evento recem-chegado, ou de uma linha relida do event store.
 */
@Service
public class LoteLocalizacaoService {

    private static final Logger log = LoggerFactory.getLogger(LoteLocalizacaoService.class);
    private static final String TIPO_LOTE_FORMADO = "LoteFormado";
    private static final String TIPO_LOTE_MOVIDO = "LoteMovidoDePasto";

    private final LoteEventoStoreRepository eventoStoreRepository;
    private final LoteLocalizacaoRepository localizacaoRepository;
    private final ObjectMapper objectMapper;

    public LoteLocalizacaoService(LoteEventoStoreRepository eventoStoreRepository,
                                   LoteLocalizacaoRepository localizacaoRepository,
                                   ObjectMapper objectMapper) {
        this.eventoStoreRepository = eventoStoreRepository;
        this.localizacaoRepository = localizacaoRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void processarFormacao(LoteFormadoEvent evento) {
        if (eventoStoreRepository.jaProcessado(evento.getEventoId())) {
            log.info("evento {} ja processado, descartando em silencio", evento.getEventoId());
            return;
        }

        long versao = eventoStoreRepository.proximaVersao(evento.getLoteId());
        boolean anexado = eventoStoreRepository.anexar(evento.getLoteId(), versao, evento.getEventoId(),
                TIPO_LOTE_FORMADO, evento.getOcorridoEm(), serializar(evento));
        if (!anexado) {
            throw new IllegalStateException("conflito de versao ao anexar evento " + evento.getEventoId()
                    + " do lote " + evento.getLoteId() + " na versao " + versao);
        }

        localizacaoRepository.registrarFormacao(evento.getLoteId(), versao, evento.getOcorridoEm());
        log.info("lote formado anexado ao event store  loteId={}  versao={}", evento.getLoteId(), versao);
    }

    @Transactional
    public void processarMovimentacao(LoteMovidoDePastoEvent evento) {
        if (eventoStoreRepository.jaProcessado(evento.getEventoId())) {
            log.info("evento {} ja processado, descartando em silencio", evento.getEventoId());
            return;
        }

        long versao = eventoStoreRepository.proximaVersao(evento.getLoteId());
        boolean anexado = eventoStoreRepository.anexar(evento.getLoteId(), versao, evento.getEventoId(),
                TIPO_LOTE_MOVIDO, evento.getOcorridoEm(), serializar(evento));
        if (!anexado) {
            throw new IllegalStateException("conflito de versao ao anexar evento " + evento.getEventoId()
                    + " do lote " + evento.getLoteId() + " na versao " + versao);
        }

        localizacaoRepository.registrarMovimentacao(evento.getLoteId(), evento.getPastoDestinoId(), versao,
                evento.getOcorridoEm());
        log.info("lote movido anexado ao event store  loteId={}  versao={}  pastoDestino={}",
                evento.getLoteId(), versao, evento.getPastoDestinoId());
    }

    /**
     * Apaga a projecao de UM lote e a reconstroi inteira, relendo o event
     * store do inicio — a prova de que a projecao e' realmente descartavel.
     * O event store nunca e' tocado aqui: ele e' sempre a fonte da verdade.
     */
    @Transactional
    public void reconstruirProjecao(String loteId) {
        localizacaoRepository.remover(loteId);

        List<EventoArmazenadoVO> stream = eventoStoreRepository.lerStream(loteId);
        for (EventoArmazenadoVO evento : stream) {
            if (TIPO_LOTE_FORMADO.equals(evento.getTipoEvento())) {
                localizacaoRepository.registrarFormacao(loteId, evento.getVersao(), evento.getOcorridoEm());
            } else if (TIPO_LOTE_MOVIDO.equals(evento.getTipoEvento())) {
                LoteMovidoDePastoEvent movimentacao = desserializarMovimentacao(evento.getPayload());
                localizacaoRepository.registrarMovimentacao(loteId, movimentacao.getPastoDestinoId(),
                        evento.getVersao(), evento.getOcorridoEm());
            }
        }
        log.info("projecao reconstruida por replay  loteId={}  eventosNoStream={}", loteId, stream.size());
    }

    public Optional<String> localizacaoAtual(String loteId) {
        return localizacaoRepository.buscarPastoAtual(loteId);
    }

    private String serializar(Object evento) {
        try {
            return objectMapper.writeValueAsString(evento);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao serializar evento para o event store", e);
        }
    }

    private LoteMovidoDePastoEvent desserializarMovimentacao(String payload) {
        try {
            return objectMapper.readValue(payload, LoteMovidoDePastoEvent.class);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao desserializar evento do event store", e);
        }
    }
}
