package br.pucminas.aed.manejo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.aed.manejo.domain.Fazenda;
import br.pucminas.aed.manejo.repository.FazendaRepository;

@Service
public class FazendaService {

    private static final Logger log = LoggerFactory.getLogger(FazendaService.class);

    private final FazendaRepository fazendaRepository;

    public FazendaService(FazendaRepository fazendaRepository) {
        this.fazendaRepository = fazendaRepository;
    }

    /**
     * Idempotente por id: se a fazenda ja existe, retorna a registrada no banco
     * (reentrega produz efeito nenhum alem do retorno) em vez de falhar.
     */
    @Transactional
    public Fazenda registrar(Fazenda fazenda) {
        return fazendaRepository.buscarPorId(fazenda.getId())
                .orElseGet(() -> {
                    fazendaRepository.salvar(fazenda);
                    log.info("fazenda registrada  id={}  nome={}", fazenda.getId(), fazenda.getNome());
                    return fazenda;
                });
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Fazenda> buscarPorId(String id) {
        return fazendaRepository.buscarPorId(id);
    }
}