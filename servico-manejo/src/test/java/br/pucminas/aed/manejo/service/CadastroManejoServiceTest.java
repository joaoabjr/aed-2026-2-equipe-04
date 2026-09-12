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
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void limparEstado() {
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
}
