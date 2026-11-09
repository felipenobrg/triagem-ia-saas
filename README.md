# Triagem Inteligente

Projeto de mentoria para **construir**, em dupla, um sistema de triagem e atendimento de solicitações técnicas. A pessoa solicitante abre um pedido; um agente de IA faz a primeira análise e verifica se há um pedido técnico compreensível para encaminhar à revisão humana. A classificação e o atendimento continuam sob responsabilidade de uma pessoa agente.

> **Estado do projeto:** este repositório contém especificações, decisões e tarefas. **Ainda não há aplicação, build ou demo executável.** As funcionalidades descritas abaixo são o que os participantes vão desenvolver, não recursos já entregues.

> **Uso exclusivamente didático:** cenários, papéis, prazos, arquitetura e critérios de aceite são exercícios para discussão e implementação na mentoria. Não representam operação real, compromisso de atendimento, validação de segurança ou recomendação de adoção por uma empresa.

## O produto, na prática

Imagine que Ana não consegue acessar um ambiente de desenvolvimento:

1. Ana entra no workspace e abre uma solicitação. Ela vê apenas os próprios pedidos.
2. O pedido passa primeiro pelo **agente de IA**. Se for um pedido técnico compreensível, ele sugere categoria, urgência e resumo e encaminha à **fila de revisão humana**. Se estiver vago ou sem problema técnico identificável, pede um complemento a Ana antes de tentar novamente.
3. Uma pessoa agente revisa a sugestão e decide a classificação. Se a IA estiver incerta, falhar ou não conseguir processar o pedido, ele segue para a pessoa agente sem sugestão; a classificação manual continua disponível. Após a decisão humana, o pedido entra na **fila de atendimento**, com prioridade e prazo.
4. Um agente assume o pedido. Se precisar de informações, faz uma pergunta a Ana; o prazo pausa até ela responder.
5. O agente resolve o pedido. Se Ana contestar dentro da janela definida, um novo ciclo começa sem apagar o histórico anterior.

A [Spec 001](specs/001-ai-assisted-request-triage/spec.md) define a análise inicial pela IA, o pedido de complemento e a revisão humana. A [Spec 002](specs/002-service-workflow/spec.md), ainda proposta para revisão dos participantes, acrescenta prioridade, prazo, responsável, andamento, reabertura e vínculo entre pedidos duplicados. A IA nunca descarta ou encerra sozinha uma solicitação.

| Papel | O que pode fazer |
| --- | --- |
| **Solicitante** (`REQUESTER`) | Criar e acompanhar os próprios pedidos, complementar informações pedidas na triagem, responder ao agente humano e contestar uma resolução elegível. |
| **Agente** (`AGENT`) | Ver a fila do workspace, classificar, assumir e conduzir pedidos, revisar sugestões e relacionar duplicados. |
| **Admin** (`ADMIN`) | Fazer o trabalho de agente e administrar membros e atribuições do workspace. |

Keycloak confirma a identidade. O backend consulta a associação e o papel no workspace a cada operação; informar um ID no navegador não concede acesso.

## Por onde começar com a dupla

Para obter a documentação localmente:

```bash
git clone https://github.com/felipenobrg/triagem-ia-saas.git
cd triagem-ia-saas
```

**Primeiro encontro — definir o comportamento.** Leiam esta página, a [constituição](.specify/memory/constitution.md) e, na [Spec 001](specs/001-ai-assisted-request-triage/spec.md), os atores, estados e cenários de aceite. Percorram juntos o exemplo da Ana: quem pode ver o pedido, quais estados ele atravessa e o que acontece quando a IA responde tarde? Registrem na spec qualquer decisão que mudar a regra. Essa é a tarefa [T001](specs/001-ai-assisted-request-triage/tasks.md).

**Primeiro PR técnico — criar a base executável.** A tarefa [T101](specs/001-ai-assisted-request-triage/tasks.md) pede Maven Wrapper, projeto Spring Boot modular e teste de arquitetura. O health check está em **T102** e pode entrar no mesmo PR se a fatia continuar pequena. Outra pessoa deve conseguir clonar, executar o teste e verificar a API pelas instruções acrescentadas ao README. Escolham e registrem versões compatíveis de Java e Spring nesse PR. Até essa entrega, não existe comando de build ou subida de aplicação verificado aqui.

**Próximo incremento — fazer o domínio funcionar.** Modelem estados e classificação manual com testes antes de acrescentar todas as integrações. Depois conectem autenticação, banco e API; construam uma tela mínima; adicionem mensageria e o agente de IA fake em fatias próprias. Durante a construção, o bypass manual permite testar o atendimento; no fluxo final, a IA faz a primeira análise. A [sequência sugerida](docs/mentoring-guide.md) mostra dependências e demonstrações de cada etapa.

