# Tarefas — Spec 003

> **Nenhuma caixa abaixo foi marcada.** As fatias M1, M2 (sem outbox) e M3 (com avaliação síncrona) já têm código e testes em `backend/`; os desvios estão declarados em [estado da implementação](../../docs/implementation-status.md). Marcar uma tarefa exige revisão independente e demonstração por outra pessoa.

> **Para estudar:** comece pelas fatias sem IA real. Uma tarefa de conversa só está pronta quando alguém consegue repetir o fluxo em clone limpo e quando existe um teste mostrando o que acontece na falha do provedor. Caixa marcada sem evidência não conclui a atividade.

Todas as tarefas estão **planejadas**. Os IDs de requisitos referem-se a [`spec.md`](spec.md). Esta spec depende de identidade, auditoria, concorrência, outbox e fila humana da [Spec 001](../001-ai-assisted-request-triage/tasks.md) (até T304 e T402, ao menos no caminho manual).

## M0 — Decisões antes do código

- [ ] **T3001** Revisar com a dupla: limite de 10 turnos, limiar de confiança 0,70, janela de inatividade de 72 h, janela de reabertura de 7 dias e custo máximo por conversa. Registrar as escolhas na spec. `FR3-003`, `FR3-006`, `FR3-010`, `FR3-021`
- [ ] **T3002** Decidir e registrar quem gera o contexto do problema (agente de conversa ou JEV) e com qual schema. `FR3-015`
- [ ] **T3003** Aprovar o [contrato](api-contract.md): rotas, códigos de erro, campos do chamado com origem conversa e o que nunca aparece na resposta. `FR3-018`, `NFR3-005`
- [ ] **T3004** Montar o conjunto sintético inicial de conversas (resolvíveis, não resolvíveis e ambíguas), com rótulo de desfecho esperado e rubrica de avaliação. Sem meta antes do baseline. `FR3-023`, `NFR3-006`
- [ ] **T3005** Escrever os casos de injeção de prompt que serão testados (pedido para marcar como resolvido, pedido de credencial, tentativa de mudar workspace). `BR3-011`

## M1 — Conversa sem IA

- [ ] **T3101** Criar o módulo `conversations` com aggregate de conversa, turnos imutáveis, sequência única e versão otimista; testar invariantes sem infraestrutura. `FR3-001`–`FR3-002`, `NFR3-003`
- [ ] **T3102** Persistência Flyway tenant-aware para conversa, turno, veredito e contexto, com índice parcial único de chamado por conversa. `FR3-016`, `NFR3-002`
- [ ] **T3103** Implementar abertura idempotente e envio de mensagem com `expectedVersion`, usando `ChatProvider` fake de eco. `FR3-001`–`FR3-002`
- [ ] **T3104** Implementar limites: turnos por conversa, conversas ativas por requester, tamanho de mensagem e taxa por subject. `FR3-003`
- [ ] **T3105** Testes negativos de autorização: conversa de outro requester, de outro workspace, leitura por agent antes do escalonamento e tentativa de agent/admin enviar turno. `BR3-002`, `BR3-010`, `NFR3-002`

## M2 — Escalonamento com contexto

- [ ] **T3201** Implementar o comando público de `requests` que cria chamado a partir de conversa, em `NEEDS_REVIEW`, com `source = CONVERSATION` e contexto opcional. `FR3-014`, `FR3-017`, `BR3-008`
- [ ] **T3202** Implementar pedido de pessoa humana pelo autor e escalonamento por agent/admin com motivo, em uma única transação com chamado, contexto, vínculo, auditoria, estado e outbox. `FR3-004`, `FR3-007`, `FR3-014`
- [ ] **T3203** Montar o contexto do problema a partir dos turnos (sem IA de contexto nesta fatia) e demonstrar o caminho "chamado sem sugestão". `FR3-015`
- [ ] **T3204** Demonstrar escalonamento concorrente: duas tentativas, um único chamado, a segunda com conflito ou o mesmo resultado pela chave. `FR3-016`, `BR3-007`, `NFR3-003`
- [ ] **T3205** Implementar a leitura da transcrição pelo técnico via rota do chamado, com auditoria de acesso e `404` para conversa não escalada. `FR3-018`, `BR3-010`
- [ ] **T3206** Criar a tela de chat do solicitante e o painel de transcrição dentro do chamado. `FR3-002`, `FR3-018`

