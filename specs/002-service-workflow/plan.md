# Plano técnico — Spec 002

> **Para estudar:** trate calendário, locks e migrações como decisões para provar em testes locais. Um teste com relógio real ou um único comando sequencial não demonstra os casos de pausa e disputa descritos na spec. Este plano não é uma arquitetura operacional verificada.

- **Estado:** proposta técnica; nenhuma implementação ou teste executado.
- **Spec:** [regras](spec.md) · **Contrato:** [HTTP](api-contract.md) · **Decisão:** [ADR-0006](../../docs/architecture/decisions/ADR-0006-service-workflow.md).

## Limites e dados

O módulo `requests` da Spec 001 continua dono de estado, atribuição, impacto confirmado, prioridade, ciclos, perguntas/respostas, resolução, reabertura e vínculo duplicado. `identity` expõe consulta de membership/papel ativo; `triage` só fornece sugestão de urgência para revisão humana. `messaging` não decide regras de atendimento. APIs públicas entre módulos impedem que controllers ou workers alterem tabelas internas diretamente.

Migrations Flyway acrescentam campos de impacto/prioridade/assignee ao pedido e tabelas `service_cycle`, `service_pause`, `request_message`, `duplicate_link` e eventos de auditoria. Usar FKs compostas `(workspace_id,request_id)` e restrição de canônico no mesmo workspace. Índices atendem fila por `(workspace_id,status,assignee,priority)` e busca por vencimento. Campos opcionais durante migração preservam pedidos da Spec 001; backfill/cálculo inicial é explícito e testado antes de tornar novo fluxo obrigatório. Nenhuma coluna de texto livre entra em índice, evento ou log.

## Cálculo e concorrência

Implementar `BusinessClock` puro, com `Clock` injetável, zona `America/Fortaleza`, intervalo [09:00,18:00) de segunda a sexta e sem feriados. Funções calculam minutos úteis entre instantes e somam minutos úteis a um instante. Persistir instantes UTC, orçamento e minutos consumidos por ciclo; `dueAt` é projeção reconstituível. Testes com tabela de casos evitam depender do relógio real.

Um comando carrega membership, pedido e versão, aplica transição no domínio e faz `UPDATE ... WHERE workspace_id=? AND id=? AND version=?`. Alteração e auditoria compartilham transação. Para vínculo de dois pedidos, obter locks em ordem crescente de ID, validar versões e invariantes e gravar vínculo/auditoria no mesmo commit. Para desativar membership, coordenar `identity` e `requests` por caso de uso público transacional: desatribuir pedidos ativos, auditar e só então desativar membro; não usar evento eventual para manter a invariante de responsável ativo.

Revisão humana e worker da Spec 001 disputam a versão do pedido. Worker tardio persiste tentativa como `OBSOLETE`, sem sugestão revisável ou transição de estado, após detectar classificação manual. Não altera prioridade, assignee ou ciclo. O worker pode ter consumido custo externo; metadados de uso/custo permanecem auditáveis sem texto livre.

## Entrega em fatias

1. **Domínio e relógio:** matriz de prioridade, estado `WAITING_REQUESTER`, ciclos e testes puros, sem API pública.
2. **Primeiro fluxo vertical:** impacto confirmado/classificação → ciclo/prazo → assumir → resolver; API e UI mínimas com PostgreSQL e OIDC da Spec 001.
3. **Espera e reabertura:** perguntas/respostas, pausa e novo ciclo; testes de tempo e autoria.
4. **Concorrência e membership:** dupla atribuição, transferência, desativação, prioridade alterada e worker tardio.
5. **Duplicados:** vínculo/desvínculo seguro, índice, autorização e UI.

Cada PR cobre uma fatia demonstrável, atualiza OpenAPI e README de execução quando houver código. Testes de integração usam PostgreSQL real/Testcontainers para locks, constraints e transações. Testes de domínio cobrem matriz e calendário. Revisão de segurança exercita IDs de outros workspaces, filtro/cursor e acesso a texto de outro requester. Testes sintéticos não fazem chamada OpenAI.

## Operação e reversão

Migrations são aditivas antes de qualquer obrigatoriedade de campo; dados da Spec 001 permanecem legíveis. Antes de mudar fluxo existente, testar upgrade de banco com fixtures antigas e rollback de aplicação compatível. Dados históricos de ciclo, auditoria e duplicado não são descartados automaticamente. Dashboard local mostra pedidos sem responsável, prazos vencidos e pausados; não há envio de notificação. Falha do relógio/projeção deve bloquear mudança inconsistente e deixar pedido anterior intacto.

## Alternativas e limites

Um scheduler que grava `overdue=true` cria estado derivado sujeito a atraso; consulta calcula vencimento a partir do ciclo. Calendário com feriados e múltiplos fusos é extensão futura, não dependência do exercício. Vínculo duplicado não mescla pedidos porque isso exigiria política de autoria, exclusão e histórico mais ampla. SLA é didático e local, sem obrigação contratual.
