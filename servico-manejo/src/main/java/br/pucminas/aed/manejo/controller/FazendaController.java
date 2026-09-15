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
        boolean jaExistia = fazendaService.buscarPorId(fazenda.getId()).isPresent();
        Fazenda resultado = fazendaService.registrar(fazenda);

        HttpStatus status = jaExistia ? HttpStatus.OK : HttpStatus.CREATED;
        log.info("Fazenda {}  id={}", jaExistia ? "ja existia (reenvio idempotente)" : "registrada com sucesso", resultado.getId());
        return ResponseEntity.status(status).body(resultado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Fazenda> buscarFazenda(@PathVariable String id) {
        log.info("Buscando informacoes sobre a fazenda de ID: {}", id);
        return fazendaService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}