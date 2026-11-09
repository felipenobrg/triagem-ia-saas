# Tarefas — Spec 002

Todas estão planejadas. A Spec 001 fornece a base; nenhuma tarefa abaixo é evidência de implementação. Cada PR informa requisito, cenário, teste executado, demo e limite conhecido. Alternar driver/reviewer.

## M0 — Revisão do produto

- [ ] **T2-001** Revisar matriz, calendário, prazos, janela de reabertura e permissões com participantes; registrar decisão na spec. `FR2-001–009`, `BR2-001–007`.
- [ ] **T2-002** Publicar OpenAPI versionada e migração aditiva alinhadas ao contrato; testar upgrade de fixture da Spec 001. `NFR2-003–004`.

## M1 — Regras testáveis e primeiro fluxo

- [ ] **T2-101** Implementar matriz de prioridade e `BusinessClock` com testes tabulares, incluindo sexta→segunda e pausa. `FR2-003`, `FR2-005`, `NFR2-002`.
- [ ] **T2-102** Confirmar impacto na classificação humana e criar ciclo/prazo atomicamente; UI mostra prioridade e prazo. `FR2-003`, `FR2-005`.
- [ ] **T2-103** Assumir pedido com versão otimista, autorização e histórico; dois agentes concorrentes, um vencedor. `FR2-001`, `NFR2-001`, `NFR2-004`.
- [ ] **T2-104** Iniciar atendimento, resolver com nota e fechar ciclo; demonstrar prazo vencido e não vencido. `FR2-007`, `NFR2-002`.

## M2 — Atendimento completo

- [ ] **T2-201** Transferir/desatribuir e desativar membro na mesma transação; testar último admin e vários pedidos atribuídos. `FR2-002`, `BR2-002`, `NFR2-001`.
- [ ] **T2-202** Atualizar impacto/urgência/override com motivo e recalcular prazo preservando consumo/violação. `FR2-004`, `BR2-004`, `NFR2-002`.
- [ ] **T2-203** Perguntar, pausar, responder/cancelar espera e retomar; testar requester errado, resposta tardia, repetição e limites de texto. `FR2-006`, `NFR2-002–003`.
- [ ] **T2-204** Reabrir dentro de sete dias, criar novo ciclo e preservar histórico; testar limite temporal exato e responsável inativo. `FR2-007`, `NFR2-002`.
- [ ] **T2-205** Testar resultado de IA tardio após classificação manual e impedir regressão de estado/prazo. `BR2-003`, `NFR2-001`.

## M3 — Vínculos e operação

- [ ] **T2-301** Vincular/desvincular duplicados com locks ordenados, versões, FK tenant-aware e histórico; testar ciclo/cadeia/concorrência. `FR2-008`, `NFR2-001`, `NFR2-003`.
- [ ] **T2-302** Filtros e detalhes por papel sem vazamento de pedido alheio, inclusive cursor. `FR2-009`, `NFR2-003`.
- [ ] **T2-303** Demo em clone limpo: criar → classificar → assumir → esperar → responder → resolver → reabrir; outro pedido duplicado; mostrar logs sem texto livre e testes. `NFR2-001–004`.

## Pronto para marcar tarefa como concluída

Spec/contrato/OpenAPI/código concordam; testes de regra e de falha aplicáveis foram executados; migração e autorização foram verificadas; PR documenta resultados reais, decisão, contribuição individual e limitações. Uma tarefa planejada não vira aceita por ter apenas documentação.
