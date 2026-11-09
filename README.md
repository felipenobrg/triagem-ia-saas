# Triagem Inteligente

Projeto de mentoria para desenhar e construir, em dupla, um SaaS didático de triagem de solicitações técnicas com IA assistiva. O recorte permite praticar Java/Spring, arquitetura de software, React/TypeScript, segurança multi-tenant, mensageria e operação.

> **Estado atual:** repositório documental. A aplicação ainda não foi implementada. A spec e as ADRs descrevem o alvo proposto; não significam que as capacidades já existem.

## Produto em uma frase

Uma pessoa autenticada envia uma solicitação técnica ao workspace. O pedido aparece na fila de triagem; a IA pode sugerir categoria, urgência e resumo, mas somente uma pessoa agente decide a classificação que o coloca na fila de atendimento. Se RabbitMQ ou IA falhar, a classificação manual continua disponível. A evolução proposta na Spec 002 acrescenta responsável, prioridade, prazo, espera por resposta, reabertura e vínculo de duplicados.

## O que os alunos vão construir

O repositório é o ponto de partida, não um aplicativo pronto. As [tarefas da Spec 001](specs/001-ai-assisted-request-triage/tasks.md) constroem a triagem; as [tarefas da Spec 002](specs/002-service-workflow/tasks.md) aprofundam o atendimento depois que identidade, criação, classificação manual e auditoria estiverem funcionando.

### Primeiro exercício executável

Criar Maven Wrapper, projeto Spring Boot, teste automatizado e endpoint de health (T101). O aceite é outra pessoa clonar o repositório, executar teste e subir a API seguindo o README atualizado no PR. Esse exercício ainda não implementa o produto.

### Alvo da Spec 001

1. Login local via Keycloak OIDC; associação e papéis verificados pelo backend.
2. Requester cria solicitação idempotente e vê apenas as próprias.
3. Agent/admin vê fila do workspace, histórico e faz triagem manual ou revisa sugestão.
4. PostgreSQL grava estado e outbox atomicamente; dispatcher entrega evento persistente ao RabbitMQ.
5. Worker idempotente usa provedor fake por padrão. OpenAI é opt-in, com dados sintéticos e teto de aplicação de US$5/mês.
6. Sugestão validada permanece rascunho até decisão humana.

O MVP é local, sem RAG, MCP, intake público ou dado real. AWS é uma extensão temporária posterior e não faz parte do aceite local.

### Evolução da Spec 002

1. Agente confirma impacto e urgência; uma matriz calcula prioridade e prazo de resolução em horário útil.
2. Agente assume ou transfere o pedido; concorrência não permite dois responsáveis vencedores.
3. Pergunta ao solicitante pausa o prazo; a resposta retoma o atendimento.
4. Resolução e contestação preservam ciclos e histórico; duplicados podem ser vinculados sem mesclar dados ou liberar leitura de outro pedido.

Essas regras são propostas para revisão dos participantes, não capacidades implementadas. A [Spec 002](specs/002-service-workflow/spec.md) define comportamento e exemplos de aceite.

## Como este projeto é desenvolvido

Usamos *spec-driven development*: primeiro comportamento e critérios observáveis; depois arquitetura e decisões; então tarefas rastreáveis e implementação. Para cada mudança:

1. Leia [AGENTS.md](AGENTS.md), a [constituição](.specify/memory/constitution.md) e a spec correspondente.
2. Atualize requisitos (`FR-*`, `BR-*`, `NFR-*`) e cenários antes de alterar comportamento.
3. Atualize o plano e crie/ajuste ADR se houver mudança de dependência, consistência, segurança, custo ou deployment.
4. Quebre em uma fatia vertical pequena. PR aponta requisito, tarefa, evidência, riscos e limitações.
5. Alterne driver/reviewer entre Guilherme e Luis. Ambos devem implementar e revisar backend e frontend ao longo do produto; cada PR tem autoria individual clara.
6. Atualize docs e tarefas junto ao comportamento implementado. Não marque requisito como entregue antes da evidência.

No início de cada sessão de mentoria, escolham uma tarefa que caiba entre os dois encontros mensais. No encontro seguinte, a dupla demonstra código, decisão, teste e uma falha tratada. O objetivo é aprender a defender decisões técnicas e produzir evidência concreta para portfólio. Antes de implementar a Spec 002, revisem com os participantes a matriz de prioridade, o calendário e a janela de reabertura.

### Ritual de uma tarefa

1. Escolham um resultado pequeno e demonstrável em `tasks.md`.
2. Leiam o requisito e escrevam um cenário de sucesso, um de autorização/concorrência e um de falha/recuperação. Ajustem a spec se houver ambiguidade.
3. Atualizem contrato/ADR quando a decisão afetar API, estado, dados ou consistência.
4. Um participante implementa; outro revisa e tenta quebrar a regra com testes. Invertam os papéis no PR seguinte.
5. Demonstrem o comportamento, o comando/teste realmente executado e o que ainda falta. Só então marquem a tarefa como concluída.

Exemplo: dois agentes tentam assumir o mesmo pedido usando a mesma versão. Apenas um vence; o segundo recebe conflito e o histórico mostra uma atribuição. A regra, o teste de concorrência e a demo fazem parte da mesma entrega.

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
| [Spec 002](specs/002-service-workflow/spec.md) · [contrato](specs/002-service-workflow/api-contract.md) · [plano](specs/002-service-workflow/plan.md) · [tarefas](specs/002-service-workflow/tasks.md) | Evolução proposta para gestão do atendimento |
| [Domínio e dados](docs/architecture/domain-and-data.md) | Bounded contexts, agregados e invariantes |
| [Modelo de ameaças](docs/security/threat-model.md) | Ativos, riscos e evidências de controle |
| [Roadmap](docs/roadmap.md) | RAG, MCP e cloud após MVP |
| [ADRs](docs/architecture/decisions/) | Decisões e razões para alternativas |
| [Contribuição](CONTRIBUTING.md) | Fluxo de trabalho e revisão |
| [Guia da mentoria](docs/mentoring-guide.md) | Condução dos encontros, ordem de implementação e exemplo de PR |

## Execução

Ainda não há aplicação nem comandos de execução. Quando o primeiro exercício estiver pronto, esta seção receberá instruções verificadas de clone, build, teste e subida da API. Conforme os serviços forem implementados, acrescentar configuração, usuários sintéticos, fluxo e shutdown. Até lá, acompanhe as tarefas das Specs 001 e 002.

## Portfólio e dados

Usar somente conteúdo sintético. Não copiar código, arquitetura interna, nomes, dados, regras de negócio, exemplos, credenciais ou documentos da CWI, OSF ou clientes. Em currículo/LinkedIn, descrever apenas contribuição individual demonstrável por PR, testes, ADR ou demo. Não alegar piloto real, precisão, escala ou segurança operacional sem evidência.

## Commits e licença

Commits em inglês com Conventional Commits (`docs:`, `feat:`, `test:`, etc.). Não adicionar `Co-authored-by`. Licença ainda não definida; até haver arquivo de licença, não presumir permissão de redistribuição.
