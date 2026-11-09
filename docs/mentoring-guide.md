# Guia de condução da mentoria

Este guia ajuda mentor e participantes a transformar as specs em código. O repositório ainda é documental: nenhum comando de build, teste ou demo do produto está verificado. As regras da [Spec 001](../specs/001-ai-assisted-request-triage/spec.md) e da [Spec 002](../specs/002-service-workflow/spec.md) são propostas para revisão dos participantes.

## A história que o produto precisa contar

1. Uma pessoa autenticada abre um pedido sintético no próprio workspace.
2. O agente de IA recebe primeiro: com informação suficiente, sugere classificação e encaminha ao humano; se faltar contexto, pede um complemento ao solicitante.
3. A pessoa agente revisa a sugestão ou classifica manualmente quando não há sugestão. A classificação humana abre o atendimento. Agente confirma impacto e urgência, gerando prioridade e prazo.
4. Um agente assume o pedido. Se precisa de informação, pergunta ao solicitante e pausa o prazo.
5. Solicitante responde, atendimento continua e o agente resolve. Uma contestação dentro da janela abre novo ciclo sem apagar o anterior.
6. Agente pode relacionar um segundo relato ao pedido canônico, sem mesclar os dados nem expor conteúdo entre solicitantes.

Use essa história como demo progressiva. Cada PR acrescenta uma parte comprovável; nenhum PR precisa construir tudo.

## Como conduzir cada encontro

| Momento | Pergunta do mentor | Entrega dos participantes |
| --- | --- | --- |
| Escolha | Qual comportamento caberá até o próximo encontro? | Uma tarefa e um cenário observável |
| Regra | Quem pode agir? Qual estado e versão são exigidos? | Spec/contrato ajustados, se necessário |
| Falha | O que ocorre com duplicação, concorrência, outro workspace ou dependência fora? | Pelo menos um cenário negativo |
| Implementação | Qual parte é domínio e qual é adaptador? | Código e testes proporcionais à regra |
| Revisão | O teste prova a regra ou só repete o código? | PR revisto por outra pessoa |
| Demo | Outra pessoa consegue reproduzir? | Comandos executados, resultado e limitação registrados |

Uma pessoa é driver, outra reviewer; inverter no PR seguinte. Cada participante deve ter PRs próprios de backend e frontend e revisões de ambos. IA pode auxiliar na escrita, busca e análise, mas quem entrega explica o fluxo, a decisão e os limites do teste.

## Sequência sugerida

| Incremento | Resultado demonstrável | Pergunta técnica principal |
| --- | --- | --- |
| 0. Contrato | Estados, atores e casos sintéticos revisados | Onde a regra ainda é ambígua? |
| 1. Esqueleto | Clone limpo, build, teste e health da API | O ambiente é reproduzível? |
| 2. Domínio | Transições e classificação manual testadas sem infraestrutura | O que o domínio impede? |
| 3. Acesso e dados | OIDC, membership, PostgreSQL, criação e leitura isolada | O ID da rota autoriza alguma coisa? |
| 4. Atendimento | Prioridade, prazo, responsável e resolução | Como tempo e concorrência afetam o pedido? |
| 5. Espera e contestação | Pausar, responder, resolver e reabrir em novo ciclo | O histórico permanece íntegro? |
| 6. Assíncrono | Outbox, RabbitMQ, worker e fake de IA que roteia humano/complemento | O que acontece se IA pedir contexto, falhar ou ficar incerta? |
| 7. Avançado | Duplicados, custo/avaliação de IA, operação | Como provar isolamento e recuperação? |

A ordem pode mudar se dependências técnicas exigirem. O primeiro incremento da Spec 002 requer identidade, criação, classificação manual e auditoria; pode ser feito antes do worker de IA. A demo final combina as capacidades sem chamar serviço real por padrão.

## Exemplo completo de spec-driven development

**Regra:** `FR2-001` diz que dois agentes não podem assumir simultaneamente o mesmo pedido.

**Cenários:**

- Dado pedido `OPEN` sem responsável e versão 5, quando agente A assume com versão 5, então responsável vira A, versão avança e auditoria registra A.
- Dado o mesmo pedido e a versão 5 antiga, quando agente B tenta assumir, então recebe `409 VERSION_CONFLICT` e não cria outra auditoria.
- Dado agente de outro workspace, quando tenta assumir pelo ID, então recebe `404` e não aprende dados do pedido.

**Código esperado:** método de domínio que valida estado, caso de uso que verifica membership, `UPDATE` condicional por versão e auditoria na mesma transação, endpoint que mapeia erros seguros. **Evidência:** teste de domínio, integração concorrente com PostgreSQL, teste negativo de workspace e demo de duas chamadas. Um teste que apenas chama o método uma vez não prova a disputa.

## Antes de abrir PR

Verificar se o PR aponta IDs de requisitos/tarefas, mostra decisões e alternativas, lista comandos e resultados executados, inclui autorização/falha pertinentes, atualiza contrato/OpenAPI e README quando necessário, usa somente dados sintéticos e declara limites. Não marcar spec ou tarefa como aceita por documentação ou CI verde sem demonstração do comportamento correspondente.
