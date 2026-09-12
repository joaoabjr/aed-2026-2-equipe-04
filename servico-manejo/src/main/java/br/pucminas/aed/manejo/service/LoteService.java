package br.pucminas.aed.manejo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.aed.manejo.domain.Fazenda;
import br.pucminas.aed.manejo.domain.Lote;
import br.pucminas.aed.manejo.repository.LoteRepository;

@Service
public class LoteService {

    private static final Logger log = LoggerFactory.getLogger(LoteService.class);

    private final LoteRepository loteRepository;
    private final FazendaService fazendaService;

    public LoteService(LoteRepository loteRepository, FazendaService fazendaService) {
        this.loteRepository = loteRepository;
        this.fazendaService = fazendaService;
    }

    @Transactional
    public void registrar(Lote lote) {
        if (loteRepository.buscarPorId(lote.getId()).isPresent()) {
            throw new IllegalArgumentException("Lote com ID " + lote.getId() + " ja existe");
        }
        if (fazendaService.buscarPorId(lote.getFazenda().getId()).isEmpty()) {
            throw new IllegalArgumentException("Fazenda com ID " + lote.getFazenda().getId() + " nao existe");
        }
        loteRepository.salvar(lote);
        log.info("lote registrado  id={}  numeracao={}  fazenda={}", lote.getId(), lote.getNumeracao(), lote.getFazenda().getId());
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Lote> buscarPorId(String id) {
        return loteRepository.buscarPorId(id);
    }

    @Transactional(readOnly = true)
    public java.util.List<Lote> buscarPorFazenda(String fazendaId) {
        return loteRepository.buscarPorFazenda(fazendaId);
    }
}
