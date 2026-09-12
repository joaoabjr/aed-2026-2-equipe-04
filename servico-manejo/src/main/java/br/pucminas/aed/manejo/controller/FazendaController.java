package br.pucminas.aed.manejo.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import br.pucminas.aed.manejo.domain.Fazenda;
import br.pucminas.aed.manejo.service.FazendaService;

import java.util.Optional;

@RestController
@RequestMapping("/api/fazendas")
public class FazendaController {

    private static final Logger log = LoggerFactory.getLogger(FazendaController.class);

    private final FazendaService fazendaService;

    public FazendaController(FazendaService fazendaService) {
        this.fazendaService = fazendaService;
    }

    @PostMapping
    public ResponseEntity<Fazenda> registrar(@Valid @RequestBody Fazenda fazenda) {
        log.info("Recebida requisicao para registrar fazenda: {}", fazenda.getId());

        fazendaService.registrar(fazenda);

        log.info("Fazenda registrada com sucesso: {}", fazenda.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(fazenda);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Fazenda> buscarFazenda(@PathVariable String id) {
        log.info("Buscando informacoes sobre a fazenda de ID: {}", id);
        return fazendaService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}