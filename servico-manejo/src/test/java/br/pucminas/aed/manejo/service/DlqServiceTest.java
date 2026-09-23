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
 * servico -- H2, sem Docker e sem broker -- cobrindo (1) a gravacao e a
 * deduplicacao por posicao Kafka (origem_topico/particao/deslocamento),
 * (2) o recoverer traduzindo os dois motivos de falha: poison message que
 * nao desserializa e erro de regra de negocio, com o envelope de
 * cabecalhos original preservado, e (3) o reprocessamento manual.
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
                "gado.animal.pesagem-registrada.v1", "{\"pesoKg\":412.6}", null, "IllegalArgumentException",
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
                TOPICO_PESAGEM, "{\"pesoKg\":401.0}", null, "ExcecaoQualquer", "erro");
        boolean reentrega = dlqService.registrar(TOPICO_PESAGEM, 0, 7, "AN-002", "evt-pes-002",
                TOPICO_PESAGEM, "{\"pesoKg\":401.0}", null, "ExcecaoQualquer", "erro");

        assertThat(reentrega).isFalse();
        assertThat(repositorio.contar()).isEqualTo(1L);
    }

    @Test
    void payloadEnormeTemDetalheLimitadoParaCabEREm4000() {
        String payloadGrande = "x".repeat(10_000);
        String detalheGrande = "d".repeat(10_000);

        dlqService.registrar(TOPICO_PESAGEM, 2, 9, "AN-003", "evt-pes-003",
                TOPICO_PESAGEM, payloadGrande, null, "ErroQualquer", detalheGrande);

        EventoDlqVO linha = repositorio.listar().get(0);
        assertThat(linha.getPayload()).hasSize(4000);
        assertThat(linha.getDetalhe()).hasSize(4000);
    }

    @Test
    void recovererMoveErroDeRegraDeNegocioParaADlqComOEnvelopeInteiro() {
        PesagemRegistradaEvent evento = new PesagemRegistradaEvent("evt-neg-001", Instant.parse("2026-09-14T10:00:00Z"),
                "AN-004", 412.6);
        ConsumerRecord<String, PesagemRegistradaEvent> registro =
                new ConsumerRecord<>(TOPICO_PESAGEM, 0, 12L, "AN-004", evento);
        registro.headers().add(new RecordHeader("ce_specversion", "1.0".getBytes(StandardCharsets.UTF_8)));
        registro.headers().add(new RecordHeader("ce_id", "evt-neg-001".getBytes(StandardCharsets.UTF_8)));
        registro.headers().add(new RecordHeader("ce_source", "/fazenda-corte/pesagem-service".getBytes(StandardCharsets.UTF_8)));
        registro.headers().add(new RecordHeader("ce_type", TOPICO_PESAGEM.getBytes(StandardCharsets.UTF_8)));
        registro.headers().add(new RecordHeader("ce_time", "2026-09-14T10:00:00Z".getBytes(StandardCharsets.UTF_8)));
        registro.headers().add(new RecordHeader("ce_subject", "animal/AN-004".getBytes(StandardCharsets.UTF_8)));

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
        // o envelope CloudEvents inteiro fica preservado, nao so ce_id/ce_type --
        // e o que torna o reprocessamento fiel ao original possivel.
        assertThat(linha.getCabecalhos()).contains("\"ce_specversion\":\"1.0\"");
        assertThat(linha.getCabecalhos()).contains("\"ce_source\":\"/fazenda-corte/pesagem-service\"");
        assertThat(linha.getCabecalhos()).contains("\"ce_subject\":\"animal/AN-004\"");
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
        // a poison message nao tem visao desserializada -- o payload e o BYTE
        // A BYTE do que chegou no fio, nada reconstruido.
        assertThat(linha.getPayload()).isEqualTo(new String(bruto, StandardCharsets.UTF_8));
    }

    @Test
    void buscarPorIdEncontraOEventoGravado() {
        dlqService.registrar(TOPICO_PESAGEM, 1, 55, "AN-006", "evt-pes-006",
                TOPICO_PESAGEM, "{\"pesoKg\":420.0}", "{\"ce_id\":\"evt-pes-006\"}",
                "IllegalArgumentException", "causa ja corrigida");
        long id = repositorio.listar().get(0).getId();

        EventoDlqVO encontrado = repositorio.buscarPorId(id).orElseThrow();
        assertThat(encontrado.getPayload()).isEqualTo("{\"pesoKg\":420.0}");
        assertThat(repositorio.buscarPorId(id + 999)).isEmpty();
    }

    /**
     * O caminho de DlqService.reprocessar() que toca Kafka de verdade
     * (KafkaTemplate.send ao topico original) nao e' exercitado aqui pelo
     * mesmo motivo de nenhum publisher do projeto ser testado nesta suite:
     * o perfil de teste roda so com H2, sem broker (bootstrap-servers
     * aponta pra um host que nunca responde, de proposito). O que E'
     * testavel sem rede -- que cada tentativa de reprocessamento vira uma
     * linha nova, e evento_dlq nunca e' alterada -- e' o que este teste
     * cobre, direto no repositorio; a demonstracao ponta-a-ponta do
     * reprocessamento esta no README (curl contra o servico rodando com
     * Kafka de verdade).
     */
    @Test
    void cadaTentativaDeReprocessamentoVirauUmaLinhaNovaSemAlterarEventoDlq() {
        dlqService.registrar(TOPICO_PESAGEM, 1, 56, "AN-007", "evt-pes-007",
                TOPICO_PESAGEM, "{\"pesoKg\":430.0}", null, "IllegalArgumentException", "causa ja corrigida");
        long id = repositorio.listar().get(0).getId();

        repositorio.registrarReprocessamento(id);
        repositorio.registrarReprocessamento(id);

        assertThat(repositorio.contarReprocessamentos(id)).isEqualTo(2);
        // a linha original de evento_dlq continua igual -- reprocessar nunca a altera.
        EventoDlqVO linha = repositorio.buscarPorId(id).orElseThrow();
        assertThat(linha.getPayload()).isEqualTo("{\"pesoKg\":430.0}");
        assertThat(linha.getMotivo()).isEqualTo("IllegalArgumentException");
    }
}