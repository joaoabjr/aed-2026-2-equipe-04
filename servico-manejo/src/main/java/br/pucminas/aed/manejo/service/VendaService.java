package br.pucminas.aed.manejo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.aed.manejo.domain.Venda;
import br.pucminas.aed.manejo.repository.AnimalRepository;
import br.pucminas.aed.manejo.repository.VendaRepository;

import java.util.List;
import java.util.Optional;

@Service
public class VendaService {

    private static final Logger log = LoggerFactory.getLogger(VendaService.class);

    private final VendaRepository vendaRepository;
    private final AnimalRepository animalRepository;
    private final LoteService loteService;

    public VendaService(VendaRepository vendaRepository, AnimalRepository animalRepository, LoteService loteService) {
        this.vendaRepository = vendaRepository;
        this.animalRepository = animalRepository;
        this.loteService = loteService;
    }

    @Transactional
    public Venda registrar(Venda venda) {
        return vendaRepository.buscarPorId(venda.getId())
                .orElseGet(() -> {
                    boolean temAnimal = venda.getAnimalId() != null && !venda.getAnimalId().isBlank();
                    boolean temLote = venda.getLoteId() != null && !venda.getLoteId().isBlank();

                    if (temAnimal == temLote) {
                        throw new IllegalArgumentException("Venda deve referenciar exatamente um alvo: animalId OU loteId");
                    }
                    if (temAnimal && animalRepository.buscarPorId(venda.getAnimalId()).isEmpty()) {
                        throw new IllegalArgumentException("Animal com ID " + venda.getAnimalId() + " nao existe");
                    }
                    if (temLote && loteService.buscarPorId(venda.getLoteId()).isEmpty()) {
                        throw new IllegalArgumentException("Lote com ID " + venda.getLoteId() + " nao existe");
                    }

                    vendaRepository.salvar(venda);

                    log.info("venda registrada  id={}  animal={}  lote={}  frigorifico={}",
                            venda.getId(), venda.getAnimalId(), venda.getLoteId(), venda.getFrigorifico());
                    return venda;
                });
    }

    @Transactional(readOnly = true)
    public Optional<Venda> buscarPorId(String id) {
        return vendaRepository.buscarPorId(id);
    }

    @Transactional(readOnly = true)
    public List<Venda> buscarPorAnimal(String animalId) {
        return vendaRepository.buscarPorAnimal(animalId);
    }

    @Transactional(readOnly = true)
    public List<Venda> buscarPorLote(String loteId) {
        return vendaRepository.buscarPorLote(loteId);
    }
}
