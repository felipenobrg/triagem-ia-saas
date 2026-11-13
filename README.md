# Triagem Inteligente

Projeto de mentoria para **construir**, em dupla, um sistema de triagem e atendimento de solicitações técnicas. A pessoa solicitante abre um pedido — por formulário ou por uma conversa com um agente de IA; a IA faz a primeira análise e verifica se há um pedido técnico compreensível para encaminhar à revisão humana. A classificação e o atendimento continuam sob responsabilidade de uma pessoa agente.

> **Estado do projeto:** o repositório contém especificações, decisões, tarefas e uma **fatia executável** do backend em `backend/`: o domínio das Specs 001 e 003 rodando sobre PostgreSQL, RabbitMQ e Keycloak reais, com provedores de IA **falsos e determinísticos**. IA real, frontend e toda a Spec 002 **não existem ainda**, e **nenhum baseline de IA foi medido** — nada aqui demonstra qualidade de IA. O que está pronto, com o teste que comprova cada requisito, está em [estado da implementação](docs/implementation-status.md); o resto é o que os participantes vão desenvolver.

> **Uso exclusivamente didático:** cenários, papéis, prazos, arquitetura e critérios de aceite são exercícios para discussão e implementação na mentoria. Não representam operação real, compromisso de atendimento, validação de segurança ou recomendação de adoção por uma empresa.

## O produto, na prática

Imagine que Ana não consegue acessar um ambiente de desenvolvimento:

1. Ana entra no workspace e abre uma solicitação. Ela vê apenas os próprios pedidos.
2. O pedido passa primeiro pelo **agente de IA**. Se for um pedido técnico compreensível, ele sugere categoria, urgência e resumo e encaminha à **fila de revisão humana**. Se estiver vago ou sem problema técnico identificável, pede um complemento a Ana antes de tentar novamente.
3. Uma pessoa agente revisa a sugestão e decide a classificação. Se a IA estiver incerta, falhar ou não conseguir processar o pedido, ele segue para a pessoa agente sem sugestão; a classificação manual continua disponível. Após a decisão humana, o pedido entra na **fila de atendimento**, com prioridade e prazo.
4. Um agente assume o pedido. Se precisar de informações, faz uma pergunta a Ana; o prazo pausa até ela responder.
5. O agente resolve o pedido. Se Ana contestar dentro da janela definida, um novo ciclo começa sem apagar o histórico anterior.

Também é possível começar por uma **conversa**: Ana abre um chat, descreve o problema e o agente de IA tenta resolver em poucos turnos. Depois de cada resposta, um avaliador separado — o **JEV** — julga se o problema foi resolvido, se vale continuar ou se é hora de chamar uma pessoa. Quando a conversa não resolve, o sistema abre um chamado já com o contexto do problema (resumo, sintomas, o que foi tentado) e o coloca na mesma fila de revisão humana, de onde um técnico assume. Só Ana pode declarar o problema resolvido; o avaliador pode pedir essa confirmação ou escalar, nunca encerrar.

A [Spec 001](specs/001-ai-assisted-request-triage/spec.md) define a análise inicial pela IA, o pedido de complemento e a revisão humana. A [Spec 002](specs/002-service-workflow/spec.md), ainda proposta para revisão dos participantes, acrescenta prioridade, prazo, responsável, andamento, reabertura e vínculo entre pedidos duplicados. A [Spec 003](specs/003-ai-chat-resolution/spec.md), também proposta, acrescenta a conversa assistida, o avaliador e o escalonamento com contexto. A IA nunca descarta ou encerra sozinha uma solicitação.

| Papel | O que pode fazer |
| --- | --- |
| **Solicitante** (`REQUESTER`) | Criar e acompanhar os próprios pedidos, conversar com o agente de IA, pedir atendimento humano, complementar informações pedidas na triagem, responder ao agente humano e contestar uma resolução elegível. |
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

**Primeiro PR técnico — criar a base executável.** A tarefa [T101](specs/001-ai-assisted-request-triage/tasks.md) pede Maven Wrapper, projeto Spring Boot modular e teste de arquitetura; o health check está em **T102**. Essa base já existe em `backend/` (Java 21, Spring Boot 3.3.5, Spring Modulith 1.2.5, verificação de módulos no teste `ModuleBoundariesTest`) — a parte que falta é justamente a que depende de vocês: **outra pessoa** precisa clonar, rodar `./mvnw test`, subir a API e revisar antes de qualquer tarefa ser marcada como concluída. Comecem conferindo se as escolhas de versão e a fatia entregue fazem sentido para o exercício, e usem o [estado da implementação](docs/implementation-status.md) como ponto de partida da revisão.

**Próximo incremento — fazer o domínio funcionar.** Modelem estados e classificação manual com testes antes de acrescentar todas as integrações. Depois conectem autenticação, banco e API; construam uma tela mínima; adicionem mensageria e o agente de IA fake em fatias próprias. Durante a construção, o bypass manual permite testar o atendimento; no fluxo final, a IA faz a primeira análise. A [sequência sugerida](docs/mentoring-guide.md) mostra dependências e demonstrações de cada etapa.

