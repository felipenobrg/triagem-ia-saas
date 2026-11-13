# Plano técnico — Spec 001

> **Para estudar:** este plano é uma proposta de abordagem, não um relato de sistema funcionando. Ao ler cada seção, pergunte qual teste provaria a afirmação e o que aconteceria se a dependência caísse no meio da operação.

- **Estado:** proposta; nada implementado, nenhum build verificado.
- **Spec:** [Spec 001](spec.md) · contrato em [`api-contract.md`](api-contract.md) · execução em [`tasks.md`](tasks.md)

## 1. Visão de arquitetura e limites

Monólito modular Java/Spring Boot, verificado por Spring Modulith ([ADR-0001](../../docs/architecture/decisions/ADR-0001-modular-monolith.md)). Regras de negócio e invariantes ficam no domínio e na aplicação; HTTP, ORM, RabbitMQ, Keycloak e SDK de IA são adaptadores de borda. CQRS é leve: comandos e consultas são separados no código, sem banco de leitura próprio nem consistência eventual interna.

Frontend React/TypeScript consome a API documentada em OpenAPI. Tudo roda local via Docker Compose com PostgreSQL, RabbitMQ e Keycloak.

## 2. Módulos, ownership e dependências permitidas

| Módulo | É dono de | Expõe | Pode depender de |
| --- | --- | --- | --- |
| `identity` | Workspace, membership, papel | Consulta de autorização (`workspaceId` + subject → papel ativo) | — |
| `requests` | Solicitação, texto original, complemento, estado, versão, histórico | Comandos de criação, transição, classificação manual e consultas autorizadas | `identity` |
| `triage` | Tentativa de triagem, sugestão, decisão de revisão, telemetria de IA | Resultado validado de triagem; porta `TriageProvider` | `identity`, API pública de `requests` |
| `messaging` | Outbox, inbox, publicação e consumo | Publicação de evento e registro de consumo idempotente | — |
| `api` | Controllers, DTOs, tratamento de erro, OpenAPI | Nada para outros módulos | todos os anteriores |

Nenhum módulo lê tabela ou classe interna de outro. `triage` nunca grava direto no estado da solicitação: pede a transição pela API pública de `requests`, que valida invariantes e versão.

## 3. Contratos e modelo de dados

Tabelas principais (nomes propostos): `workspace`, `membership`, `request`, `request_text` (origem imutável), `request_clarification`, `request_history`, `triage_attempt`, `triage_suggestion`, `review`, `idempotency_key`, `outbox_message`, `inbox_message`, `ai_budget_reservation`. Chaves e índices são *tenant-aware*: toda FK que cruza agregados carrega `workspace_id` e as consultas filtram por ele. Migrações com Flyway, versionadas e irreversíveis por edição.

`request.version` é a única fonte de concorrência otimista exposta na API. Idempotência guarda hash da chave e hash do corpo canonicalizado, com escopo `(subject, workspace, operação)` e retenção mínima de 24 horas.

Detalhe do modelo em [Domínio e dados](../../docs/architecture/domain-and-data.md). Contrato HTTP em [`api-contract.md`](api-contract.md).

## 4. Consistência, concorrência e idempotência

Criar solicitação grava, na mesma transação: linha de `request`, texto original, histórico e `outbox_message` com `TriageRequested.v1` ([ADR-0002](../../docs/architecture/decisions/ADR-0002-outbox-rabbitmq.md)). O dispatcher lê a outbox em lotes limitados com lease, publica com `mandatory=true` e publisher confirms e só marca como publicada após confirmação sem `basic.return`.

O worker consome, registra inbox único por `(consumidor, eventId)`, reabre os dados pelo `requestId`, confere o `workspaceId` do evento e chama o provedor. O ack ocorre somente depois de persistir o resultado ou a falha recuperável. Toda escrita de estado compara `expectedVersion`; perda de corrida devolve `409` sem efeito.

Corrida entre decisão humana e worker: a decisão humana incrementa a versão; o worker, ao tentar aplicar o resultado, encontra versão divergente e marca a tentativa como `OBSOLETE`.

## 5. Falhas, timeout, retry, DLQ e recuperação

- Erro transitório conhecido (`429`, `5xx`, conexão): retry persistido com `next_attempt_at`, atraso crescente e limite de tentativas.
- Mensagem inválida, versão de schema desconhecida ou poison: DLQ, sem inferir payload.
- Timeout após envio ao provedor: tentativa vira `OUTCOME_UNKNOWN`; não há retry automático, pois a chamada pode ter sido cobrada. Reenvio exige confirmação humana explícita.
- Broker indisponível: a criação continua funcionando (outbox acumula) e a classificação manual com motivo mantém o atendimento vivo.
- Replay é comando explícito, autorizado, idempotente e auditado.

