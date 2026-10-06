# Triagem Inteligente

SaaS de triagem assistida de solicitações para equipes de atendimento e operação. A pessoa envia um pedido em texto; o sistema preserva a mensagem, sugere categoria, urgência e resumo com apoio de IA e aguarda revisão humana antes de criar ou atualizar um chamado na fila.

> **Estado:** especificação e arquitetura inicial. A aplicação ainda não foi implementada. Requisitos, diagramas e decisões neste repositório são a fonte de verdade para o desenvolvimento.

## Problema e proposta

Solicitações chegam por canais e formatos diferentes. A equipe precisa entender o pedido, classificá-lo e encaminhá-lo sem perder o contexto nem deixar uma sugestão automática agir como decisão final. O produto propõe uma caixa de entrada por equipe, histórico auditável e triagem assistida, com entrada manual disponível quando a IA ou a infraestrutura estiver indisponível.

### Fluxo principal

1. Uma pessoa autenticada registra uma solicitação em um workspace.
2. A transação grava a solicitação e um evento na tabela outbox.
3. Um publicador entrega o evento ao RabbitMQ; um worker processa a triagem de forma idempotente.
4. O serviço recupera somente documentos autorizados daquele workspace quando RAG estiver habilitado e envia contexto mínimo ao adaptador de IA.
5. A resposta estruturada é validada, associada ao pedido e apresentada como rascunho.
6. Um atendente aceita, edita ou rejeita a sugestão. A aprovação humana é necessária para encaminhar à fila operacional.
7. A equipe acompanha estado, histórico, tentativas e falhas. Se o processamento falhar, o pedido permanece disponível para triagem manual.

## Como tratar este projeto

Este repositório segue desenvolvimento orientado por especificações (*spec-driven development*). A especificação aprovada descreve o comportamento esperado; o plano traduz os requisitos em arquitetura; as tarefas pequenas orientam a implementação. Código, testes e decisões devem apontar para os identificadores de requisito que atendem.

Antes de implementar uma funcionalidade:

1. Leia a [constituição do projeto](.specify/memory/constitution.md) e a especificação ativa.
2. Esclareça termos, limites, riscos e critérios de aceite ainda ambíguos.
3. Atualize a especificação com cenários observáveis e requisitos identificados (`FR-*`, `NFR-*`).
4. Registre a abordagem técnica e os trade-offs no plano ou em uma ADR.
5. Divida a abordagem em tarefas pequenas, testáveis e rastreáveis.
6. Implemente testes junto do comportamento. Abra PR com os IDs atendidos e evidências.
7. Atualize especificação, arquitetura e estado das tarefas quando o comportamento ou a decisão mudar.

Não implemente comportamento relevante que contradiga a especificação. Se código, teste e especificação discordarem, pare a mudança e reconcilie os três. Não use termos como “pronto”, “seguro” ou “suportado” sem evidência correspondente.

## Documentação

| Documento | Para que serve |
| --- | --- |
| [Constituição](.specify/memory/constitution.md) | Princípios que toda especificação e implementação deve cumprir |
| [Especificação do produto](specs/001-ai-assisted-request-triage/spec.md) | Escopo, atores, requisitos e critérios de aceite |
| [Plano técnico](specs/001-ai-assisted-request-triage/plan.md) | Componentes, módulos, fluxo assíncrono, RAG e implantação |
| [Tarefas](specs/001-ai-assisted-request-triage/tasks.md) | Sequência proposta de entregas e critérios de conclusão |
| [Modelo de domínio e dados](docs/architecture/domain-and-data.md) | Contextos, agregados, entidades, invariantes e consultas |
| [Segurança e privacidade](docs/security/threat-model.md) | Limites de confiança, ameaças e controles obrigatórios |
| [ADRs](docs/architecture/decisions/) | Decisões arquiteturais registradas com contexto e consequências |

## Arquitetura proposta

- **Backend:** Java LTS e Spring Boot, organizado como monólito modular.
- **Persistência:** PostgreSQL com migrações Flyway; `pgvector` somente para a etapa de busca semântica.
- **API:** REST documentada com OpenAPI; autenticação e autorização por workspace.
- **Mensageria:** RabbitMQ para trabalho assíncrono; outbox transacional, confirmação de publicação, consumidores idempotentes, retry limitado e dead-letter queue.
- **Frontend:** React e TypeScript, integrado à API por contrato documentado.
- **Execução local:** Docker Compose com aplicação, banco e broker; CI executa build, testes e verificações estáticas.
- **IA:** adaptador substituível, resposta JSON validada por schema e revisão humana obrigatória.
- **RAG:** busca restrita ao workspace, fontes rastreáveis e avaliação por conjunto sintético antes de habilitar.
- **Cloud didática:** ambiente AWS temporário para aprender deploy, observabilidade, custo e teardown. Não é alvo de produção.
- **MCP:** extensão opcional posterior; ferramentas autenticadas, escopadas por workspace e somente de leitura na primeira versão.

O início é um monólito modular com um banco. A fila existe para desacoplar e controlar trabalho lento de IA, não para converter cada módulo em serviço. Separação em serviços só será considerada após evidência de necessidade operacional ou organizacional e uma decisão registrada.

## Limites do primeiro release

O MVP demonstra workspace e papéis, cadastro e fila de solicitações, sugestão de triagem, aprovação humana, histórico, operação manual sem IA, testes, pipeline e uma implantação didática temporária. RAG, cobrança real, envio de mensagens, integrações externas, MCP com escrita, alta disponibilidade e uso com equipes/dados reais ficam fora do primeiro release.

O projeto é educacional e de portfólio. Usar dados sintéticos. Não reutilizar código, arquitetura interna, nomes, dados, exemplos, credenciais ou regras de negócio da CWI, OSF ou clientes. Não conectar a produção nem expor dados pessoais reais durante a mentoria.

## Como contribuir

Consulte [CONTRIBUTING.md](CONTRIBUTING.md). Cada PR deve relacionar requisito, tarefa, testes executados, limitações e atualização documental. Commits usam Conventional Commits em inglês e não incluem trailers de coautoria.

## Licença

Licença ainda não definida. Até que uma licença seja adicionada, não presuma permissão para reutilizar ou redistribuir o conteúdo deste repositório.
