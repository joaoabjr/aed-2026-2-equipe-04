package br.pucminas.aed.manejo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.aed.manejo.domain.PesagemRegistradaEvent;

/**
 * O coracao da idempotencia deste servico.
 *
 * O registro da chave de deduplicacao e o efeito de negocio (anexar a
 * pesagem ao historico) precisam estar na MESMA transacao — se estivessem
 * em transacoes separadas, existiria uma janela em que o processo morre
 * entre as duas e o evento seria reprocessado, exatamente o que se quer
 * evitar.
 *
 * @Transactional fica AQUI, nao no listener: assim o ack do offset (que
 * acontece no listener) so ocorre depois que este commit terminou.
 */
@Service
public class HistoricoPesagemService {

    private static final Logger log = LoggerFactory.getLogger(HistoricoPesagemService.class);

    private final HistoricoPesagemRepository repositorio;

    public HistoricoPesagemService(HistoricoPesagemRepository repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * @return true se o efeito de negocio foi aplicado; false se era duplicata.
     */
    @Transactional
    public boolean processar(String eventoId, PesagemRegistradaEvent evento) {

        boolean primeiraVez = repositorio.registrarEventoSeNovo(eventoId);
        if (!primeiraVez) {
            log.info("evento {} JA PROCESSADO, descartando em silencio", eventoId);
            return false;
        }

        repositorio.registrarPesagem(evento.getAnimalId(), evento.getPesoKg(), evento.getOcorridoEm());

        log.info("pesagem registrada  evento={}  animal={}  pesoKg={}",
                eventoId, evento.getAnimalId(), evento.getPesoKg());
        return true;
    }
}