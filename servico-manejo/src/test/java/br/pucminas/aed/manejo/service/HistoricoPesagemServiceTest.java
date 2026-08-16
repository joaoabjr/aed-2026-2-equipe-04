package br.pucminas.aed.manejo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import br.pucminas.aed.manejo.domain.PesagemRegistradaEvent;

/**
 * O teste que a rubrica pede: "um teste automatizado que entregue o mesmo
 * evento tres vezes e verifique o efeito unico" (B.3), rodando com H2 —
 * sem Docker e sem o servico-pesagem no ar, no mesmo espirito dos testes do
 * demo da aula 01.
 *
 * Roda com: mvn -f servico-manejo/pom.xml test
 */
@SpringBootTest
@ActiveProfiles("test")
class HistoricoPesagemServiceTest {

    @Autowired
    private HistoricoPesagemService service;

    @Autowired
    private HistoricoPesagemRepository repositorio;

    @AfterEach
    void limparEstado() {
        repositorio.limparTudo();
    }

    @Test
    void eventoNovoRegistraUmaPesagem() {
        PesagemRegistradaEvent evento = criarEvento("evt-001", "AN-004821", 412.6);

        boolean aplicado = service.processar("evt-001", evento);

        assertThat(aplicado).isTrue();
        assertThat(repositorio.contarRegistrosDoAnimal("AN-004821")).isEqualTo(1L);
    }

    @Test
    void mesmoEventoEntregueTresVezesRegistraUmaSoVez() {
        PesagemRegistradaEvent evento = criarEvento("evt-002", "AN-004822", 388.4);

        boolean primeira = service.processar("evt-002", evento);
        boolean segunda = service.processar("evt-002", evento);   // reentrega
        boolean terceira = service.processar("evt-002", evento);  // reentrega

        assertThat(primeira).isTrue();
        assertThat(segunda).isFalse();
        assertThat(terceira).isFalse();
        assertThat(repositorio.contarRegistrosDoAnimal("AN-004822")).isEqualTo(1L);
    }

    @Test
    void duasPesagensLegitimasDoMesmoAnimalGeramDoisRegistros() {
        // eventoId DIFERENTE - duas leituras de peso reais, nao uma reentrega.
        // E o cuidado que o ADR-002 registrou: dedup e por eventoId, nunca por animalId.
        PesagemRegistradaEvent primeiraLeitura = criarEvento("evt-003", "AN-004823", 401.0);
        PesagemRegistradaEvent segundaLeitura = criarEvento("evt-004", "AN-004823", 405.2);

        service.processar("evt-003", primeiraLeitura);
        service.processar("evt-004", segundaLeitura);

        assertThat(repositorio.contarRegistrosDoAnimal("AN-004823")).isEqualTo(2L);
        assertThat(repositorio.historicoDePeso("AN-004823")).containsExactly(401.0, 405.2);
    }

    private PesagemRegistradaEvent criarEvento(String eventoId, String animalId, double pesoKg) {
        return new PesagemRegistradaEvent(eventoId, Instant.now(), animalId, pesoKg);
    }
}
