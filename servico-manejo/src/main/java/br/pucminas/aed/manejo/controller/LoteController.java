package br.pucminas.aed.manejo.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import br.pucminas.aed.manejo.domain.Lote;
import br.pucminas.aed.manejo.service.LoteService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/lotes")
public class LoteController {

    private static final Logger log = LoggerFactory.getLogger(LoteController.class);

    private final LoteService loteService;

    public LoteController(LoteService loteService) {
        this.loteService = loteService;
    }

    @PostMapping
    public ResponseEntity<Lote> registrar(@Valid @RequestBody Lote lote) {
        log.info("Recebida requisicao para registrar lote: {}", lote.getId());

        loteService.registrar(lote);

        log.info("Lote registrado com sucesso: {}", lote.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(lote);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Lote> buscarLote(@PathVariable String id) {
        log.info("Buscando informacoes sobre o lote de ID: {}", id);
        return loteService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/fazenda/{fazendaId}")
    public ResponseEntity<List<Lote>> buscarLotesPorFazenda(@PathVariable String fazendaId) {
        log.info("Buscando lotes da fazenda: {}", fazendaId);
        List<Lote> lotes = loteService.buscarPorFazenda(fazendaId);
        return ResponseEntity.ok(lotes);
    }
}