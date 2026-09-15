package br.pucminas.aed.expedicao.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.aed.expedicao.domain.AnimalEmbarcadoParaAbateEvent;
import br.pucminas.aed.expedicao.service.ExpedicaoService;

@RestController
public class ExpedicaoController {

    private final ExpedicaoService expedicaoService;

    public ExpedicaoController(ExpedicaoService expedicaoService) {
        this.expedicaoService = expedicaoService;
    }

    @PostMapping("/embarques")
    public ResponseEntity<Void> registrarEmbarque(@RequestBody AnimalEmbarcadoParaAbateEvent evento) {
        expedicaoService.publicar(evento);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
