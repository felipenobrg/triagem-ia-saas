# Tarefas — Spec 002

> **Nada desta spec foi implementado.** Ver [estado da implementação](../../docs/implementation-status.md).

> **Para estudar:** estas tarefas só começam depois de identidade, criação, classificação manual e auditoria existirem. Cada item pede um comportamento observável e um teste que outra pessoa consiga repetir; marcar caixa sem evidência não conclui a atividade.

Todas as tarefas estão **planejadas**. Os IDs de requisitos referem-se a [`spec.md`](spec.md).

## M0 — Revisão do produto

- [ ] **T2001** Revisar com a dupla matriz de prioridade, prazos por prioridade, calendário local e janela de reabertura; registrar na spec qualquer alteração antes de implementar. `FR2-003`, `FR2-005`, `FR2-007`
- [ ] **T2002** Aprovar as rotas, códigos de erro e campos novos do [contrato](api-contract.md), incluindo o que o requester nunca pode ver. `FR2-009`, `NFR2-003`
- [ ] **T2003** Escrever a tabela de casos de relógio (abertura fora do horário, sexta para segunda, pausa, troca de prioridade, resolução após vencimento) antes do código de cálculo. `FR2-005`, `NFR2-002`

## M1 — Prioridade, ciclo e prazo

- [ ] **T2101** Implementar impacto informado e confirmado e bloquear entrada em `OPEN` sem impacto confirmado. `FR2-003`
- [ ] **T2102** Implementar o cálculo puro de tempo útil com `Clock` injetável e a tabela de testes de T2003. `FR2-005`, `NFR2-002`
- [ ] **T2103** Criar ciclo de atendimento com trechos de prioridade, consumo e `breachedAt` derivável; recalcular na troca de prioridade sem reiniciar o relógio. `FR2-004`–`FR2-005`, `BR2-004`
- [ ] **T2104** Implementar override de prioridade com motivo obrigatório, registrando valor calculado e efetivo. `FR2-003`, `BR2-004`

## M2 — Atribuição e atendimento

- [ ] **T2201** Implementar assumir, transferir e desatribuir com `expectedVersion`, autorização por papel e auditoria. `FR2-001`–`FR2-002`
- [ ] **T2202** Demonstrar a disputa de atribuição com PostgreSQL real: duas tentativas na mesma versão, uma vence, a outra recebe conflito e o histórico registra uma única atribuição. `FR2-001`, `NFR2-001`
- [ ] **T2203** Desativar ou rebaixar membership desatribuindo pedidos ativos na mesma transação, mantendo a regra de último admin. `FR2-002`, `BR2-002`
- [ ] **T2204** Implementar pausa com pergunta, resposta apenas do autor, cancelamento por admin e encerramento exatamente uma vez. `FR2-006`, `BR2-005`
- [ ] **T2205** Implementar resolução com nota e reabertura dentro da janela de 7 dias, criando novo ciclo e preservando os anteriores. `FR2-007`, `BR2-004`

## M3 — Vínculos, fila e operação

- [ ] **T2301** Implementar vínculo e desvínculo de duplicados com as duas versões, ordem estável de lock e rejeição de cadeia, auto-vínculo e cross-workspace. `FR2-008`, `BR2-007`
- [ ] **T2302** Implementar filtros da fila por responsável, prioridade, prazo vencido e estado, com `overdue` derivado do consumo. `FR2-009`, `BR2-006`
- [ ] **T2303** Expor histórico paginado com atribuições, prioridades, pausas, ciclos, reabertura e vínculos, sem revelar pedido alheio. `FR2-009`, `NFR2-003`–`NFR2-004`
- [ ] **T2304** Criar a interface mínima com responsável, prioridade, prazo ou minutos restantes, motivo de espera e histórico. `FR2-009`
- [ ] **T2305** Demonstrar que decisão humana vence resultado tardio de IA: estado, responsável, prioridade e prazo não mudam. `BR2-003`
- [ ] **T2306** Executar e registrar os cenários de aceite 1 a 10 da spec, com saída real dos testes. `NFR2-001`–`NFR2-004`

## Pronto para marcar tarefa como concluída

- Requisito `FR2-*`/`BR2-*`/`NFR2-*` vinculado e atendido.
- Teste de concorrência ou de relógio pertinente **executado**, com saída registrada no PR.
- Contrato, histórico e documentação coerentes com o código.
- Revisão por outra pessoa, com riscos e limitações declarados. Nenhum valor de prazo é apresentado como SLA real.
