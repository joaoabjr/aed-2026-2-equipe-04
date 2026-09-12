package br.pucminas.aed.manejo.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import br.pucminas.aed.manejo.domain.Animal;
import br.pucminas.aed.manejo.service.AnimalService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/animais")
public class AnimalController {

    private static final Logger log = LoggerFactory.getLogger(AnimalController.class);

    private final AnimalService animalService;

    public AnimalController(AnimalService animalService) {
        this.animalService = animalService;
    }

    @PostMapping
    public ResponseEntity<Animal> registrar(@Valid @RequestBody Animal animal) {
        log.info("Recebida requisicao para registrar animal: {}", animal.getId());

        Animal salvo = animalService.registrar(animal);

        log.info("Animal registrado com sucesso: {}", salvo.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(salvo);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Animal> buscarAnimal(@PathVariable String id) {
        log.info("Buscando informacoes sobre o animal de ID: {}", id);
        return animalService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/lote/{loteId}")
    public ResponseEntity<List<Animal>> buscarAnimaisPorLote(@PathVariable String loteId) {
        log.info("Buscando animais do lote: {}", loteId);
        List<Animal> animais = animalService.buscarPorLote(loteId);
        return ResponseEntity.ok(animais);
    }
}