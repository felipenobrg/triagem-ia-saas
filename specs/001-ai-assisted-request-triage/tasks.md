# Tarefas — Spec 001

> **Nenhuma caixa abaixo foi marcada.** Parte do comportamento das tarefas M2/M4 já existe em `backend/`, mas marcar exige revisão independente e execução por outra pessoa em clone limpo. O mapa de requisito → teste executado está em [estado da implementação](../../docs/implementation-status.md).

> **Para estudar:** use cada tarefa como exercício pequeno, com demonstração reproduzível e pelo menos um teste de falha ou de autorização quando couber. Caixa vazia significa trabalho planejado; marcá-la exige evidência de código e comportamento, não apenas leitura da spec.

Todas as tarefas estão **planejadas**, não implementadas. Concluir significa evidência anexada ao PR, critérios atendidos e documentação atualizada. Os IDs de requisitos referem-se a [`spec.md`](spec.md).

## M0 — Contratos para começar

- [ ] **T001** Rever vocabulário, atores, estados e transições com os dois participantes; atualizar a spec se a dupla escolher outra regra para o exercício. `FR-001`–`FR-017`, `FR-023`–`FR-024`, `BR-001`–`BR-010`
- [ ] **T002** Aprovar contrato OpenAPI inicial, envelope de erro, paginação por cursor, `Idempotency-Key`, concorrência por versão e limites de payload. `FR-005`–`FR-010`, `NFR-004`
- [ ] **T003** Fixar as ADRs e registrar o que só será decidido após evidência (modelo e preço atuais, tamanho de textos, cloud). Decisões já fixadas não reabrem gate do MVP.
- [ ] **T004** Criar conjunto inicial de solicitações sintéticas suficientes e insuficientes, com rubrica de rota, pergunta e rótulos; versionar a adjudicação. Sem meta de qualidade antes do baseline. `FR-017`, `NFR-006`

## M1 — Esqueleto reproduzível e identidade

- [ ] **T101** Criar Maven Wrapper e app Spring Boot modular; configurar Spring Modulith e teste de verificação de módulos no CI. Registrar no PR as versões escolhidas de Java e Spring. `NFR-007`
- [ ] **T102** Criar UI React/TypeScript, health check da API, tratamento uniforme de erro e documentação OpenAPI alinhada ao contrato. `NFR-001`
- [ ] **T103** Compose com PostgreSQL, RabbitMQ e Keycloak; importar realm e membros exclusivamente sintéticos; health checks e instrução de bootstrap. `FR-001`, `NFR-001`
- [ ] **T104** Validar OIDC no backend como Resource Server (issuer, audience, assinatura, expiração) e resolver membership e papel pela aplicação. Nunca confiar em papel ou workspace vindos do cliente. `FR-001`–`FR-004`
- [ ] **T105** CI executa compilação, testes, análise estática e guardas contra segredo em configuração. `.env.example` apenas com placeholders. `NFR-001`, `NFR-007`

## M2 — Domínio, solicitações e isolamento

- [ ] **T201** Implementar workspace, membership, papéis e regra de último admin. `FR-002`, `FR-004`, `BR-001`
- [ ] **T202** Implementar o aggregate de solicitação e as transições com versão otimista; testar invariantes sem infraestrutura. `FR-006`, `FR-008`, `NFR-004`
- [ ] **T203** Persistência com Flyway, chaves e índices tenant-aware e FKs compostas; revisão explícita contra acesso cruzado. `FR-003`, `BR-003`, `NFR-002`
- [ ] **T204** Implementar criação idempotente com escopo subject + workspace + operação, hash da chave e do corpo canonicalizado; mesmo pedido devolve o resultado original e corpo divergente conflita. `FR-005`
- [ ] **T205** Criar telas do fluxo do requester e da fila de agent/admin, com detalhe, histórico, filtros limitados e paginação por cursor. `FR-003`, `FR-007`
- [ ] **T206** Revisão e classificação manual com autorização, `expectedVersion`, trilha de auditoria e disputa concorrente. `FR-009`–`FR-010`, `NFR-004`
- [ ] **T207** Testes negativos por papel e workspace em toda rota e consulta relevante; manter fixtures sem dado real. `FR-002`–`FR-004`, `NFR-002`
- [ ] **T208** Demonstrar a corrida entre classificação manual e worker: o humano vence, a tentativa tardia fica `OBSOLETE` e não há sugestão revisável nem regressão de estado. `FR-008`, `BR-004`, `BR-007`

## M3 — Outbox, RabbitMQ e recuperação

