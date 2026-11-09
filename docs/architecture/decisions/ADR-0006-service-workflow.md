# ADR-0006: Atendimento com prazo e decisão humana

- **Estado:** proposta para revisão dos participantes; implementação pendente
- **Data:** 2026-11-09

## Contexto

A Spec 001 ensina triagem e entrega confiável, mas a regra após classificação é pequena. A mentoria precisa de decisões observáveis sobre responsável, prioridade, tempo, reabertura e pedidos relacionados, sem depender de cloud ou de novas integrações.

## Decisão proposta

Adicionar a [Spec 002](../../../specs/002-service-workflow/spec.md) como evolução do atendimento no mesmo monólito. `requests` mantém ownership de estado, responsável e ciclos. Prioridade é derivada de impacto confirmado e urgência aceita, com override humano justificado. Prazo de resolução usa calendário local fixo e cálculo reprodutível; pausas e ciclos são persistidos. Atribuição e vínculo de duplicados exigem versão/locks e autorização por workspace. IA continua sugestiva. Nenhuma notificação externa ou SLA comercial é introduzido.

## Alternativas consideradas

- Adicionar RAG, MCP ou cloud para elevar dificuldade: ampliaria infraestrutura antes de exercitar novas regras de domínio.
- Scheduler como fonte do estado vencido: produz atraso e recuperação complexa para um valor calculável.
- Mesclar duplicados: altera autoria, histórico e política de acesso; vínculo explícito mantém cada pedido íntegro.
- Calendários configuráveis por workspace já no primeiro incremento: aumenta configuração sem hipótese didática suficiente.

## Consequências e revisão

Há novas transições, migrações, testes de relógio, concorrência e autorização. Interfaces e OpenAPI precisam evoluir junto. Revisar calendário, matriz e prazos com participantes antes da implementação. Reavaliar quando uso medido justificar regras de feriado, escalonamento ou notificação; qualquer piloto real exigirá nova análise de privacidade e SLA.
