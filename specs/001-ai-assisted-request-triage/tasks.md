# Tarefas — Spec 001

Todas as tarefas estão **planejadas**, não implementadas. Concluir significa evidência anexada no PR, critérios atendidos e documentação atualizada. IDs de requisitos referem-se a [`spec.md`](spec.md).

## M0 — Contratos para começar

- [ ] **T001** Rever vocabulário, atores, estados e transições com os dois participantes; atualizar spec caso a conversa revele regra real diferente. `FR-001–017`, `BR-001–009`
- [ ] **T002** Aprovar contrato OpenAPI inicial, envelope de erro, paginação por cursor, `Idempotency-Key`, concorrência/versionamento e limites de payload. `FR-005–010`, `NFR-004`
- [ ] **T003** Fixar ADRs e registrar pontos que só serão decididos após evidência (modelo/preço atual, tamanho de textos, cloud). Decisões já fixadas não reabrem gate do MVP.
- [ ] **T004** Criar conjunto inicial de solicitações sintéticas e rubric de rótulos; versionar método de adjudicação. Sem meta de qualidade antes do baseline. `FR-017`, `NFR-006`

## M1 — Esqueleto reproduzível e identidade

- [ ] **T101** Criar Maven Wrapper e app Spring Boot modular; configurar Spring Modulith e teste de verificação de módulos no CI. `NFR-007`
- [ ] **T102** Criar UI React/TypeScript, API health, tratamento uniforme de erro e documentação OpenAPI alinhada ao contrato. `NFR-001`
- [ ] **T103** Compose com PostgreSQL, RabbitMQ e Keycloak; importar realm e membros exclusivamente sintéticos; health checks e instrução de bootstrap. `FR-001`, `NFR-001`
- [ ] **T104** Validar OIDC no backend como Resource Server; validar issuer/audience e expiração; resolver membership/role pela aplicação. Nunca confiar em role/workspace do client. `FR-001–004`
- [ ] **T105** CI executa compilação, testes, análise estática e guardas para segredo/config local. `.env.example` somente placeholders. `NFR-001`, `NFR-007`

## M2 — Domínio, requests e tenant isolation

- [ ] **T201** Implementar membership/workspace, papéis e regra de último admin. `FR-002`, `FR-004`, `BR-001`
- [ ] **T202** Implementar aggregate request e transições com versão otimista; testar invariantes sem infraestrutura. `FR-006`, `FR-008`, `NFR-004`
- [ ] **T203** Persistência Flyway com chaves/índices tenant-aware e FKs compostas; revisão contra acesso cruzado. `FR-003`, `BR-003`, `NFR-002`
- [ ] **T204** Implementar criação idempotente: escopo subject+workspace+operação, hash chave e corpo canonicalizado; mesmo pedido retorna resultado original, corpo divergente conflita. `FR-005`
- [ ] **T205** Criar telas fluxo requester e fila agent/admin, detalhe/histórico, filtros limitados e paginação cursor. `FR-003`, `FR-007`
- [ ] **T206** Revisão e classificação manual com autorização, `expectedVersion`, audit trail e disputa concorrente. `FR-009–010`, `NFR-004`
- [ ] **T207** Testes negativos por papel e workspace em toda rota/query relevante; manter fixtures sem dados reais. `FR-002–004`, `NFR-002`

## M3 — Outbox, RabbitMQ e recuperação

- [ ] **T301** Gravar request, audit e `TriageRequested.v1` na outbox na mesma transação. Payload com IDs/correlation apenas. `FR-018`, `BR-008`
- [ ] **T302** Implementar dispatcher com lease/lote limitado, mensagem persistente e confirmação; `mandatory=true`, tratar `basic.return` e não marcar publish se unroutable. `FR-019`
- [ ] **T303** Declarar topology durável, routing key versionada, DLQ e fila/agenda de retry limitado com `next_attempt_at`. `FR-021`
- [ ] **T304** Consumidor com inbox unique, validação de envelope, confirmação somente depois do commit e operação idempotente. `FR-020`, `FR-022`
- [ ] **T305** Medir backlog, idade, retorno de mensagem, redelivery, retry e DLQ; runbook de inspeção e replay autorizado/auditado. `FR-021`, `NFR-003`, `NFR-005`
- [ ] **T306** Exercitar crash antes/depois publish e antes/depois commit/ack, broker indisponível e entrega duplicada. Documentar janela de chamada externa e `OUTCOME_UNKNOWN`. `FR-022`, `NFR-005`

## M4 — IA opt-in, revisão e avaliação

- [ ] **T401** Criar porta `TriageProvider`, fake determinístico default, validação de saída e limites. `FR-011–014`
- [ ] **T402** Implementar fluxo de sugestão e tentativa com estados explícitos, aprovação/edit/rejeição humana e caminho manual. `FR-011–014`
- [ ] **T403** Implementar adapter OpenAI Responses API com structured output configurado; testes usam fake e não fazem chamadas de rede. `FR-014–016`
- [ ] **T404** Guardar segredo fora do Git; gate `OPENAI_LIVE_ENABLED`; configurar modelo/preço/capacidade máxima por chamada; reserva concorrente e bloqueio duro de US$5/mês. `FR-015`
- [ ] **T405** Tratar 429/5xx retryable conhecidos com backoff limitado; timeout ambíguo e falha após possível envio viram `OUTCOME_UNKNOWN`, com confirmação humana para reenvio. `FR-012`, `FR-021–022`
- [ ] **T406** Rodar dataset sintético, comparar com labels, publicar métricas, confusion matrix, amostra de sumários, custo/latência e limitações. Guardar baseline, sem gate arbitrário. `FR-017`, `NFR-006`

## M5 — Prontidão do demo local

- [ ] **T501** Threat model atualizado; executar cenários de abuso de prompt, token inválido, IDOR, payload e custo com conteúdo sintético. `NFR-002`
- [ ] **T502** Logs/métricas sem corpo sensível e dashboards locais para outbox, Rabbit, tentativas, IA e orçamento. `NFR-003`
- [ ] **T503** Documentar backup/restore local e demonstrar restauração em banco vazio. `NFR-005`
- [ ] **T504** README testado por outra pessoa em clone limpo: subir Compose, entrar como papéis seed, criar/revisar request com fake, inspecionar eventos e executar shutdown. `NFR-001`
- [ ] **T505** Fazer walkthrough de arquitetura com os dois: modelo e invariantes, por que modular monolith, CQRS leve, outbox/at-least-once, falha ambígua de IA, autorização e trade-offs. Cada pessoa explica PRs próprios.
- [ ] **T506** Atualizar posicionamento de GitHub/LinkedIn/currículo somente com capacidades demonstradas e contribuição individual verificável; sem alegar piloto, escala ou precisão que não foram medidas.

## Depois do MVP — novas especificações necessárias

- [ ] **T601** Propor spec RAG apenas se baseline justificar hipótese; incluir ingestão, filtro pré-busca por workspace, deleção/reindex, fontes, ataque e métricas.
- [ ] **T602** Propor spec MCP opcional; ferramentas read-only, OIDC propagado, isolamento, limites e auditoria.
- [ ] **T603** Após aceite local, avaliar sandbox AWS temporário com preço/quota atuais, região, Terraform, acesso restrito, alarmes, backup/restore e teardown revisado.

RAG, MCP e AWS não bloqueiam nem pertencem à definição de pronto do primeiro MVP.
