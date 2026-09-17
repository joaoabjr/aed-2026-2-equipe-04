package br.pucminas.aed.manejo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.pucminas.aed.manejo.domain.Animal;
import br.pucminas.aed.manejo.domain.AnimalRejeitadoNoEmbarqueEvent;
import br.pucminas.aed.manejo.domain.Venda;
import br.pucminas.aed.manejo.repository.AnimalRepository;
import br.pucminas.aed.manejo.repository.VendaRepository;

/**
 * O caminho de exceção do domínio (ADR-002): quando o Sistema do Frigorífico
 * recusa o animal na triagem, esta e' a compensação permanente disparada no
 * servico-manejo — não uma reversão temporária, mas o NOVO estado vigente
 * do animal, valido até a próxima decisão de manejo.
 *
 * Três efeitos, dentro da MESMA transação do dedup (mesmo desenho de
 * HistoricoPesagemService — o offset só confirma, no listener, depois do
 * commit):
 *
 *   1. o animal permanece no/retorna ao lote de origem — como nenhum fluxo
 *      deste sistema jamais desassocia um animal individual do seu lote so'
 *      por entrar em processo de venda (so' o agregado Lote tem
 *      movimentação própria, e e' de pasto — ADR-005), "retornar ao lote de
 *      origem" e' reafirmar o {@code lote_id} que o animal ja tem, nao
 *      mover nada;
 *   2. a dieta e' reavaliada a partir do motivo da rejeição — estado
 *      vigente novo, gravado em {@code animal.dieta_atual};
 *   3. a tentativa de venda que não se concretizou e' encerrada — toda
 *      venda ainda aberta (nao encerrada) que referencia este animalId.
 */
@Service
public class CompensacaoEmbarqueService {

    private static final Logger log = LoggerFactory.getLogger(CompensacaoEmbarqueService.class);

    private static final String MOTIVO_PESO_INSUFICIENTE = "PESO_INSUFICIENTE";
    private static final String DIETA_REFORCO_ENERGETICO = "REFORCO_ENERGETICO";
    private static final String DIETA_MANUTENCAO = "MANUTENCAO";

    private final CompensacaoEmbarqueRepository repositorio;
    private final AnimalRepository animalRepository;
    private final VendaRepository vendaRepository;

    public CompensacaoEmbarqueService(CompensacaoEmbarqueRepository repositorio,
                                      AnimalRepository animalRepository,
                                      VendaRepository vendaRepository) {
        this.repositorio = repositorio;
        this.animalRepository = animalRepository;
        this.vendaRepository = vendaRepository;
    }

    /**
     * @return true se o efeito de negocio foi aplicado; false se era duplicata.
     */
    @Transactional
    public boolean processar(String eventoId, AnimalRejeitadoNoEmbarqueEvent evento) {

        boolean primeiraVez = repositorio.registrarEventoSeNovo(eventoId);
        if (!primeiraVez) {
            log.info("evento {} JA PROCESSADO, descartando em silencio", eventoId);
            return false;
        }

        Animal animal = animalRepository.buscarPorId(evento.getAnimalId())
                .orElseThrow(() -> new IllegalArgumentException("Animal com ID " + evento.getAnimalId() + " nao existe"));

        String dietaReavaliada = reavaliarDieta(evento.getMotivoRejeicao());
        animalRepository.atualizarDieta(animal.getId(), dietaReavaliada);

        int vendasEncerradas = 0;
        for (Venda venda : vendaRepository.buscarPorAnimal(evento.getAnimalId())) {
            vendaRepository.deletar(venda.getId());
            vendasEncerradas++;
        }

        log.info("rejeicao de embarque compensada  evento={}  animal={}  loteOrigem={}  motivo={}  dieta={}  vendasEncerradas={}",
                eventoId, animal.getId(), animal.getLote().getId(), evento.getMotivoRejeicao(), dietaReavaliada, vendasEncerradas);
        return true;
    }

    /**
     * Regra deterministica e deliberadamente simples: o motivo da rejeição
     * decide a dieta, sem envolver um zootecnista humano nesta etapa (fora
     * de escopo — ver docs/contrato-rejeicao-embarque.md). PESO_INSUFICIENTE
     * pede mais energia para o animal atingir a meta na proxima tentativa;
     * qualquer outro motivo (ex. VACINACAO_VENCIDA, que nada tem a ver com
     * peso) so' confirma a dieta em manutencao.
     */
    private String reavaliarDieta(String motivoRejeicao) {
        if (MOTIVO_PESO_INSUFICIENTE.equalsIgnoreCase(motivoRejeicao)) {
            return DIETA_REFORCO_ENERGETICO;
        }
        return DIETA_MANUTENCAO;
    }
}
