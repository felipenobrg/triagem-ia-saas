# ADR-0001: Começar com monólito modular

- **Status:** Accepted for the educational MVP
- **Date:** 2026-10-06

## Context

O produto tem limites conceituais distintos (workspace, requests, triage, messaging e depois knowledge), mas será construído por dois profissionais em uma mentoria e precisa de uma primeira versão observável. Distribuir módulos em serviços desde o início multiplicaria deploy, rede, autorização e operação antes de demonstrar necessidade.

## Decision

Construir um único backend deployável em Java/Spring Boot com módulos internos, APIs de aplicação explícitas e dependências dirigidas ao domínio. PostgreSQL é a fonte de verdade. Usar CQRS leve para separar comandos das consultas sem segunda base ou event sourcing.

## Alternatives considered

- **Microservices por domínio:** rejeitado no MVP por custo operacional e consistência distribuída precoce.
- **Aplicação sem limites de módulo:** rejeitado por tornar regras e ownership difíceis de demonstrar e refatorar.
- **Event sourcing:** rejeitado; histórico auditável pode ser obtido com tabela de auditoria/outbox sem tornar cada evento histórico uma fonte de estado.

## Consequences

- Um deploy e transações locais tornam fluxo inicial mais simples.
- Limites internos precisam de verificações de dependência e testes de arquitetura; uma pasta por si só não cria módulo.
- Concorrência, escala independente ou ownership distinto podem mais tarde justificar separação; nesse caso demonstrar demanda, contratos, observabilidade, SLOs e custo antes de extrair serviço.

## Revisit when

Um módulo exigir escala ou disponibilidade independente, equipe/ownership separado, isolamento regulatório ou ciclo de deploy incompatível; ou a qualidade dos limites internos se tornar insuficiente mesmo após refatoração.
