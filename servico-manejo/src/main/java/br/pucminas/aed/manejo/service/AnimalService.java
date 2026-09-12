package br.pucminas.aed.manejo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.aed.manejo.domain.Animal;
import br.pucminas.aed.manejo.domain.Lote;
import br.pucminas.aed.manejo.repository.AnimalRepository;

import java.util.List;
import java.util.Optional;

@Service
public class AnimalService {

    private static final Logger log = LoggerFactory.getLogger(AnimalService.class);

    private final AnimalRepository animalRepository;
    private final LoteService loteService;

    public AnimalService(AnimalRepository animalRepository, LoteService loteService) {
        this.animalRepository = animalRepository;
        this.loteService = loteService;
    }

    @Transactional
    public Animal registrar(Animal animal) {
        if (animalRepository.existePorId(animal.getId())) {
            throw new IllegalArgumentException("Animal com ID " + animal.getId() + " ja existe");
        }
        if (loteService.buscarPorId(animal.getLote().getId()).isEmpty()) {
            throw new IllegalArgumentException("Lote com ID " + animal.getLote().getId() + " nao existe");
        }

        animalRepository.salvar(animal);

        log.info("animal registrado  id={}  nome={}  raca={}  idade={}  lote={}",
                animal.getId(), animal.getNomeDoAnimal(), animal.getRaca(), animal.getIdade(), animal.getLote().getId());
        return animal;
    }

    @Transactional(readOnly = true)
    public Optional<Animal> buscarPorId(String id) {
        return animalRepository.buscarPorId(id);
    }

    @Transactional(readOnly = true)
    public List<Animal> buscarPorLote(String loteId) {
        return animalRepository.buscarPorLote(loteId);
    }
}