## M3 — JEV assíncrono

- [ ] **T3301** Criar a porta `ConversationJudge` com fake determinístico e validação completa de veredito (rota, motivo, confiança, limites, pedido de credencial). `FR3-009`, `FR3-012`, `NFR3-001`
- [ ] **T3302** Gravar `ConversationEvaluationRequested.v1` na outbox junto do turno do assistente e consumir com inbox idempotente. `FR3-008`, `BR3-009`
- [ ] **T3303** Aplicar veredito: `CONTINUE`, `LIKELY_RESOLVED` com confiança suficiente (`AWAITING_CONFIRMATION`) e `ESCALATE`. `FR3-008`–`FR3-010`
- [ ] **T3304** Implementar confirmação e negação de resolução pelo autor, incluindo `RESOLVED_CONFIRMED` sem chamado. `FR3-005`, `BR3-005`
- [ ] **T3305** Demonstrar veredito obsoleto: novo turno, pedido de humano ou confirmação antes do veredito chegar não deixam o veredito alterar o estado. `FR3-011`, `NFR3-003`
- [ ] **T3306** Implementar veredito inválido e JEV indisponível: escalonamento com motivo seguro, retry limitado e DLQ. `FR3-009`, `FR3-019`
- [ ] **T3307** Implementar encerramento por inatividade com varredura idempotente e reabertura por pedido de humano dentro de 7 dias. `FR3-006`, `BR3-005`
- [ ] **T3308** Executar os casos de injeção de prompt de T3005 e registrar o resultado real. `BR3-011`, `NFR3-002`

## M4 — Provedores reais, custo e avaliação

- [ ] **T3401** Implementar adapters reais de chat e JEV com saída estruturada, atrás de opt-in explícito; testes continuam usando fake, sem rede. `FR3-012`
- [ ] **T3402** Implementar reserva e reconciliação de orçamento compartilhado, custo máximo por conversa e escalonamento ao atingir o limite. `FR3-021`
- [ ] **T3403** Tratar timeout ambíguo e falha após possível envio: turno `OUTCOME_UNKNOWN`, sem retry automático, com caminho claro para a pessoa. `FR3-020`
- [ ] **T3404** Instrumentar telemetria por turno e veredito sem conteúdo, com painel local de turnos, escalonamentos, motivos, confiança, custo e DLQ. `FR3-022`, `NFR3-004`–`NFR3-005`
- [ ] **T3405** Rodar o conjunto sintético de conversas e publicar baseline: taxa de escalonamento, falsos resolvidos, escalonamento tardio, validade de schema, latência e custo, com limitações declaradas. `FR3-023`, `NFR3-006`
- [ ] **T3406** Executar e registrar os cenários de aceite 1 a 18 da spec, com saída real dos testes. `NFR3-001`–`NFR3-007`

## M5 — Demonstração e revisão

- [ ] **T3501** Atualizar o [modelo de ameaças](../../docs/security/threat-model.md) com a superfície da conversa: transcrição, injeção, custo por usuário e acesso do técnico. `NFR3-005`
- [ ] **T3502** Demonstrar o fluxo completo em clone limpo, com outra pessoa executando: conversa que resolve, conversa que escala, chamado revisado por uma pessoa e atendimento pela Spec 002.
- [ ] **T3503** Explicar, cada participante no próprio PR, por que o avaliador é separado, o que o impede de encerrar um problema e como a falha vira caminho humano.
- [ ] **T3504** Revisar o README e o [guia da mentoria](../../docs/mentoring-guide.md) para refletir o que realmente existe depois destas fatias — sem alegar resolução automática, satisfação ou economia.

## Definition of Done

- Requisito `FR3-*`/`BR3-*`/`NFR3-*` vinculado e atendido.
- Teste de falha de provedor, de autorização ou de concorrência pertinente **executado**, com saída registrada no PR.
- Nenhum texto de conversa em log, métrica ou evento; verificado por inspeção.
- Contrato, documentação e spec coerentes com o código; limites e limiares que mudaram estão na spec.
- Revisão por outra pessoa, com riscos e limitações declarados. Nenhuma taxa de resolução é alegada sem o baseline de T3405.
