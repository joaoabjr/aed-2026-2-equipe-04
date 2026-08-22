package br.pucminas.aed.vacinacao.service;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import br.pucminas.aed.vacinacao.domain.VacinacaoRegistradaEvent;

@Service
public class VacinacaoCallbackService {

    private static final Logger log = LoggerFactory.getLogger(VacinacaoCallbackService.class);

    public void tratar(CompletableFuture<SendResult<String, VacinacaoRegistradaEvent>> resultadoFuturo,
                       String eventoId) {

        resultadoFuturo.whenComplete((resultado, erro) -> {
            if (erro != null) {
                log.error("FALHA ao publicar evento {} no topico: {}", eventoId, erro.getMessage(), erro);
                return;
            }
            log.info("evento {} publicado  topico={}  particao={}  offset={}",
                    eventoId,
                    resultado.getRecordMetadata().topic(),
                    resultado.getRecordMetadata().partition(),
                    resultado.getRecordMetadata().offset());
        });
    }
}