package br.pucminas.aed.manejo.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.aed.manejo.domain.LoteMovidoDePastoEvent;
import br.pucminas.aed.manejo.service.ManejoPastoService;

@RestController
@RequestMapping("/api/pastos")
public class PastoController {

    private final ManejoPastoService manejoPastoService;

    public PastoController(ManejoPastoService manejoPastoService) {
        this.manejoPastoService = manejoPastoService;
    }

    @PostMapping("/movimentacoes")
    public ResponseEntity<Void> moverLote(@Valid @RequestBody LoteMovidoDePastoEvent evento) {
        manejoPastoService.mover(evento);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