Para a Spec 002, comecem por responsável, prioridade e prazo **após** identidade, criação, classificação manual e auditoria. A implementação de RabbitMQ e IA não bloqueia essa primeira fatia. Antes do código, revisem com a dupla a [matriz de prioridade, o calendário e a janela de reabertura](specs/002-service-workflow/spec.md).

Para a Spec 003, a primeira fatia é a **conversa sem IA de verdade**: turnos imutáveis, limites, autorização e escalonamento manual criando um chamado com contexto na fila humana. O avaliador (JEV) entra depois, assíncrono e com fake, e só então os provedores reais com opt-in. Antes do código, decidam na spec o limite de turnos, o limiar de confiança e as janelas de inatividade e reabertura — são [valores propostos, sem evidência](specs/003-ai-chat-resolution/spec.md).

## Como trabalhamos com specs

Cada mudança segue o ciclo **regra → cenário observável → contrato → código e teste → demonstração**:

1. Escolham uma tarefa pequena e identifiquem os requisitos `FR-*`, `BR-*`, `NFR-*` ou equivalentes `FR2-*`/`FR3-*`, `BR2-*`/`BR3-*`, `NFR2-*`/`NFR3-*` envolvidos.
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
- **IA:** agente de triagem com provedor falso e determinístico por padrão. Ele encaminha para revisão humana ou pede um complemento; indisponibilidade e incerteza encaminham ao humano. A Spec 003 acrescenta duas portas separadas — agente de conversa e avaliador (JEV) —, também com fake por padrão, resposta síncrona e avaliação assíncrona. OpenAI é opcional, com opt-in, somente dados sintéticos e teto de aplicação definido na Spec 001. Sugestão não abre atendimento sem revisão humana.

É uma arquitetura **planejada**. O [plano da Spec 001](specs/001-ai-assisted-request-triage/plan.md), o [plano da Spec 002](specs/002-service-workflow/plan.md) e as [ADRs](docs/architecture/decisions/) explicam os limites e as decisões. RAG, MCP e cloud ficam no [roadmap posterior](docs/roadmap.md).

## Documentos do projeto

| Para entender... | Leia |
| --- | --- |
| Comportamento inicial, atores, estados e aceite | [Spec 001](specs/001-ai-assisted-request-triage/spec.md) · [contrato HTTP](specs/001-ai-assisted-request-triage/api-contract.md) · [tarefas](specs/001-ai-assisted-request-triage/tasks.md) |
| Atendimento, prioridade, prazo, atribuição e reabertura | [Spec 002](specs/002-service-workflow/spec.md) · [contrato HTTP](specs/002-service-workflow/api-contract.md) · [tarefas](specs/002-service-workflow/tasks.md) |
| Conversa com IA, avaliação pelo JEV e escalonamento com contexto | [Spec 003](specs/003-ai-chat-resolution/spec.md) · [contrato HTTP](specs/003-ai-chat-resolution/api-contract.md) · [tarefas](specs/003-ai-chat-resolution/tasks.md) |
| O que já está implementado e com qual teste | [Estado da implementação](docs/implementation-status.md) · [backend](backend/README.md) |
| Como conduzir os encontros e revisar PRs | [Guia da mentoria](docs/mentoring-guide.md) · [contribuição](CONTRIBUTING.md) |
| Dados, segurança e escolhas de arquitetura | [Domínio e dados](docs/architecture/domain-and-data.md) · [modelo de ameaças](docs/security/threat-model.md) · [ADRs](docs/architecture/decisions/) |

## Execução e limites

O ambiente local sobe inteiro por Docker — PostgreSQL, RabbitMQ, Keycloak e backend:

```bash
docker compose up -d --build
curl http://localhost:8080/api/v1/health
docker compose down
```

Os usuários do realm sintético são `ana` (solicitante), `bruno` (agente), `carla` (admin) e `dani` (outro workspace), todos com senha `senha-sintetica`. Nenhuma credencial deste repositório é real.

Sem Docker, é necessário **JDK 21** (o wrapper usa o `JAVA_HOME` do ambiente) e rede na primeira execução:

```bash
cd backend
export JAVA_HOME="/c/Users/<voce>/.jdks/ms-21.0.12.1"   # ajuste ao seu caminho
./mvnw test   # 47 testes; os de integração sobem Postgres e RabbitMQ via Testcontainers
```

O fluxo de demonstração completo — Ana abre o chat, o problema não resolve, o JEV escala **de forma assíncrona** e Bruno recebe o chamado com o contexto do problema — está em [`backend/README.md`](backend/README.md), com os comandos autenticados por token do Keycloak e a lista de limitações. Esses comandos foram executados na máquina de quem os escreveu; conforme o `NFR-001`, só contam como verificados depois que **outra pessoa** os rodar em clone limpo (T504).

Usar apenas dados sintéticos. Não copiar código, regras, documentos, credenciais ou dados da CWI, OSF ou clientes. Em portfólio, descrever somente contribuição e comportamento demonstráveis por PR, teste ou demo. Commits seguem Conventional Commits em inglês; não incluir trailer `Co-authored-by`. A licença ainda não foi definida; não presumir permissão de redistribuição.
