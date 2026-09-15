package br.pucminas.aed.manejo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import br.pucminas.aed.manejo.domain.LoteFormadoEvent;
import br.pucminas.aed.manejo.domain.LoteMovidoDePastoEvent;

/**
 * O criterio que mais pesa na aula 05 (ADR-005): apagar a projecao inteira
 * e reconstruir pelo event store precisa dar o MESMO resultado que o fold
 * incremental original. Se nao der, a projecao virou fonte da verdade sem
 * ninguem decidir isso — exatamente o que o event store existe para evitar.
 *
 * Roda com: mvn -f servico-manejo/pom.xml test
 */
@SpringBootTest
@ActiveProfiles("test")
class LoteLocalizacaoServiceTest {

    @Autowired
    private LoteLocalizacaoService service;

    @Autowired
    private LoteEventoStoreRepository eventoStoreRepository;

    @Autowired
    private LoteLocalizacaoRepository localizacaoRepository;

    @AfterEach
    void limparEstado() {
        localizacaoRepository.limparTudo();
        eventoStoreRepository.limparTudo();
    }

    @Test
    void loteFormadoAindaNaoTemPastoAtual() {
        service.processarFormacao(criarFormacao("evt-lf-001", "LOTE-01"));

        assertThat(service.localizacaoAtual("LOTE-01")).isEmpty();
    }

    @Test
    void movimentacaoAtualizaOPastoAtual() {
        service.processarFormacao(criarFormacao("evt-lf-002", "LOTE-02"));
        service.processarMovimentacao(criarMovimentacao("evt-lm-002a", "LOTE-02", "PASTO-A", "PASTO-B"));

        assertThat(service.localizacaoAtual("LOTE-02")).contains("PASTO-B");
    }

    @Test
    void duasMovimentacoesEmSequenciaFicamComADestinoMaisRecente() {
        service.processarFormacao(criarFormacao("evt-lf-005", "LOTE-05"));
        service.processarMovimentacao(criarMovimentacao("evt-lm-005a", "LOTE-05", "PASTO-A", "PASTO-B"));
        service.processarMovimentacao(criarMovimentacao("evt-lm-005b", "LOTE-05", "PASTO-B", "PASTO-C"));

        assertThat(service.localizacaoAtual("LOTE-05")).contains("PASTO-C");
        assertThat(eventoStoreRepository.lerStream("LOTE-05")).hasSize(3); // formacao + 2 movimentacoes
    }

    @Test
    void mesmoEventoEntregueDuasVezesNaoDuplicaVersaoNoEventStore() {
        LoteFormadoEvent evento = criarFormacao("evt-lf-003", "LOTE-03");

        service.processarFormacao(evento);
        service.processarFormacao(evento); // reentrega

        assertThat(eventoStoreRepository.lerStream("LOTE-03")).hasSize(1);
    }

    @Test
    void projecaoReconstruidaPorReplayDaOMesmoResultadoQueOFoldIncremental() {
        String loteId = "LOTE-04";
        service.processarFormacao(criarFormacao("evt-lf-004", loteId));
        service.processarMovimentacao(criarMovimentacao("evt-lm-004a", loteId, "PASTO-A", "PASTO-B"));
        service.processarMovimentacao(criarMovimentacao("evt-lm-004b", loteId, "PASTO-B", "PASTO-C"));

        String pastoAntesDoReplay = service.localizacaoAtual(loteId).orElseThrow();
        assertThat(pastoAntesDoReplay).isEqualTo("PASTO-C");

        // apaga SO a projecao — o event store fica intacto, e' ele que sustenta o replay
        localizacaoRepository.remover(loteId);
        assertThat(service.localizacaoAtual(loteId)).isEmpty(); // prova que realmente apagou

        service.reconstruirProjecao(loteId);

        assertThat(service.localizacaoAtual(loteId)).contains(pastoAntesDoReplay);
    }

    private LoteFormadoEvent criarFormacao(String eventoId, String loteId) {
        return new LoteFormadoEvent(eventoId, Instant.now(), loteId, List.of(), "peso e idade semelhantes");
    }

    private LoteMovidoDePastoEvent criarMovimentacao(String eventoId, String loteId, String origem, String destino) {
        return new LoteMovidoDePastoEvent(eventoId, Instant.now(), loteId, origem, destino);
    }
}
