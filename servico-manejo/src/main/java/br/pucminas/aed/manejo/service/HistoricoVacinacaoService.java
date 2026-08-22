package br.pucminas.aed.manejo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.aed.manejo.domain.VacinacaoRegistradaEvent;


@Service
public class HistoricoVacinacaoService {

    private static final Logger log = LoggerFactory.getLogger(HistoricoVacinacaoService.class);

    private final HistoricoVacinacaoRepository repositorio;

    public HistoricoVacinacaoService(HistoricoVacinacaoRepository repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * @return true se o efeito de negocio foi aplicado; false se era duplicata.
     */
    @Transactional
    public boolean processar(String eventoId, VacinacaoRegistradaEvent evento) {

        boolean primeiraVez = repositorio.registrarEventoSeNovo(eventoId);
        if (!primeiraVez) {
            log.info("evento {} JA PROCESSADO, descartando em silencio", eventoId);
            return false;
        }

        repositorio.registrarVacinacao(evento.getAnimalId(), evento.getVacina(), evento.getMetodoDeVacinacao(), evento.getOcorridoEm());

        log.info("vacinacao registrada  evento={}  animal={}  vacina={}",
                eventoId, evento.getAnimalId(), evento.getVacina());
        return true;
    }
}