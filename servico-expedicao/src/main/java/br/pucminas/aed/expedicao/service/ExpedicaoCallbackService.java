package br.pucminas.aed.expedicao.service;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import br.pucminas.aed.expedicao.domain.AnimalEmbarcadoParaAbateEvent;
import br.pucminas.aed.expedicao.domain.AnimalRejeitadoNoEmbarqueEvent;

@Service
public class ExpedicaoCallbackService {

    private static final Logger log = LoggerFactory.getLogger(ExpedicaoCallbackService.class);

    public void tratar(CompletableFuture<SendResult<String, AnimalEmbarcadoParaAbateEvent>> resultadoFuturo,
                       String eventoId) {
        resultadoFuturo.whenComplete((resultado, erro) -> {
            if (erro != null) {
                log.error("falha ao publicar embarque {}", eventoId, erro);
                return;
            }
            log.info("embarque {} publicado  topico={}  particao={}  offset={}", eventoId,
                    resultado.getRecordMetadata().topic(), resultado.getRecordMetadata().partition(),
                    resultado.getRecordMetadata().offset());
        });
    }

    public void tratarRejeicao(CompletableFuture<SendResult<String, AnimalRejeitadoNoEmbarqueEvent>> resultadoFuturo,
                       String eventoId) {
        resultadoFuturo.whenComplete((resultado, erro) -> {
            if (erro != null) {
                log.error("falha ao publicar rejeicao de embarque {}", eventoId, erro);
                return;
            }
            log.info("rejeicao de embarque {} publicada  topico={}  particao={}  offset={}", eventoId,
                    resultado.getRecordMetadata().topic(), resultado.getRecordMetadata().partition(),
                    resultado.getRecordMetadata().offset());
        });
    }
}
