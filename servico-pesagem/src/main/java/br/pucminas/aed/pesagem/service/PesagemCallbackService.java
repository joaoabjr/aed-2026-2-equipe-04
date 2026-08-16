package br.pucminas.aed.pesagem.service;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import br.pucminas.aed.pesagem.domain.PesagemRegistradaEvent;

/**
 * Trata o resultado assincrono do envio ao Kafka.
 *
 * O send() do KafkaTemplate retorna imediatamente um CompletableFuture; o
 * erro de broker pode acontecer bem depois da resposta 202 do controller. E
 * aqui que ele aparece: quando a promessa completa com falha, o evento NAO
 * foi publicado e o operador precisa ver o motivo no log — silenciar esse
 * caso faria a balanca "aceitar" uma pesagem que nunca chegou ao consumidor.
 *
 * Sucesso tambem e logado, com particao e offset, para dar rastreabilidade
 * do evento no topico.
 */
@Service
public class PesagemCallbackService {

    private static final Logger log = LoggerFactory.getLogger(PesagemCallbackService.class);

    public void tratar(CompletableFuture<SendResult<String, PesagemRegistradaEvent>> resultadoFuturo,
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