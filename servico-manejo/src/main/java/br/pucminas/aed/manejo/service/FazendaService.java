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

    @Transactional
    public void registrar(Fazenda fazenda) {
        if (fazendaRepository.buscarPorId(fazenda.getId()).isPresent()) {
            throw new IllegalArgumentException("Fazenda com ID " + fazenda.getId() + " ja existe");
        }
        fazendaRepository.salvar(fazenda);
        log.info("fazenda registrada  id={}  nome={}", fazenda.getId(), fazenda.getNome());
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Fazenda> buscarPorId(String id) {
        return fazendaRepository.buscarPorId(id);
    }
}