package br.pucminas.aed.manejo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.test.context.ActiveProfiles;

import br.pucminas.aed.manejo.domain.EventoDlqVO;
import br.pucminas.aed.manejo.domain.PesagemRegistradaEvent;

/**
 * A DLQ permanente (ADR-006): o registro do que deixou de ser processado
 * nos fluxos de pesagem e embarque. Mesmo espirito dos outros testes do
 * servico — H2, sem Docker e sem broker — cobrindo (1) a gravacao e a
 * deduplicacao por posicao Kafka (origem_topico/particao/deslocamento) e
 * (2) o recoverer traduzindo os dois motivos de falha: poison message que
 * nao desserializa e erro de regra de negocio.
 *
 * Roda com: mvn -f servico-manejo/pom.xml test
 */
@SpringBootTest
@ActiveProfiles("test")
class DlqServiceTest {

    private static final String TOPICO_PESAGEM = "gado.animal.pesagem-registrada.v1";

    @Autowired
    private DlqService dlqService;

    @Autowired
    private DlqRecoverer dlqRecoverer;

    @Autowired
    private DlqRepository repositorio;

    @AfterEach
    void limparEstado() {
        repositorio.limparTudo();
    }

    @Test
    void falhaRegistradaFicaGravaNaDlqPermanente() {
        boolean gravado = dlqService.registrar(TOPICO_PESAGEM, 1, 42, "AN-001", "evt-pes-001",
                "gado.animal.pesagem-registrada.v1", "{\"pesoKg\":412.6}", "IllegalArgumentException",
                "peso negativo nao faz sentido");

        assertThat(gravado).isTrue();
        assertThat(repositorio.contar()).isEqualTo(1L);

        EventoDlqVO linha = repositorio.listar().get(0);
        assertThat(linha.getOrigemTopico()).isEqualTo(TOPICO_PESAGEM);
        assertThat(linha.getParticao()).isEqualTo(1);
        assertThat(linha.getDeslocamento()).isEqualTo(42L);
        assertThat(linha.getChave()).isEqualTo("AN-001");
        assertThat(linha.getEventoId()).isEqualTo("evt-pes-001");
        assertThat(linha.getPayload()).isEqualTo("{\"pesoKg\":412.6}");
        assertThat(linha.getMotivo()).isEqualTo("IllegalArgumentException");
        assertThat(linha.getRegistradoEm()).isNotNull();
    }

    @Test
    void mesmaPosicaoKafkaFicaGravadaUmaSoVez() {
        dlqService.registrar(TOPICO_PESAGEM, 0, 7, "AN-002", "evt-pes-002",
                TOPICO_PESAGEM, "{\"pesoKg\":401.0}", "ExcecaoQualquer", "erro");
        boolean reentrega = dlqService.registrar(TOPICO_PESAGEM, 0, 7, "AN-002", "evt-pes-002",
                TOPICO_PESAGEM, "{\"pesoKg\":401.0}", "ExcecaoQualquer", "erro");

        assertThat(reentrega).isFalse();
        assertThat(repositorio.contar()).isEqualTo(1L);
    }

    @Test
    void payloadEnormeTemDetalheLimitadoParaCabEREm4000() {
        String payloadGrande = "x".repeat(10_000);
        String detalheGrande = "d".repeat(10_000);

        dlqService.registrar(TOPICO_PESAGEM, 2, 9, "AN-003", "evt-pes-003",
                TOPICO_PESAGEM, payloadGrande, "ErroQualquer", detalheGrande);

        EventoDlqVO linha = repositorio.listar().get(0);
        assertThat(linha.getPayload()).hasSize(4000);
        assertThat(linha.getDetalhe()).hasSize(4000);
    }

    @Test
    void recovererMoveErroDeRegraDeNegocioParaADlq() {
        PesagemRegistradaEvent evento = new PesagemRegistradaEvent("evt-neg-001", Instant.parse("2026-09-14T10:00:00Z"),
                "AN-004", 412.6);
        ConsumerRecord<String, PesagemRegistradaEvent> registro =
                new ConsumerRecord<>(TOPICO_PESAGEM, 0, 12L, "AN-004", evento);
        registro.headers().add(new RecordHeader("ce_id", "evt-neg-001".getBytes(StandardCharsets.UTF_8)));
        registro.headers().add(new RecordHeader("ce_type", TOPICO_PESAGEM.getBytes(StandardCharsets.UTF_8)));

        dlqRecoverer.accept(registro, new IllegalArgumentException("animal inexistente"));

        List<EventoDlqVO> linhas = repositorio.listar();
        assertThat(linhas).hasSize(1);
        EventoDlqVO linha = linhas.get(0);
        assertThat(linha.getOrigemTopico()).isEqualTo(TOPICO_PESAGEM);
        assertThat(linha.getParticao()).isEqualTo(0);
        assertThat(linha.getDeslocamento()).isEqualTo(12L);
        assertThat(linha.getChave()).isEqualTo("AN-004");
        assertThat(linha.getEventoId()).isEqualTo("evt-neg-001");
        assertThat(linha.getTipoEvento()).isEqualTo(TOPICO_PESAGEM);
        assertThat(linha.getMotivo()).isEqualTo("IllegalArgumentException");
        assertThat(linha.getPayload()).contains("\"pesoKg\"");
        assertThat(linha.getPayload()).contains("\"animalId\":\"AN-004\"");
    }

    @Test
    void recovererMovePoisonMessageComPayloadBrutoParaADlq() {
        byte[] bruto = "{\"eventoId\":\"evt-veneno\",\"pesoKg\":\"nao-e-numero\"}"
                .getBytes(StandardCharsets.UTF_8);
        ConsumerRecord<String, Object> registro = new ConsumerRecord<>(TOPICO_PESAGEM, 3, 91L, "AN-005", null);
        registro.headers().add(new RecordHeader("ce_id", "evt-veneno".getBytes(StandardCharsets.UTF_8)));

        DeserializationException veneno = new DeserializationException(
                "nao desserializa", bruto, false, new RuntimeException("json invalido"));

        dlqRecoverer.accept(registro, veneno);

        EventoDlqVO linha = repositorio.listar().get(0);
        assertThat(linha.getParticao()).isEqualTo(3);
        assertThat(linha.getDeslocamento()).isEqualTo(91L);
        assertThat(linha.getEventoId()).isEqualTo("evt-veneno");
        assertThat(linha.getMotivo()).isEqualTo("DeserializationException");
        // a poison message nao tem visao desserializada — o payload e o BYTE
        // A BYTE do que chegou no fio, nada reconstruido.
        assertThat(linha.getPayload()).isEqualTo(new String(bruto, StandardCharsets.UTF_8));
    }
}