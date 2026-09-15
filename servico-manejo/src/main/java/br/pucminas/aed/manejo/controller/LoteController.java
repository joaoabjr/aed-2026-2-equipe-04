package br.pucminas.aed.manejo.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import br.pucminas.aed.manejo.domain.Lote;
import br.pucminas.aed.manejo.domain.LoteFormadoEvent;
import br.pucminas.aed.manejo.service.LoteFormacaoService;
import br.pucminas.aed.manejo.service.LoteService;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/lotes")
public class LoteController {

    private static final Logger log = LoggerFactory.getLogger(LoteController.class);

    private final LoteService loteService;
    private final LoteFormacaoService loteFormacaoService;

    public LoteController(LoteService loteService, LoteFormacaoService loteFormacaoService) {
        this.loteService = loteService;
        this.loteFormacaoService = loteFormacaoService;
    }

    @PostMapping
    public ResponseEntity<Lote> registrar(@Valid @RequestBody Lote lote) {
        boolean jaExistia = loteService.buscarPorId(lote.getId()).isPresent();
        Lote resultado = loteService.registrar(lote);

        HttpStatus status = jaExistia ? HttpStatus.OK : HttpStatus.CREATED;
        log.info("Lote {}  id={}", jaExistia ? "ja existia (reenvio idempotente)" : "registrado com sucesso", resultado.getId());
        return ResponseEntity.status(status).body(resultado);
    }

    @PostMapping("/formacoes")
    public ResponseEntity<Void> formar(@Valid @RequestBody LoteFormadoEvent evento) {
        loteFormacaoService.formar(evento);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
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