Para a Spec 002, comecem por responsável, prioridade e prazo **após** identidade, criação, classificação manual e auditoria. A implementação de RabbitMQ e IA não bloqueia essa primeira fatia. Antes do código, revisem com a dupla a [matriz de prioridade, o calendário e a janela de reabertura](specs/002-service-workflow/spec.md).

## Como trabalhamos com specs

Cada mudança segue o ciclo **regra → cenário observável → contrato → código e teste → demonstração**:

1. Escolham uma tarefa pequena e identifiquem os requisitos `FR-*`, `BR-*`, `NFR-*` ou equivalentes `FR2-*`, `BR2-*`, `NFR2-*` envolvidos.
2. Escrevam um cenário de sucesso e pelo menos um de autorização, concorrência ou falha. Resolvam ambiguidades na spec antes de programar o comportamento afetado.
3. Atualizem contrato HTTP, plano ou ADR quando a decisão mudar API, dados, limites, segurança ou consistência.
4. Um participante implementa e outro revisa; invertam os papéis no PR seguinte. Ambos devem implementar e revisar backend e frontend ao longo do projeto.
5. No encontro seguinte, demonstrem o resultado, os testes **realmente executados** e uma limitação ou falha tratada. Só então marquem a tarefa como concluída.

Exemplo da [Spec 002](specs/002-service-workflow/spec.md): dois agentes tentam assumir o mesmo pedido usando a mesma versão. Apenas um deve conseguir; o outro recebe conflito e o histórico registra uma atribuição. O teste precisa exercer essa disputa, não apenas uma chamada isolada. Veja o [guia da mentoria](docs/mentoring-guide.md) para o cenário completo e o roteiro de revisão.

IA pode ajudar a escrever e investigar código. Quem entrega precisa explicar como o fluxo funciona, por que escolheu a solução e qual evidência prova a regra.

## Arquitetura proposta

- **Backend:** Java/Spring Boot em monólito modular, com limites verificados pelo Spring Modulith. Módulos propostos: `identity`, `requests`, `triage`, `messaging` e `api`.
- **Dados:** PostgreSQL e Flyway. Pedido, auditoria e intenção de publicar evento são gravados na mesma transação quando necessário.
- **Identidade:** Keycloak/OIDC local; membership e papéis de negócio ficam na aplicação.
- **Processamento assíncrono:** RabbitMQ com outbox/inbox, confirmação de publicação, consumidor idempotente, retry limitado e DLQ. A entrega é *at-least-once*.
- **Interface:** React e TypeScript; contrato da API documentado em OpenAPI quando implementado.
- **IA:** agente de triagem com provedor falso e determinístico por padrão. Ele encaminha para revisão humana ou pede um complemento; indisponibilidade e incerteza encaminham ao humano. OpenAI é opcional, com opt-in, somente dados sintéticos e teto de aplicação definido na Spec 001. Sugestão não abre atendimento sem revisão humana.

É uma arquitetura **planejada**. O [plano da Spec 001](specs/001-ai-assisted-request-triage/plan.md), o [plano da Spec 002](specs/002-service-workflow/plan.md) e as [ADRs](docs/architecture/decisions/) explicam os limites e as decisões. RAG, MCP e cloud ficam no [roadmap posterior](docs/roadmap.md).

## Documentos do projeto

| Para entender... | Leia |
| --- | --- |
| Comportamento inicial, atores, estados e aceite | [Spec 001](specs/001-ai-assisted-request-triage/spec.md) · [contrato HTTP](specs/001-ai-assisted-request-triage/api-contract.md) · [tarefas](specs/001-ai-assisted-request-triage/tasks.md) |
| Atendimento, prioridade, prazo, atribuição e reabertura | [Spec 002](specs/002-service-workflow/spec.md) · [contrato HTTP](specs/002-service-workflow/api-contract.md) · [tarefas](specs/002-service-workflow/tasks.md) |
| Como conduzir os encontros e revisar PRs | [Guia da mentoria](docs/mentoring-guide.md) · [contribuição](CONTRIBUTING.md) |
| Dados, segurança e escolhas de arquitetura | [Domínio e dados](docs/architecture/domain-and-data.md) · [modelo de ameaças](docs/security/threat-model.md) · [ADRs](docs/architecture/decisions/) |

## Execução e limites

Ainda não há aplicação nem comandos de execução. O primeiro PR técnico deve atualizar esta seção com comandos **testados em clone limpo** para build, teste e subida. Os PRs seguintes acrescentam Compose, usuários sintéticos, fluxo de demonstração e shutdown conforme cada parte existir.

Usar apenas dados sintéticos. Não copiar código, regras, documentos, credenciais ou dados da CWI, OSF ou clientes. Em portfólio, descrever somente contribuição e comportamento demonstráveis por PR, teste ou demo. Commits seguem Conventional Commits em inglês; não incluir trailer `Co-authored-by`. A licença ainda não foi definida; não presumir permissão de redistribuição.
