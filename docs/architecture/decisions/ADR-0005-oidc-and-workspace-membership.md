# ADR-0005: OIDC local e membership da aplicação

- **Estado:** escolhido para MVP
- **Data:** 2026-10-06

## Contexto

O SaaS precisa demonstrar identidade e isolamento multi-tenant sem intake público. Autenticação e autorização de produto são problemas distintos: identidade vem de provedor OIDC; papel e associação ao workspace mudam conforme o domínio.

## Decisão

Usar Keycloak local para OIDC e realm/contas sintéticas. Spring Security Resource Server valida token. Aplicação mantém membership e papéis `REQUESTER`, `AGENT`, `ADMIN` em PostgreSQL e consulta em cada operação. Não aceitar signup público nem usar workspace/role fornecidos pelo cliente como prova. Requester lê somente as próprias solicitações.

## Alternativas

- Intake público com token por workspace: rejeitado por aumentar abuso e simplificar demais identidade de usuário.
- Guardar papéis de domínio apenas em access token: token pode ficar desatualizado e não expressa ownership de request.
- Usuário local gerenciado pela aplicação: menos componente, mas não exercita OIDC; pode servir para teste unitário.

## Consequências

Compose inclui Keycloak; ambiente precisa bootstrap de realm e subject IDs estáveis. Revogação e role passam pela membership da aplicação. OIDC issuer é configurável para futura sandbox; cloud exige configuração e revisão próprias.

## Reavaliar

Se setup local ficar obstáculo desproporcional, sem remover teste de autorização/membership; ou se surgirem requisitos de federação/SSO num escopo posterior.
