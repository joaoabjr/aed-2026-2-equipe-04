package br.pucminas.aed.expedicao.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.pucminas.aed.expedicao.domain.AnimalEmbarcadoParaAbateEvent;
import br.pucminas.aed.expedicao.domain.AnimalRejeitadoNoEmbarqueEvent;
import br.pucminas.aed.expedicao.service.ExpedicaoService;
import br.pucminas.aed.expedicao.service.RejeicaoEmbarqueService;

@RestController
public class ExpedicaoController {

    private final ExpedicaoService expedicaoService;
    private final RejeicaoEmbarqueService rejeicaoEmbarqueService;

    public ExpedicaoController(ExpedicaoService expedicaoService, RejeicaoEmbarqueService rejeicaoEmbarqueService) {
        this.expedicaoService = expedicaoService;
        this.rejeicaoEmbarqueService = rejeicaoEmbarqueService;
    }

    @PostMapping("/embarques")
    public ResponseEntity<Void> registrarEmbarque(@RequestBody AnimalEmbarcadoParaAbateEvent evento) {
        expedicaoService.publicar(evento);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * O caminho de exceção (ADR-002): o Sistema do Frigorífico recusou o
     * animal na triagem de recebimento. A recusa é registrada de forma
     * definitiva — não é apagada nem reenviada — e dispara, do lado do
     * servico-manejo, a compensação permanente descrita no ADR.
     */
    @PostMapping("/rejeicoes")
    public ResponseEntity<Void> registrarRejeicao(@RequestBody AnimalRejeitadoNoEmbarqueEvent evento) {
        rejeicaoEmbarqueService.publicar(evento);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
