package br.pucminas.aed.pesagem.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.aed.pesagem.domain.PesagemRegistradaEvent;
import br.pucminas.aed.pesagem.service.PesagemService;

/**
 * Simula a balanca eletronica do curral publicando o evento assim que pesa
 * um animal. O corpo da requisicao E o evento — nao existe fabrica nem campo
 * calculado aqui, o mesmo espirito do servico-pedidos do demo da aula 01.
 *
 * 202, NAO 200: a API aceitou o evento para publicacao. No instante da
 * resposta o efeito de negocio (o historico de peso, no servico-manejo)
 * ainda nao aconteceu, e esse servico pode nem estar no ar.
 */
@RestController
public class PesagemController {

    private final PesagemService pesagemService;

    public PesagemController(PesagemService pesagemService) {
        this.pesagemService = pesagemService;
    }

    @PostMapping("/pesagens")
    public ResponseEntity<Void> registrarPesagem(@RequestBody PesagemRegistradaEvent evento) {
        pesagemService.publicar(evento);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
