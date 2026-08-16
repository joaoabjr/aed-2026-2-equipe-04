package br.pucminas.aed.pesagem.service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import br.pucminas.aed.pesagem.domain.PesagemRegistradaEvent;

/**
 * Monta o envelope CloudEvents 1.0 em MODO BINARIO: os atributos ce_* vao nos
 * cabecalhos da mensagem, o corpo carrega so o fato de negocio (data).
 *
 * Os quatro obrigatorios (specversion, id, source, type) mais time (quando o
 * FATO aconteceu, nao quando o broker recebeu). subject e datacontenttype sao
 * opcionais no CloudEvents mas baratos de incluir: subject identifica o
 * recurso de forma legivel sem abrir o corpo ("animal/AN-004821"), e
 * datacontenttype documenta o formato da carga para quem so olha o cabecalho.
 *
 * A CHAVE DE PARTICAO e animalId: e a menor unidade cuja ordem o negocio
 * exige — duas pesagens do MESMO animal precisam chegar ao consumidor na
 * ordem em que aconteceram (a curva de peso depende disso). Pesagens de
 * animais diferentes podem ser processadas fora de ordem entre si sem
 * problema, por isso nao precisamos de uma so particao para o topico inteiro.
 */
@Service
public class PesagemService {

    private static final String TYPE = "gado.animal.pesagem-registrada.v1";
    private static final String SOURCE = "/fazenda-corte/pesagem-service";

    private final KafkaTemplate<String, PesagemRegistradaEvent> kafkaTemplate;
    private final PesagemCallbackService callbackService;
    private final String topico;

    public PesagemService(KafkaTemplate<String, PesagemRegistradaEvent> kafkaTemplate,
                           PesagemCallbackService callbackService,
                           @Value("${demo.topico}") String topico) {
        this.kafkaTemplate = kafkaTemplate;
        this.callbackService = callbackService;
        this.topico = topico;
    }

    public void publicar(PesagemRegistradaEvent evento) {

        ProducerRecord<String, PesagemRegistradaEvent> registro =
                new ProducerRecord<String, PesagemRegistradaEvent>(topico, evento.getAnimalId(), evento);

        adicionarCabecalho(registro, "ce_specversion", "1.0");
        adicionarCabecalho(registro, "ce_id", evento.getEventoId());
        adicionarCabecalho(registro, "ce_source", SOURCE);
        adicionarCabecalho(registro, "ce_type", TYPE);
        adicionarCabecalho(registro, "ce_time", evento.getOcorridoEm().toString());
        adicionarCabecalho(registro, "ce_subject", "animal/" + evento.getAnimalId());
        adicionarCabecalho(registro, "ce_datacontenttype", "application/json");

        CompletableFuture<SendResult<String, PesagemRegistradaEvent>> resultadoFuturo =
                kafkaTemplate.send(registro);

        callbackService.tratar(resultadoFuturo, evento.getEventoId());
    }

    private void adicionarCabecalho(ProducerRecord<String, PesagemRegistradaEvent> registro,
                                     String nome, String valor) {
        registro.headers().add(new RecordHeader(nome, valor.getBytes(StandardCharsets.UTF_8)));
    }
}