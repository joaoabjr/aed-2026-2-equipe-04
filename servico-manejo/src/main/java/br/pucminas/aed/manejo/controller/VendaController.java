package br.pucminas.aed.manejo.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import br.pucminas.aed.manejo.domain.Venda;
import br.pucminas.aed.manejo.service.VendaService;

import java.util.List;

@RestController
@RequestMapping("/api/vendas")
public class VendaController {

    private static final Logger log = LoggerFactory.getLogger(VendaController.class);

    private final VendaService vendaService;

    public VendaController(VendaService vendaService) {
        this.vendaService = vendaService;
    }

    @PostMapping
    public ResponseEntity<Venda> registrar(@Valid @RequestBody Venda venda) {
        boolean jaExistia = vendaService.buscarPorId(venda.getId()).isPresent();
        Venda resultado = vendaService.registrar(venda);

        HttpStatus status = jaExistia ? HttpStatus.OK : HttpStatus.CREATED;
        log.info("Venda {}  id={}", jaExistia ? "ja existia (reenvio idempotente)" : "registrada com sucesso", resultado.getId());
        return ResponseEntity.status(status).body(resultado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Venda> buscarVenda(@PathVariable String id) {
        log.info("Buscando informacoes sobre a venda de ID: {}", id);
        return vendaService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/animal/{animalId}")
    public ResponseEntity<List<Venda>> buscarVendasPorAnimal(@PathVariable String animalId) {
        log.info("Buscando vendas do animal: {}", animalId);
        return ResponseEntity.ok(vendaService.buscarPorAnimal(animalId));
    }

    @GetMapping("/lote/{loteId}")
    public ResponseEntity<List<Venda>> buscarVendasPorLote(@PathVariable String loteId) {
        log.info("Buscando vendas do lote: {}", loteId);
        return ResponseEntity.ok(vendaService.buscarPorLote(loteId));
    }
}
