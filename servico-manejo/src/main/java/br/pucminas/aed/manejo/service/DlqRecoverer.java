package br.pucminas.aed.manejo.service;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.ErrorHandlingUtils;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * O recuperador (recoverer) do DefaultErrorHandler dos consumidores de
 * pesagem e embarque: e' chamado depois que as tentativas de processamento
 * se esgotam, com o ConsumerRecord que falhou e a excecao que derrubou o
 * listener. Em vez de travar a particao reprocessando para sempre, este
 * recuperador traduz o record em conceitos de negocio e grava a falha na
 * DLQ permanente (DlqService) — só depois disso o container confirma o
 * offset do topico original (ackAfterHandle), e o consumo segue.
 *
 * Cobre os dois motivos de falha:
 *   - poison message: o valor nao desserializa. Nesse caso o
 *     registro.value() e' null e a excecao raiz e' DeserializationException — o
 *     payload gravado sao os BYTES ORIGINAIS da mensagem (getData()), o
 *     unico registro fiel possivel do que chegou no fio;
 *   - erro de regra de negocio: o valor desserializou mas o processamento
 *     lancou. O payload gravado e' a visao que ESTE servico tem do evento,
 *     resserializada (os campos desconhecidos do contrato tolerante, ex.
 *     metodoDePesagem, nao sao reconstituiveis a partir do objeto).
 *
 * Nao engole erro da DLQ de proposito: se a gravacao permanente falhar, a
 * excecao se propaga, o record NAO e' confirmado e volta a ser entregue —
 * perder silenciosamente e' pior do que reprocessar.
 */
@Component
public class DlqRecoverer implements ConsumerRecordRecoverer {

    private static final String CABECALHO_EVENTO_ID = "ce_id";
    private static final String CABECALHO_TIPO_EVENTO = "ce_type";

    private final DlqService dlqService;
    private final ObjectMapper objectMapper;

    public DlqRecoverer(DlqService dlqService, ObjectMapper objectMapper) {
        this.dlqService = dlqService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void accept(ConsumerRecord<?, ?> registro, Exception excecao) {
        Exception raiz = ErrorHandlingUtils.findRootCause(excecao);

        dlqService.registrar(
                registro.topic(),
                registro.partition(),
                registro.offset(),
                chaveDoRegistro(registro),
                lerCabecalho(registro, CABECALHO_EVENTO_ID),
                lerCabecalho(registro, CABECALHO_TIPO_EVENTO),
                extrairPayload(registro, raiz),
                motivo(raiz),
                raiz.getMessage());
    }

    private String extrairPayload(ConsumerRecord<?, ?> registro, Exception raiz) {
        if (raiz instanceof DeserializationException deserializacao && !deserializacao.isKey()) {
            return new String(deserializacao.getData(), StandardCharsets.UTF_8);
        }
        Object valor = registro.value();
        if (valor == null) {
            return "null";
        }
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (Exception e) {
            return String.valueOf(valor);
        }
    }

    private String chaveDoRegistro(ConsumerRecord<?, ?> registro) {
        Object chave = registro.key();
        return chave == null ? null : String.valueOf(chave);
    }

    private String motivo(Exception raiz) {
        return raiz == null ? "Desconhecido" : raiz.getClass().getSimpleName();
    }

    private String lerCabecalho(ConsumerRecord<?, ?> registro, String nome) {
        Header cabecalho = registro.headers().lastHeader(nome);
        if (cabecalho == null) {
            return null;
        }
        return new String(cabecalho.value(), StandardCharsets.UTF_8);
    }
}