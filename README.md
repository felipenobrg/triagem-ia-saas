# Triagem Inteligente

Projeto de mentoria para desenhar e construir, em dupla, um SaaS didático de triagem de solicitações técnicas com IA assistiva. O recorte permite praticar Java/Spring, arquitetura de software, React/TypeScript, segurança multi-tenant, mensageria e operação.

> **Estado atual:** repositório documental. A aplicação ainda não foi implementada. A spec e as ADRs descrevem o alvo proposto; não significam que as capacidades já existem.

## Produto em uma frase

Uma pessoa autenticada envia uma solicitação técnica ao workspace. O sistema registra o pedido, tenta sugerir categoria, urgência e resumo, e só encaminha à fila depois que uma pessoa agente revisa. Se RabbitMQ ou IA falhar, a solicitação continua disponível para classificação manual.

## Primeiro slice executável

1. Login local via Keycloak OIDC; associação e papéis verificados pelo backend.
2. Requester cria solicitação idempotente e vê apenas as próprias.
3. Agent/admin vê fila do workspace, histórico e faz triagem manual ou revisa sugestão.
4. PostgreSQL grava estado e outbox atomicamente; dispatcher entrega evento persistente ao RabbitMQ.
5. Worker idempotente usa provedor fake por padrão. OpenAI é opt-in, com dados sintéticos e teto de aplicação de US$5/mês.
6. Sugestão validada permanece rascunho até decisão humana.

MVP é local, sem RAG, MCP, intake público ou dado real. AWS é uma extensão temporária posterior e não faz parte do aceite local.

## Como este projeto é desenvolvido

Usamos *spec-driven development*: primeiro comportamento e critérios observáveis; depois arquitetura e decisões; então tarefas rastreáveis e implementação. Para cada mudança:

1. Leia [AGENTS.md](AGENTS.md), a [constituição](.specify/memory/constitution.md) e a spec correspondente.
2. Atualize requisitos (`FR-*`, `BR-*`, `NFR-*`) e cenários antes de alterar comportamento.
3. Atualize o plano e crie/ajuste ADR se houver mudança de dependência, consistência, segurança, custo ou deployment.
4. Quebre em uma fatia vertical pequena. PR aponta requisito, tarefa, evidência, riscos e limitações.
5. Alterne driver/reviewer entre Guilherme e Luis. Ambos devem implementar e revisar backend e frontend ao longo do produto; cada PR tem autoria individual clara.
6. Atualize docs e tarefas junto ao comportamento implementado. Não marque requisito como entregue antes da evidência.

No início de cada sessão de mentoria, escolham uma tarefa que caiba entre os dois encontros mensais. No encontro seguinte, a dupla demonstra código, decisão, teste e uma falha tratada. O objetivo é aprender a defender trade-offs tecnicamente e produzir evidência concreta para portfólio.

## Arquitetura proposta

- Monólito modular Spring Boot; Spring Modulith verifica limites e dependências.
- Módulos: identity, requests, triage, messaging e API.
- PostgreSQL/Flyway como fonte de verdade; CQRS leve no mesmo banco.
- RabbitMQ para triagem assíncrona; outbox/inbox próprias, publicação confirmada e roteabilidade verificada, consumidores idempotentes, retry limitado e DLQ.
- React + TypeScript; OpenAPI como contrato da API.
- Keycloak local para OIDC; membership/role mantidos e autorizados pela aplicação.
- Porta de IA substituível, fake default e adaptador OpenAI opt-in.
- Docker Compose e CI reproduzíveis.

DDD é aplicado para linguagem, limites e invariantes. Eventos representam integração e não implicam event sourcing. RAG e MCP só entram com novas specs após evidência de valor e controles definidos.

## Documentação

| Documento | Uso |
| --- | --- |
| [Spec 001](specs/001-ai-assisted-request-triage/spec.md) | Escopo, atores, regras, requisitos e aceite |
| [Contrato HTTP](specs/001-ai-assisted-request-triage/api-contract.md) | Rotas, autorização, idempotência, erros e evento |
| [Plano técnico](specs/001-ai-assisted-request-triage/plan.md) | Módulos, stack, dados, mensageria, IA e segurança |
| [Tarefas](specs/001-ai-assisted-request-triage/tasks.md) | Milestones e rastreabilidade de implementação |
| [Domínio e dados](docs/architecture/domain-and-data.md) | Bounded contexts, agregados e invariantes |
| [Modelo de ameaças](docs/security/threat-model.md) | Ativos, riscos e evidências de controle |
| [Roadmap](docs/roadmap.md) | RAG, MCP e cloud após MVP |
| [ADRs](docs/architecture/decisions/) | Decisões e razões para alternativas |
| [Contribuição](CONTRIBUTING.md) | Fluxo de trabalho e revisão |

## Execução

Ainda não há aplicação nem comandos de execução. Quando M1 estiver pronto, esta seção será substituída por instruções verificadas de clone, configuração, subida, usuários sintéticos, testes e shutdown. Até lá, use `tasks.md` para acompanhar a implementação planejada.

## Portfólio e dados

Usar somente conteúdo sintético. Não copiar código, arquitetura interna, nomes, dados, regras de negócio, exemplos, credenciais ou documentos da CWI, OSF ou clientes. Em currículo/LinkedIn, descrever apenas contribuição individual demonstrável por PR, testes, ADR ou demo. Não alegar piloto real, precisão, escala ou segurança operacional sem evidência.

## Commits e licença

Commits em inglês com Conventional Commits (`docs:`, `feat:`, `test:`, etc.). Não adicionar `Co-authored-by`. Licença ainda não definida; até haver arquivo de licença, não presumir permissão de redistribuição.
