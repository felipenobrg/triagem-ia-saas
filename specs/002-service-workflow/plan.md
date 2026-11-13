# Plano técnico — Spec 002

> **Para estudar:** este plano propõe como implementar tempo, concorrência e histórico sem job nem serviço novo. Antes de aceitar, escreva o teste de relógio que provaria o cálculo e o teste de corrida que provaria a atribuição.

- **Estado:** proposta para revisão dos participantes; nada implementado.
- **Spec:** [Spec 002](spec.md) · contrato em [`api-contract.md`](api-contract.md) · execução em [`tasks.md`](tasks.md)

## 1. Visão de arquitetura e limites

Nenhum componente novo. O módulo `requests` da [Spec 001](../001-ai-assisted-request-triage/plan.md) ganha responsável, impacto, prioridade, ciclo de atendimento, pausas, reabertura e vínculo de duplicados. `identity` continua dono de membership e papel e passa a notificar a desativação de membership dentro da mesma transação de negócio. `triage` não ganha poder: continua sugerindo categoria, urgência e resumo.

## 2. Módulos, ownership e dependências permitidas

- `requests` é dono de `request_assignment`, `service_cycle`, `cycle_segment`, `cycle_pause`, `priority_decision` e `duplicate_link`. Nenhum outro módulo escreve nessas tabelas.
- `identity` expõe uma operação de aplicação para desativar/rebaixar membership que, na mesma transação, chama a API pública de `requests` para desatribuir os pedidos afetados.
- `api` traduz comandos e consultas; não contém regra de prioridade nem de relógio.
- O cálculo de tempo útil fica em um componente puro do domínio, sem dependência de framework, recebendo `Clock` injetável.

## 3. Contratos e modelo de dados

`service_cycle` guarda `requestId`, `workspaceId`, número do ciclo, `openedAt`, prioridade efetiva, minutos úteis consumidos, `breachedAt` e `resolvedAt`. `cycle_segment` registra cada trecho de prioridade com início, fim, orçamento e consumo, de forma que uma troca de prioridade não apague violação anterior. `cycle_pause` registra pergunta, autor, início, fim e como foi encerrada (resposta ou cancelamento).

`duplicate_link` tem unicidade por `(workspaceId, secondaryRequestId)` e checagem de que o canônico não é secundário. Todas as tabelas carregam `workspace_id` e entram nos índices. Migrações Flyway novas, nunca edição das anteriores.

## 4. Consistência, concorrência e idempotência

Toda escrita compara `expectedVersion` e incrementa a versão do pedido. Atribuição usa a mesma versão como mecanismo único: a segunda tentativa concorrente falha com conflito, sem lock pessimista adicional. Operações com dois pedidos ordenam os IDs antes de travar, para evitar deadlock, e validam as duas versões antes de gravar o vínculo.

Desativar membership e desatribuir pedidos é uma transação só. A pausa é fechada por uma atualização condicional em `cycle_pause` (`fim IS NULL`), o que garante encerramento exatamente uma vez, mesmo com resposta e cancelamento concorrentes.

Comandos HTTP não são idempotentes por chave nesta spec: após timeout ambíguo, o cliente relê o estado e decide. Isso é intencional e precisa aparecer na UI.

## 5. Falhas, timeout, retry, DLQ e recuperação

Prazo vencido é derivado na leitura, então a indisponibilidade de worker, Rabbit ou IA não altera nem atrasa o cálculo. Não há job de SLA no MVP. Se um alerta for introduzido depois, ele lê o estado derivado e é idempotente, sem recalcular nada.

A recuperação é sempre manual e auditada: admin pode desatribuir, cancelar espera com motivo e corrigir prioridade com motivo. Nenhuma dessas operações apaga histórico.

## 6. Segurança, privacidade, observabilidade e custo

Pergunta, resposta, nota de resolução e motivo ficam apenas no banco, com limite de tamanho, e nunca em log ou evento. O requester não recebe identificador de pedido alheio, nem via vínculo de duplicado. Métricas propostas: pedidos por prioridade, tempo útil até resolução, violações por ciclo, pausas por pedido e conflitos de versão por operação. Nenhuma métrica aqui representa SLA real e o custo de infraestrutura não muda.

## 7. Testes e evidências por requisito

| Requisito | Evidência pretendida | Executado? |
| --- | --- | --- |
| `FR2-001`, `NFR2-001` | Teste de concorrência com PostgreSQL real: duas atribuições na mesma versão | Não |
| `FR2-002` | Teste de desativação de membership com desatribuição atômica e regra de último admin | Não |
| `FR2-003`–`FR2-004` | Testes de tabela da matriz e de override com e sem motivo | Não |
| `FR2-005`, `NFR2-002` | Tabela de testes de relógio injetável, incluindo sexta 17:00 com P1 | Não |
| `FR2-006` | Testes de pausa, resposta do autor, resposta de terceiro e cancelamento por admin | Não |
| `FR2-007` | Testes de resolução, reabertura no instante limite e fora da janela | Não |
| `FR2-008` | Testes de vínculo inválido (cadeia, auto, cross-workspace, resolvido) sem efeito parcial | Não |
| `FR2-009`, `NFR2-003`–`NFR2-004` | Testes negativos de filtro e cursor e verificação de histórico e logs | Não |

A coluna só muda com comando executado e saída anexada ao PR.

## 8. Entrega em fatias, rollback e operação

1. Impacto confirmado, matriz de prioridade e ciclo com prazo (sem pausa).
2. Atribuição, transferência, desatribuição e desativação de membership.
3. Pausa, resposta, cancelamento de espera e resolução com nota.
4. Reabertura com janela e novo ciclo.
5. Vínculo de duplicados e filtros da fila.
6. Interface mínima mostrando responsável, prioridade, prazo, espera e histórico.

Cada fatia é uma migração aditiva. Reverter comportamento significa desligar a rota na API, não apagar dados de ciclo já gravados.

## 9. Alternativas e trade-offs

- **Scheduler como fonte do estado vencido:** rejeitado; cria atraso e recuperação complexa para um valor calculável ([ADR-0006](../../docs/architecture/decisions/ADR-0006-service-workflow.md)).
- **Mesclar duplicados:** rejeitado; alteraria autoria, histórico e política de acesso.
- **Calendários configuráveis por workspace já agora:** adiado; aumenta configuração sem ganho didático.
- **Prioridade sugerida pela IA:** fora de escopo; exigiria avaliação e nova decisão contratual.
- **Lock pessimista na atribuição:** rejeitado; a versão otimista já cobre o caso e mantém um único mecanismo de concorrência.

## 10. Referências técnicas verificadas

Nenhuma biblioteca de calendário ou SLA foi escolhida. O cálculo de tempo útil é código próprio e testável; se a dupla considerar uma dependência, registre a versão e a razão no PR que a introduzir.
