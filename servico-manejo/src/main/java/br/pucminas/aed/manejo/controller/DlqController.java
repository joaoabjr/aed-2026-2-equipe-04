package br.pucminas.aed.manejo.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.aed.manejo.domain.EventoDlqVO;
import br.pucminas.aed.manejo.service.DlqService;

/**
 * Leitura da DLQ (Dead Letter Queue) permanente do servico-manejo, e o
 * caminho MANUAL de reprocessamento (Parte A do projeto final). Nao ha
 * endpoint de limpeza nem de edicao: o acumulo em evento_dlq e' proposital
 * -- o que falhou fica registrado para auditoria, e o fluxo normal jamais
 * apaga a DLQ (regra do ADR-002: o sistema nao corrige o passado). Reprocessar
 * NAO apaga nem altera a linha da DLQ -- so' reenvia o evento ao topico
 * original e registra a tentativa em `dlq_reprocessamento`.
 */
@RestController
@RequestMapping("/api/dlq")
public class DlqController {

    private final DlqService dlqService;

    public DlqController(DlqService dlqService) {
        this.dlqService = dlqService;
    }

    @GetMapping
    public ResponseEntity<List<EventoDlqVO>> listar() {
        return ResponseEntity.ok(dlqService.listar());
    }

    @PostMapping("/{id}/reprocessar")
    public ResponseEntity<Void> reprocessar(@PathVariable long id) {
        dlqService.reprocessar(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}