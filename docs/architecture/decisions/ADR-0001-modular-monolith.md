# ADR-0001: Backend como monólito modular

- **Estado:** escolhido para MVP; verificar em implementação
- **Data:** 2026-10-06

## Contexto

Dois desenvolvedores constroem produto didático em conjunto. Precisam praticar boundaries, deploy, testes e operação sem criar custos distribuídos antes de comprovar necessidade. Há fluxos assíncronos externos, mas isso não exige separar deploy por domínio.

## Decisão

Backend Java/Spring em um deploy com módulos `identity`, `requests`, `triage`, `messaging` e `api`, APIs internas explícitas e ownership de dados. PostgreSQL compartilhado como fonte de verdade. Spring Modulith verifica ciclos e acessos internos. Usar CQRS leve para separar comandos e consultas, sem event sourcing ou banco de leitura apartado.

## Alternativas

- Microserviços: mais rede, deploy, identidade distribuída e consistência sem demanda mensurável.
- Pacotes sem regras: simples inicialmente, porém mistura ownership e permite dependências acidentais.
- Event sourcing: não necessário para histórico auditável, que é atendido por registros próprios.

## Consequências

Um deploy e transações locais simplificam consistência. Limites só são reais se API interna e verificação forem respeitadas. RabbitMQ continua como infraestrutura para trabalho de triagem; não torna cada módulo um serviço.

## Reavaliar quando

Medição mostrar necessidade de escala/isolamento/deploy independente, ou boundary permanecer inadequado após refatoração. Exigir evidência operacional, contratos, custo e ADR nova.
