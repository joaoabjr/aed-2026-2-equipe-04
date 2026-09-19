package br.pucminas.aed.manejo.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.aed.manejo.domain.EventoDlqVO;
import br.pucminas.aed.manejo.service.DlqService;

/**
 * Leitura da DLQ (Dead Letter Queue) permanente do servico-manejo. Nao ha
 * endpoint de escrita nem de limpeza: o acumulo e' proposital — o que falhou
 * fica registrado para auditoria e reprocessamento manual, e o fluxo normal
 * jamais apaga a DLQ (regra do ADR-002: o sistema nao corrige o passado).
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
}