Entrega é *at-least-once*. Nada no sistema promete exactly-once, e idempotência de domínio é o que protege o efeito.

## 6. Segurança, privacidade, observabilidade e custo

Resource Server OIDC valida issuer, audience, assinatura e expiração; papel de produto vem de `identity`, nunca do token. Autorização é checada por rota e repetida na consulta. Logs estruturados levam request, event e correlation ID, sem texto de solicitação, prompt ou segredo. Métricas: idade da outbox, backlog, redeliveries, tentativas por status, latência e custo de IA, DLQ.

IA: porta `TriageProvider` com fake determinístico default. O adapter OpenAI só é ativado por `OPENAI_LIVE_ENABLED=true`, com chave fora do repositório, reserva de custo antes da chamada, reconciliação depois e bloqueio ao atingir US$5/mês ([ADR-0003](../../docs/architecture/decisions/ADR-0003-human-reviewed-ai.md)). Testes nunca fazem chamada de rede.

## 7. Testes e evidências por requisito

| Requisito | Evidência pretendida | Executado? |
| --- | --- | --- |
| `FR-001`–`FR-004` | Testes de integração de autenticação e autorização negativa por papel e workspace | Não |
| `FR-005` | Teste de repetição de chave com corpo igual e divergente | Não |
| `FR-006`–`FR-008`, `NFR-004` | Testes de domínio de transição e teste de concorrência com PostgreSQL real | Não |
| `FR-009`–`FR-010` | Teste de classificação manual com bypass e verificação de histórico | Não |
| `FR-011`–`FR-014`, `FR-023`–`FR-024` | Testes com fake cobrindo rota humana, clarificação única, incerteza e falha | Não |
| `FR-015`–`FR-016` | Teste de reserva de orçamento concorrente e inspeção de log sem segredo | Não |
| `FR-017`, `NFR-006` | Execução do dataset sintético com relatório de métricas e limitações | Não |
| `FR-018`–`FR-022` | Testes de outbox, unroutable, redelivery, DLQ e crash em pontos definidos | Não |
| `NFR-001`, `NFR-005` | Clone limpo executado por outra pessoa; restauração de backup em banco vazio | Não |
| `NFR-002`–`NFR-003`, `NFR-007` | Testes negativos multi-workspace, inspeção de logs e teste de modularidade no CI | Não |

Nenhuma linha acima foi verificada: a coluna só muda com comando executado e resultado anexado ao PR.

## 8. Entrega em fatias, rollback e operação

1. Esqueleto reproduzível: Maven Wrapper, app modular, teste de arquitetura, health check, CI.
2. Identidade: Compose com Keycloak, realm sintético, Resource Server, membership e papéis.
3. Domínio manual: solicitação, estados, versão, classificação manual, histórico, isolamento.
4. Interface mínima: criação pelo requester e fila do agente.
5. Mensageria: outbox, dispatcher, topologia, consumidor idempotente, DLQ e retry.
6. IA fake: porta, validação, rota humana e clarificação, revisão humana.
7. IA real opt-in e avaliação em dataset sintético.

Cada fatia sobe atrás de configuração (flag de IA, flag de triagem automática) e pode voltar à classificação manual sem perda de dados. Rollback de migração é feito por nova migração, não por edição de versão aplicada.

## 9. Alternativas e trade-offs

- **Microserviços desde o início:** rejeitado; multiplicaria operação antes de haver domínio estável ([ADR-0001](../../docs/architecture/decisions/ADR-0001-modular-monolith.md)).
- **Eventos publicados dentro da transação sem outbox:** rejeitado; broker fora da transação perde ou duplica intenção.
- **Chamada de IA síncrona no request HTTP:** rejeitada nesta spec; acopla latência e custo do modelo ao usuário e dificulta retry controlado. A [Spec 003](../003-ai-chat-resolution/spec.md) usa chamada síncrona apenas na conversa, com regras próprias.
- **Event sourcing:** rejeitado; histórico auditável resolve o requisito sem o custo conceitual.
- **Decisão automática da IA:** proibida pela constituição e pela [ADR-0003](../../docs/architecture/decisions/ADR-0003-human-reviewed-ai.md).

## 10. Referências técnicas verificadas

Nenhuma versão de Java, Spring Boot, Spring Modulith, Flyway ou cliente RabbitMQ foi fixada ainda. A dupla escolhe e registra versões compatíveis no primeiro PR técnico (T101), com a fonte consultada. Até lá, não afirmar compatibilidade.
