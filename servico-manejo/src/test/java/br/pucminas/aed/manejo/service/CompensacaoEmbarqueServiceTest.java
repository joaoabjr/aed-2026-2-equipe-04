package br.pucminas.aed.manejo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import br.pucminas.aed.manejo.domain.Animal;
import br.pucminas.aed.manejo.domain.AnimalRejeitadoNoEmbarqueEvent;
import br.pucminas.aed.manejo.domain.Fazenda;
import br.pucminas.aed.manejo.domain.Lote;
import br.pucminas.aed.manejo.domain.Venda;

/**
 * O caminho de exceção do domínio (ADR-002): a compensação disparada pela
 * recusa do animal na triagem do frigorífico. Mesmo espirito de
 * HistoricoPesagemServiceTest — prova a idempotência por eventoId — mais os
 * dois efeitos de negocio que so' esta compensação escreve: a dieta
 * reavaliada e o encerramento da venda que nao se concretizou.
 *
 * Roda com: mvn -f servico-manejo/pom.xml test
 */
@SpringBootTest
@ActiveProfiles("test")
class CompensacaoEmbarqueServiceTest {

    @Autowired
    private FazendaService fazendaService;

    @Autowired
    private LoteService loteService;

    @Autowired
    private AnimalService animalService;

    @Autowired
    private VendaService vendaService;

    @Autowired
    private CompensacaoEmbarqueService compensacaoEmbarqueService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void limparEstado() {
        jdbcTemplate.update("DELETE FROM venda");
        jdbcTemplate.update("DELETE FROM animal");
        jdbcTemplate.update("DELETE FROM lote");
        jdbcTemplate.update("DELETE FROM fazenda");
        jdbcTemplate.update("DELETE FROM evento_processado");
    }

    @Test
    void rejeicaoPorPesoInsuficienteReavaliaDietaParaReforcoEnergetico() {
        Animal animal = cadastrarAnimalNoLote("AN-001", "LT-001");

        boolean aplicado = compensacaoEmbarqueService.processar("evt-rej-001",
                evento("evt-rej-001", "AN-001", "PESO_INSUFICIENTE"));

        assertThat(aplicado).isTrue();
        Animal atualizado = animalService.buscarPorId("AN-001").orElseThrow();
        assertThat(atualizado.getDietaAtual()).isEqualTo("REFORCO_ENERGETICO");
        // "retorna ao lote de origem": o lote nunca mudou, e continua sendo o mesmo.
        assertThat(atualizado.getLote().getId()).isEqualTo(animal.getLote().getId());
    }

    @Test
    void rejeicaoPorOutroMotivoMantemDietaEmManutencao() {
        cadastrarAnimalNoLote("AN-002", "LT-002");

        compensacaoEmbarqueService.processar("evt-rej-002",
                evento("evt-rej-002", "AN-002", "VACINACAO_VENCIDA"));

        assertThat(animalService.buscarPorId("AN-002").orElseThrow().getDietaAtual()).isEqualTo("MANUTENCAO");
    }

    @Test
    void mesmoEventoEntregueTresVezesAplicaCompensacaoUmaSoVez() {
        cadastrarAnimalNoLote("AN-003", "LT-003");
        AnimalRejeitadoNoEmbarqueEvent evento = evento("evt-rej-003", "AN-003", "PESO_INSUFICIENTE");

        boolean primeira = compensacaoEmbarqueService.processar("evt-rej-003", evento);
        boolean segunda = compensacaoEmbarqueService.processar("evt-rej-003", evento);   // reentrega
        boolean terceira = compensacaoEmbarqueService.processar("evt-rej-003", evento);  // reentrega

        assertThat(primeira).isTrue();
        assertThat(segunda).isFalse();
        assertThat(terceira).isFalse();
    }

    @Test
    void rejeicaoEncerraVendaAbertaDoAnimal() {
        cadastrarAnimalNoLote("AN-004", "LT-004");
        vendaService.registrar(new Venda("VD-001", "AN-004", null, "Frigorifico Exemplo S.A.", 480.0));
        assertThat(vendaService.buscarPorAnimal("AN-004")).hasSize(1);

        compensacaoEmbarqueService.processar("evt-rej-004", evento("evt-rej-004", "AN-004", "PESO_INSUFICIENTE"));

        assertThat(vendaService.buscarPorAnimal("AN-004")).isEmpty();
    }

    @Test
    void rejeicaoDeAnimalInexistenteLancaExcecao() {
        assertThatThrownBy(() -> compensacaoEmbarqueService.processar("evt-rej-005",
                evento("evt-rej-005", "AN-999", "PESO_INSUFICIENTE")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Animal cadastrarAnimalNoLote(String animalId, String loteId) {
        Fazenda fazenda = new Fazenda("FZ-" + loteId, "Fazenda Exemplo");
        fazendaService.registrar(fazenda);
        Lote lote = new Lote(loteId, 1, fazenda);
        loteService.registrar(lote);
        Animal animal = new Animal(animalId, "Mimosa", "Nelore", 3, new Date(), lote);
        return animalService.registrar(animal);
    }

    private AnimalRejeitadoNoEmbarqueEvent evento(String eventoId, String animalId, String motivoRejeicao) {
        return new AnimalRejeitadoNoEmbarqueEvent(eventoId, Instant.now(), animalId, motivoRejeicao);
    }
}
