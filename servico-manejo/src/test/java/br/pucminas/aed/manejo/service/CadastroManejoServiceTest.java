package br.pucminas.aed.manejo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import br.pucminas.aed.manejo.domain.Animal;
import br.pucminas.aed.manejo.domain.Fazenda;
import br.pucminas.aed.manejo.domain.Lote;
import br.pucminas.aed.manejo.domain.VacinacaoRegistradaEvent;
import br.pucminas.aed.manejo.domain.Venda;

@SpringBootTest
@ActiveProfiles("test")
class CadastroManejoServiceTest {

    @Autowired
    private FazendaService fazendaService;

    @Autowired
    private LoteService loteService;

    @Autowired
    private AnimalService animalService;

    @Autowired
    private HistoricoVacinacaoService historicoVacinacaoService;

    @Autowired
    private HistoricoVacinacaoRepository historicoVacinacaoRepository;

    @Autowired
    private VendaService vendaService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void limparEstado() {
        jdbcTemplate.update("DELETE FROM venda");
        jdbcTemplate.update("DELETE FROM animal");
        jdbcTemplate.update("DELETE FROM lote");
        jdbcTemplate.update("DELETE FROM fazenda");
        historicoVacinacaoRepository.limparTudo();
    }

    @Test
    void cadastraEstruturaERecuperaAnimalComLoteEFazenda() {
        Fazenda fazenda = new Fazenda("FZ-001", "Fazenda Exemplo");
        Lote lote = new Lote("LT-001", 1, fazenda);
        Animal animal = new Animal("AN-001", "Mimosa", "Nelore", 3, new Date(), lote);

        fazendaService.registrar(fazenda);
        loteService.registrar(lote);
        animalService.registrar(animal);

        Animal encontrado = animalService.buscarPorId("AN-001").orElseThrow();
        assertThat(encontrado.getLote().getNumeracao()).isEqualTo(1);
        assertThat(encontrado.getLote().getFazenda().getNome()).isEqualTo("Fazenda Exemplo");
    }

    @Test
    void registraHistoricoDeVacinacaoNoSchema() {
        VacinacaoRegistradaEvent evento = new VacinacaoRegistradaEvent(
                "evt-vac-001", Instant.now(), "AN-001", 0.0, "intramuscular",
                "Febre aftosa", Instant.now().plusSeconds(86_400));

        assertThat(historicoVacinacaoService.processar("evt-vac-001", evento)).isTrue();
        assertThat(historicoVacinacaoRepository.contarRegistrosDoAnimal("AN-001")).isEqualTo(1L);
    }

    @Test
    void reenviarMesmaFazendaEIdempotente() {
        fazendaService.registrar(new Fazenda("FZ-001", "Fazenda Exemplo"));

        Fazenda reenviada = fazendaService.registrar(new Fazenda("FZ-001", "Fazenda Exemplo Renomeada"));

        assertThat(reenviada.getNome()).isEqualTo("Fazenda Exemplo");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fazenda", Long.class)).isEqualTo(1L);
    }

    @Test
    void reenviarMesmoLoteEIdempotente() {
        fazendaService.registrar(new Fazenda("FZ-001", "Fazenda Exemplo"));
        Lote lote = new Lote("LT-001", 1, new Fazenda("FZ-001", "Fazenda Exemplo"));
        loteService.registrar(lote);

        Lote reenviado = loteService.registrar(new Lote("LT-001", 2, new Fazenda("FZ-001", "Fazenda Exemplo")));

        assertThat(reenviado.getNumeracao()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lote", Long.class)).isEqualTo(1L);
    }

    @Test
    void reenviarMesmoAnimalEIdempotente() {
        fazendaService.registrar(new Fazenda("FZ-001", "Fazenda Exemplo"));
        loteService.registrar(new Lote("LT-001", 1, new Fazenda("FZ-001", "Fazenda Exemplo")));
        Lote loteDoAnimal = new Lote("LT-001", 1, new Fazenda("FZ-001", "Fazenda Exemplo"));
        animalService.registrar(new Animal("AN-001", "Mimosa", "Nelore", 3, new Date(), loteDoAnimal));

        Animal reenviado = animalService.registrar(
                new Animal("AN-001", "Mimosa Renomeada", "Nelore", 4, new Date(), loteDoAnimal));

        assertThat(reenviado.getNomeDoAnimal()).isEqualTo("Mimosa");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM animal", Long.class)).isEqualTo(1L);
    }

    @Test
    void reenviarMesmaVendaEIdempotente() {
        fazendaService.registrar(new Fazenda("FZ-001", "Fazenda Exemplo"));
        loteService.registrar(new Lote("LT-001", 1, new Fazenda("FZ-001", "Fazenda Exemplo")));
        vendaService.registrar(new Venda("VD-001", null, "LT-001", "Frigorifico Exemplo S.A.", 480.0));

        Venda reenviada = vendaService.registrar(new Venda("VD-001", null, "LT-001", "Frigorifico Outro S.A.", 500.0));

        assertThat(reenviada.getFrigorifico()).isEqualTo("Frigorifico Exemplo S.A.");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM venda", Long.class)).isEqualTo(1L);
    }
}
