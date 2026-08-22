package br.pucminas.aed.vacinacao.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.aed.vacinacao.domain.VacinacaoRegistradaEvent;
import br.pucminas.aed.vacinacao.service.VacinacaoService;


@RestController
public class VacinacaoController {

    private final VacinacaoService vacinacaoService;

    public VacinacaoController(VacinacaoService vacinacaoService) {
        this.vacinacaoService = vacinacaoService;
    }

    @PostMapping("/vacinacao")
    public ResponseEntity<Void> registrarVacinacao(@RequestBody VacinacaoRegistradaEvent evento) {
        vacinacaoService.publicar(evento);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