- [ ] **T301** Gravar solicitação, auditoria e `TriageRequested.v1` na outbox, na mesma transação. Payload apenas com IDs e correlation. `FR-018`, `BR-008`
- [ ] **T302** Implementar dispatcher com lease e lote limitado, mensagem persistente, `mandatory=true`, tratamento de `basic.return` e marcação de publicação somente após confirmação. `FR-019`
- [ ] **T303** Declarar topologia durável, routing key versionada, DLQ e agenda de retry limitado com `next_attempt_at`. `FR-021`
- [ ] **T304** Consumidor com inbox único, validação de envelope, ack somente após commit e operação idempotente. `FR-020`, `FR-022`
- [ ] **T305** Medir backlog, idade da outbox, retorno de mensagem, redelivery, retry e DLQ; escrever runbook de inspeção e replay autorizado e auditado. `FR-021`, `NFR-003`, `NFR-005`
- [ ] **T306** Exercitar crash antes e depois do publish, antes e depois do commit e do ack, broker indisponível e entrega duplicada. Documentar a janela da chamada externa e o `OUTCOME_UNKNOWN`. `FR-022`, `NFR-005`

## M4 — IA opt-in, revisão e avaliação

- [ ] **T401** Criar a porta `TriageProvider` com rotas `HUMAN_REVIEW` e `REQUEST_CLARIFICATION`, fake determinístico default, validação de saída e limites de tamanho. `FR-011`–`FR-014`
- [ ] **T402** Implementar o fluxo análise inicial → revisão humana com sugestão, ou pedido de complemento; mais aprovação, edição, rejeição e caminho manual. `FR-011`–`FR-014`, `FR-023`–`FR-024`
- [ ] **T403** Implementar adapter OpenAI com saída estruturada configurada; testes usam fake e não fazem chamada de rede. `FR-014`–`FR-016`
- [ ] **T404** Guardar segredo fora do Git; gate `OPENAI_LIVE_ENABLED`; configurar modelo, preço e custo máximo por chamada; reserva concorrente e bloqueio duro de US$5/mês. `FR-015`
- [ ] **T405** Tratar `429` e `5xx` retryable conhecidos com backoff limitado; timeout ambíguo e falha após possível envio viram `OUTCOME_UNKNOWN`, com confirmação humana para reenvio. `FR-012`, `FR-021`–`FR-022`
- [ ] **T406** Rodar o dataset sintético; comparar rota humano/complemento e rótulos; publicar retenção indevida, matriz de confusão, amostras de perguntas e resumos, custo, latência e limitações. Guardar baseline, sem gate arbitrário. `FR-017`, `NFR-006`
- [ ] **T407** Implementar complemento único, pedido direto de pessoa agente, encaminhamento por incerteza ou falha e bypass auditado durante indisponibilidade; testar que nada fica retido indefinidamente sem sugestão. `FR-023`–`FR-024`, `BR-010`

## M5 — Prontidão do demo local

- [ ] **T501** Atualizar o threat model e executar cenários de abuso de prompt, token inválido, IDOR, payload excessivo e custo, com conteúdo sintético. `NFR-002`
- [ ] **T502** Logs e métricas sem corpo sensível, com painéis locais para outbox, Rabbit, tentativas, IA e orçamento. `NFR-003`
- [ ] **T503** Documentar backup e restore local e demonstrar restauração em banco vazio. `NFR-005`
- [ ] **T504** README testado por outra pessoa em clone limpo: subir Compose, entrar com os papéis seed, criar e revisar solicitação com o fake, inspecionar eventos e executar shutdown. `NFR-001`
- [ ] **T505** Fazer walkthrough de arquitetura entre os dois: modelo e invariantes, por que monólito modular, CQRS leve, outbox e at-least-once, falha ambígua de IA, autorização e trade-offs. Cada pessoa explica os próprios PRs.
- [ ] **T506** Atualizar posicionamento em GitHub, LinkedIn e currículo somente com capacidades demonstradas e contribuição individual verificável; sem alegar piloto, escala ou precisão não medidos.

## Depois do MVP — novas especificações necessárias

- [ ] **T601** Propor spec de RAG apenas se o baseline justificar a hipótese; incluir ingestão, filtro pré-busca por workspace, deleção e reindexação, fontes, ataque e métricas.
- [ ] **T602** Propor spec de MCP opcional, com ferramentas read-only, OIDC propagado, isolamento, limites e auditoria.
- [ ] **T603** Após o aceite local, avaliar sandbox AWS temporário com preço e quota atuais, região, Terraform, acesso restrito, alarmes, backup/restore e teardown revisado.

RAG, MCP e AWS não bloqueiam nem pertencem à definição de pronto do primeiro MVP.

## Definition of Done

- Requisitos relacionados atendidos e estado da spec atualizado com evidência.
- Testes pertinentes realmente executados; resultado real registrado no PR.
- Contrato, documentação e segurança alinhados ao código.
- Revisão por outra pessoa; riscos e limitações registrados.
