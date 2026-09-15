package br.pucminas.aed.manejo.controller;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.aed.manejo.service.LoteLocalizacaoService;

/**
 * Leitura da projecao (ADR-005) e o caminho manual de reprocessamento: se a
 * projecao sair de sincronia com o event store por qualquer motivo, este e'
 * o endpoint que refaz o replay — nao existe reconstrucao automatica.
 */
@RestController
@RequestMapping("/api/lotes/{loteId}/localizacao")
public class LoteLocalizacaoController {

    private final LoteLocalizacaoService loteLocalizacaoService;

    public LoteLocalizacaoController(LoteLocalizacaoService loteLocalizacaoService) {
        this.loteLocalizacaoService = loteLocalizacaoService;
    }

    @GetMapping
    public ResponseEntity<String> localizacaoAtual(@PathVariable String loteId) {
        Optional<String> pastoAtual = loteLocalizacaoService.localizacaoAtual(loteId);
        return pastoAtual.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping("/reconstruir")
    public ResponseEntity<Void> reconstruir(@PathVariable String loteId) {
        loteLocalizacaoService.reconstruirProjecao(loteId);
        return ResponseEntity.ok().build();
    }
}
