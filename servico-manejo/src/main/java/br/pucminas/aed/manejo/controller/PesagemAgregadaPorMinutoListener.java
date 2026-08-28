package br.pucminas.aed.manejo.controller;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.pucminas.aed.manejo.domain.PesagemRegistradaEvent;

/**
 * Segundo agregador do topico de pesagem: group.id proprio
 * ("pesagem-agregador"), independente do group.id "manejo" do
 * PesagemListener — os dois recebem o stream inteiro, nenhum tira particao
 * do outro (grupos diferentes no mesmo topico nao dividem particoes entre
 * si; so consumidores do MESMO grupo dividem).
 *
 * Pergunta de negocio: peso medio do rebanho por janela de 1 minuto, para
 * acompanhar ganho/variacao de peso ao longo do dia — nao "quantos eventos
 * por minuto" (isso seria metrica de infraestrutura).
 *
 * RELOGIO ESCOLHIDO: hora de OCORRENCIA (ocorridoEm / ce_time, event time),
 * nao hora de chegada. A pesagem e lancada perto da hora real (balanca
 * eletronica), entao event time reflete quando o animal foi realmente
 * pesado — e o que faz sentido para uma curva de peso, mesmo que o
 * consumidor fique atras (reprocessamento, restart) sem distorcer a janela
 * a que aquela leitura pertence.
 *
 * JANELA ALINHADA PELO RELOGIO: Instant.truncatedTo(ChronoUnit.MINUTES) faz
 * o piso para o minuto cheio em UTC (00:00, 00:01, 00:02...), independente
 * de quando este processo subiu.
 *
 * EVENTO ATRASADO: se chega um evento cuja janela ja foi fechada (o
 * relogio de parede passou do fim da janela + TOLERANCIA_FECHAMENTO e o
 * agregado ja foi logado e descartado da memoria), o evento NAO reabre a
 * janela — e descartado do agregado com um log de aviso. Reabrir uma janela
 * ja publicada faria o mesmo minuto aparecer duas vezes no log com valores
 * diferentes, o que e pior do que aceitar a perda de uma amostra atrasada
 * demais.
 */
@Component
public class PesagemAgregadaPorMinutoListener {

    private static final Logger log = LoggerFactory.getLogger(PesagemAgregadaPorMinutoListener.class);

    // margem entre o fim "de direito" da janela (pelo relogio) e o momento em
    // que ela e efetivamente fechada e publicada no log — da tempo para
    // eventos com pequeno atraso de rede/processamento sem segurar o
    // resultado indefinidamente.
    private static final Duration TOLERANCIA_FECHAMENTO = Duration.ofSeconds(15);

    private final NavigableMap<Instant, Acumulador> janelasAbertas = new TreeMap<>();
    private Instant ultimaJanelaFechada = Instant.EPOCH;

    @KafkaListener(topics = "${demo.topico}", groupId = "pesagem-agregador",
            containerFactory = "pesagemAgregadoKafkaListenerContainerFactory")
    public synchronized void aoReceberPesagem(ConsumerRecord<String, PesagemRegistradaEvent> registro) {
        PesagemRegistradaEvent evento = registro.value();
        Instant janela = evento.getOcorridoEm().truncatedTo(ChronoUnit.MINUTES);

        if (!janela.isAfter(ultimaJanelaFechada)) {
            log.warn("pesagem atrasada descartada do agregado  particao={}  offset={}  ocorridoEm={}  " +
                            "janelaEsperada={}  ultimaJanelaFechada={}",
                    registro.partition(), registro.offset(), evento.getOcorridoEm(), janela, ultimaJanelaFechada);
            return;
        }

        janelasAbertas.computeIfAbsent(janela, j -> new Acumulador())
                .acumular(evento.getPesoKg(), registro.partition(), registro.offset());
    }

    /**
     * Fecha e publica janelas vencidas independente de chegar evento novo —
     * senao a ultima janela do dia nunca seria logada se o fluxo de
     * pesagens parasse. TreeMap em ordem crescente de inicio de janela: se a
     * mais antiga ainda nao venceu, nenhuma das seguintes venceu tambem
     * (fim de janela cresce junto com o inicio), entao da para parar no
     * primeiro "ainda nao".
     */
    @Scheduled(fixedRate = 5000)
    public synchronized void fecharJanelasVencidas() {
        Instant agora = Instant.now();
        Iterator<Map.Entry<Instant, Acumulador>> it = janelasAbertas.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Instant, Acumulador> entrada = it.next();
            Instant inicio = entrada.getKey();
            Instant fim = inicio.plus(1, ChronoUnit.MINUTES);
            if (agora.isBefore(fim.plus(TOLERANCIA_FECHAMENTO))) {
                break;
            }

            Acumulador acumulador = entrada.getValue();
            log.info("peso medio do rebanho por minuto  janela=[{}, {})  amostras={}  pesoMedioKg={}  {}",
                    inicio, fim, acumulador.getContagem(),
                    String.format("%.2f", acumulador.media()), acumulador.resumoDeOffsets());

            it.remove();
            ultimaJanelaFechada = inicio;
        }
    }

    private static final class Acumulador {
        private double soma;
        private long contagem;
        private final Map<Integer, Long> ultimoOffsetPorParticao = new HashMap<>();

        void acumular(double pesoKg, int particao, long offset) {
            soma += pesoKg;
            contagem++;
            ultimoOffsetPorParticao.merge(particao, offset, Math::max);
        }

        long getContagem() {
            return contagem;
        }

        double media() {
            return contagem == 0 ? 0.0 : soma / contagem;
        }

        String resumoDeOffsets() {
            return ultimoOffsetPorParticao.entrySet().stream()
                    .map(e -> "particao=" + e.getKey() + ":offset=" + e.getValue())
                    .collect(Collectors.joining(", "));
        }
    }
}
