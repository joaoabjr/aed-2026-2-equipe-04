#ADR-002 — Domínio do projeto

## Status

Aceita · 2026-08-16 · Equipe 04

## Contexto

O problema escolhido é a coordenação de eventos entre os setores de uma operação de gado de corte em confinamento — hoje um conjunto de planilhas e conversas por telefone entre recepção, zootecnia, sanidade, comercial e expedição, sem um registro único e ordenado do que aconteceu com cada animal. Isso importa agora porque decisões de venda e embarque dependem de informação que está espalhada (peso mais recente, status vacinal, lote atual) e cada atraso ou erro de comunicação vira animal parado a mais no confinamento ou embarque recusado no frigorífico — custo direto, não abstrato.

O domínio foi trazido por um membro da equipe com vivência prática em fazenda de confinamento/recria, que acompanhou de perto a rotina de pesagem, formação de lotes e negociação com frigorífico — não é um domínio escolhido por afinidade acadêmica, é um processo que a pessoa já viu falhar por falta de rastreabilidade entre as etapas.

## Decisão

O processo escolhido vai do cadastro do animal na fazenda até o embarque para o frigorífico.

Como ele atende cada um dos quatro critérios:

- ponto de decisão com regra de negócio: o embarque só é confirmado se o peso do animal atingir a meta mínima acordada em contrato — é a expedição, e não o comercial, quem decide embarcar ou não com base nesse número.
- sistema externo: o Sistema do Frigorífico, que recebe o romaneio de carga e pode recusar o animal na triagem de recebimento, fora do nosso controle de deploy.
- caminho de exceção com compensação: AnimalRejeitadoNoEmbarque — quando o frigorífico recusa, o animal retorna ao lote de origem e a dieta é reavaliada, compensando a tentativa de venda que não se concretizou.
- algo que valha reprocessar: PesagemRegistrada — se o serviço de pesagem cair ou o broker atrasar, o histórico de peso precisa ser reprocessável sem duplicar leituras, porque é o dado que sustenta a decisão de formar lote e de embarcar.

## Alternativas consideradas

Processo de vacinação e sanidade isolado — trazido como candidato por ser mais simples de modelar, mas recusado porque tem só um sistema externo (emissor de GTA) e nenhum ponto de decisão de negócio interessante além de "aplicar ou não"; não sustentaria os quatro critérios com profundidade suficiente para as próximas aulas.

Processo de compra de insumos (ração, medicamentos) — descartado porque é essencialmente um fluxo de aprovação financeira, sem caminho de exceção característico do domínio pecuário e sem volume que justifique mensageria — o ganho de modelar como eventos seria pequeno perto do custo de implementar.

Processo de reprodução/manejo reprodutivo do rebanho — descartado por falta de experiência real de algum membro da equipe nesse subdomínio; modelar sem vivência prática arriscava reproduzir suposições erradas sobre as regras de negócio, o problema que o domínio de gado de corte pelo lado de engorda evita.

## Consequencias aceitas

Esta decisão nos custa escopo: ficam de fora do projeto o manejo reprodutivo, a genética do rebanho e a gestão de insumos/ração — o domínio modelado é só o ciclo comercial do animal, do cadastro ao embarque, não a fazenda inteira.

Também aceitamos que o processo tem uma dependência forte de um sistema de terceiro (Frigorífico) sobre o qual não temos controle de disponibilidade nem de contrato de API documentado.
