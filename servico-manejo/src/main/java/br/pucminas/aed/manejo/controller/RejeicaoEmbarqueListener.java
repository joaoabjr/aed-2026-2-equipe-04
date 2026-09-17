package br.pucminas.aed.manejo.controller;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import br.pucminas.aed.manejo.domain.AnimalRejeitadoNoEmbarqueEvent;
import br.pucminas.aed.manejo.service.CompensacaoEmbarqueService;

/**
 * O consumidor do caminho de exceção do domínio (ADR-002): a recusa do
 * animal na triagem do frigorífico, publicada pelo servico-expedicao.
 *
 * Mesmo desenho de PesagemListener/VacinacaoListener: processa dentro da
 * transação do service e só confirma o offset (ack.acknowledge()) DEPOIS
 * do commit — é a compensação, não so' observabilidade, entao precisa do
 * mesmo cuidado com idempotencia.
 *
 * Group.id proprio ("manejo-rejeicao-embarque"), num topico proprio — nao
 * compete por particoes com nenhum outro consumidor deste servico.
 */
@Component
public class RejeicaoEmbarqueListener {

    private static final String CABECALHO_ID = "ce_id";

    private final CompensacaoEmbarqueService compensacaoEmbarqueService;

    public RejeicaoEmbarqueListener(CompensacaoEmbarqueService compensacaoEmbarqueService) {
        this.compensacaoEmbarqueService = compensacaoEmbarqueService;
    }

    @KafkaListener(topics = "${demo.topico-rejeicao-embarque}", groupId = "${demo.grupo-rejeicao-embarque}",
            containerFactory = "rejeicaoEmbarqueKafkaListenerContainerFactory")
    public void aoRejeitarEmbarque(ConsumerRecord<String, AnimalRejeitadoNoEmbarqueEvent> registro,
                                   Acknowledgment ack) {

        String eventoId = lerCabecalho(registro, CABECALHO_ID);

        compensacaoEmbarqueService.processar(eventoId, registro.value());

        ack.acknowledge(); // confirma DEPOIS do commit da transacao
    }

    private String lerCabecalho(ConsumerRecord<String, AnimalRejeitadoNoEmbarqueEvent> registro, String nome) {
        Header cabecalho = registro.headers().lastHeader(nome);
        if (cabecalho == null) {
            return null;
        }
        return new String(cabecalho.value(), StandardCharsets.UTF_8);
    }
}
