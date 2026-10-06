# Spec 001: Triagem assistida de solicitações

- **Estado:** pronta para revisão dos participantes; ainda não aprovada como produto
- **Data:** 2026-10-06
- **Alvo:** MVP didático local, com dados sintéticos
- **Fonte de verdade:** este documento define comportamento. `plan.md` define a abordagem técnica; `tasks.md` rastreia execução.

## 1. Problema e objetivo

Equipes internas de suporte técnico recebem solicitações em texto livre. Queremos organizar a fila e testar se uma IA consegue sugerir categoria, urgência e resumo sem retirar o controle da pessoa atendente. O produto é um exercício de arquitetura e engenharia para dois desenvolvedores Java: domínio, limites modulares, contratos, consistência, operação assíncrona, IA controlada e entrega ponta a ponta.

O primeiro incremento executável é local, autenticado e sem RAG. Não será usado por equipes reais nem com dados de trabalho.

## 2. Escopo

### Incluído no MVP local

- Workspaces sintéticos, membros, papéis `REQUESTER`, `AGENT` e `ADMIN`.
- Login OIDC por Keycloak local; cadastro/associação de usuários sem signup público.
- Solicitação autenticada, fila por workspace, histórico e transições auditadas.
- Triagem assíncrona com provedor falso por padrão e adaptador opcional OpenAI.
- Revisão humana obrigatória, classificação manual e caminho de recuperação.
- PostgreSQL, Flyway, RabbitMQ, outbox própria, inbox/idempotência e retry limitado.
- API REST documentada, React/TypeScript, Docker Compose e CI.
- Avaliação inicial com casos inteiramente sintéticos, sem meta de qualidade pré-fixada.

### Fora do MVP

RAG/pgvector, MCP, entrada pública/anônima, anexos, cobrança, notificações externas, integrações de clientes, microserviços, Kubernetes, event sourcing, banco de leitura separado e uso de dados reais. AWS é uma etapa opcional posterior, isolada e temporária, após aceite local e revisão de custo/segurança.

## 3. Atores e autorização

| Papel | Permissões |
| --- | --- |
| `REQUESTER` | Criar solicitações e ler somente as próprias solicitações no workspace associado. |
| `AGENT` | Ler a fila e solicitações do workspace; revisar sugestões; classificar manualmente; avançar estados operacionais válidos. |
| `ADMIN` | Permissões de agente, mais gerenciar membros, papéis e configuração do workspace. |
| Worker | Consumir eventos internos e executar somente triagem do workspace/request indicados no evento validado. |

Keycloak autentica a identidade. A associação e o papel no workspace são mantidos pela aplicação e consultados em cada operação; claims enviados pelo navegador não concedem acesso. Toda rota de recurso verifica workspace e permissão. Não existe token público de intake.

## 4. Linguagem do domínio

- **Workspace:** limite de isolamento, associação e autorização.
- **Solicitação:** texto original imutável, estado operacional e histórico.
- **Triagem:** tentativa de produzir uma sugestão; não é o estado da solicitação.
- **Sugestão:** categoria, urgência e resumo não confiáveis até revisão humana.
- **Revisão:** decisão de uma pessoa agente, registrada e vinculada à versão da sugestão.
- **Outbox:** registro durável da intenção de publicar evento, gravado na transação de negócio.

Categorias iniciais: `ACCESS`, `INCIDENT`, `QUESTION`, `SERVICE_REQUEST`, `OTHER`. Urgências: `LOW`, `NORMAL`, `HIGH`, `CRITICAL`. A sugestão deve ser conservadora: ausência de evidência explícita não autoriza elevar urgência. Não há SLA nem automação operacional baseada nesses valores.

## 5. Requisitos funcionais

### Identidade, workspace e acesso

- **FR-001 — Autenticar:** API aceita somente access tokens OIDC válidos emitidos pelo realm local configurado.
- **FR-002 — Autorizar por membership:** cada operação exige membership ativo e papel permitido no workspace. ID de workspace fornecido pelo cliente é seletor, nunca prova de autorização.
- **FR-003 — Isolar leitura:** requester vê apenas suas próprias solicitações; agent/admin veem recursos do workspace autorizado. Respostas e filtros não revelam existência de recurso fora do escopo.
- **FR-004 — Administrar membros:** somente admin pode associar/remover membros e alterar papel. Deve existir pelo menos um admin ativo por workspace.

### Solicitações e ciclo de vida

- **FR-005 — Criar idempotentemente:** criação exige `Idempotency-Key`. Chave é escopada por subject autenticado, workspace e operação. Repetição com mesmo corpo devolve a mesma confirmação; mesma chave com corpo diferente retorna conflito. Não persistir chave em claro; reter registro por pelo menos 24 horas.
- **FR-006 — Preservar origem:** corpo original é imutável. Correções são eventos/revisões separados, sem apagar a origem.
- **FR-007 — Consultar:** agent/admin podem filtrar fila por estado, categoria, urgência e data; requester só lista as próprias solicitações. Detalhe inclui histórico paginado e autorizado.
- **FR-008 — Transicionar:** estado operacional e tentativa de triagem são separados. Transições inválidas ou sobre versão desatualizada retornam conflito e não alteram dados.
- **FR-009 — Classificar manualmente:** agent/admin pode encaminhar uma solicitação para `OPEN` sem IA, registrando categoria, urgência, autor e motivo opcional.
- **FR-010 — Auditar:** registrar ator (pessoa ou sistema), instante, ação, versão, estado anterior/novo e correlation ID. Histórico não é editável pela API comum.

Estados de solicitação:

| De | Para | Quem/condição |
| --- | --- | --- |
| `RECEIVED` | `TRIAGE_PENDING` | Sistema ao agendar triagem automática |
| `RECEIVED`, `TRIAGE_PENDING`, `NEEDS_REVIEW` | `OPEN` | Agent/admin por classificação manual |
| `TRIAGE_PENDING` | `NEEDS_REVIEW` | Sistema após sugestão validada |
| `NEEDS_REVIEW` | `OPEN` | Agent/admin aprova ou edita e aprova sugestão |
| `NEEDS_REVIEW` | `RECEIVED` | Agent/admin rejeita sugestão; registrar rejeição e permitir triagem manual |
| `OPEN` | `IN_PROGRESS` | Agent/admin |
| `IN_PROGRESS` | `OPEN`, `RESOLVED` | Agent/admin; reabertura exige registro |

Tentativa de triagem: `QUEUED`, `PROCESSING`, `SUCCEEDED`, `RETRY_SCHEDULED`, `FAILED`, `OUTCOME_UNKNOWN`. Timeout após envio ao provedor pode significar que a chamada foi processada: marcar `OUTCOME_UNKNOWN`, não repetir automaticamente chamada potencialmente cobrada. Solicitação permanece utilizável manualmente.

### Triagem por IA

- **FR-011 — Produzir sugestão:** solicitar somente categoria, urgência e resumo curto. Não exigir nem armazenar raciocínio privado do modelo.
- **FR-012 — Validar no servidor:** validar schema, enums, limites de tamanho e estado da solicitação; schema estruturado do provedor não substitui validação de domínio.
- **FR-013 — Aprovação humana:** modelo não altera estado operacional. Somente agent/admin pode aceitar, editar e aceitar ou rejeitar uma sugestão válida.
- **FR-014 — Provedor substituível:** domínio depende de uma porta `TriageProvider`. Fake é o padrão local e obrigatório nos testes; chamadas reais são opt-in.
- **FR-015 — Proteger custo:** OpenAI real exige `OPENAI_LIVE_ENABLED=true`, chave em variável local não versionada e orçamento mensal da aplicação de US$5. Reservar margem/custo máximo antes da chamada, reconciliar uso informado após resposta e bloquear novas chamadas ao atingir o limite. Se o custo não puder ser estimado com segurança, bloquear a chamada. Limite do app não substitui limite configurado na conta do provedor.
- **FR-016 — Minimizar e rastrear:** enviar apenas texto necessário; guardar provedor/modelo, versões de schema e prompt, uso/custo reportado, tempo, status e correlation ID. Não registrar segredo, prompt completo ou corpo original em logs.
- **FR-017 — Avaliar baseline:** manter conjunto sintético rotulado por ambos os participantes, divergências reconciliadas e métricas de macro-F1/categoria, matriz de confusão, erro de urgência, schema válido, latência e custo. Apresentar baseline antes de escolher meta; não alegar precisão sem amostra e método.

### Eventos e entrega

- **FR-018 — Commit atômico:** persistir solicitação, auditoria necessária e evento outbox na mesma transação PostgreSQL.
- **FR-019 — Publicar com segurança:** evento persistente, estável e versionado contém IDs/correlation, nunca texto da solicitação. Publicador usa publisher confirms e `mandatory=true`; trata `basic.return` de mensagem não roteável e só marca publicação após confirmação sem retorno.
- **FR-020 — Consumir idempotentemente:** inbox com unicidade por consumidor/event ID; confirmar RabbitMQ somente após resultado/falha recuperável estar persistido.
- **FR-021 — Recuperar de falhas:** retries persistidos, limitados e com atraso para erros sabidamente transitórios. Mensagem inválida/poison vai à DLQ. Replay é ação explícita, autorizada, idempotente e auditada. Falha do broker não impede consulta e classificação manual.
- **FR-022 — Declarar semântica:** documentar entrega at-least-once; não prometer exactly-once. Cobrir crash após chamada de IA e antes de persistir resposta, cuja resolução pode ser `OUTCOME_UNKNOWN`.

## 6. Regras de negócio

- **BR-001:** workspace e membership ativo são obrigatórios em toda leitura/escrita de negócio.
- **BR-002:** requester nunca revisa sugestão nem acessa solicitação de outro requester.
- **BR-003:** texto original, decisão humana e sugestão são registros distintos.
- **BR-004:** nenhuma sugestão altera fila ou estado sem decisão humana.
- **BR-005:** cada sugestão e tentativa pertence a um único request/workspace; o modelo não define identidade, workspace ou permissão.
- **BR-006:** falha de IA/broker/cota não remove a solicitação nem impede classificação manual.
- **BR-007:** repetição de evento não duplica sugestão, decisão ou auditoria de negócio.
- **BR-008:** payload de broker não contém texto submetido, segredos ou prompt.
- **BR-009:** todas as fixtures, avaliações e demos usam dados sintéticos identificados como tais.

## 7. Contrato HTTP mínimo

Detalhamento e envelope de erro em [`api-contract.md`](api-contract.md). Todos os endpoints exigem Bearer OIDC, exceto health checks sem dados de negócio.

- `POST /api/v1/workspaces/{workspaceId}/requests` — criar; exige `Idempotency-Key`.
- `GET /api/v1/workspaces/{workspaceId}/requests` — fila ou solicitações próprias; paginação por cursor.
- `GET /api/v1/workspaces/{workspaceId}/requests/{requestId}` — detalhe e histórico autorizado.
- `POST .../{requestId}/reviews` — aprovar, editar/aprovar ou rejeitar; exige versão esperada.
- `POST .../{requestId}/manual-classification` — classificar sem IA.
- `POST .../{requestId}/triage-retries` — reprocesso explícito por agent/admin, apenas quando elegível.

## 8. Requisitos de qualidade

- **NFR-001 — Reprodutibilidade:** ambiente local sobe a partir de instruções versionadas, health checks e configuração de exemplo, sem segredo real.
- **NFR-002 — Isolamento verificável:** testes de autorização negativos cobrem rotas, consultas, eventos e relações de banco multi-workspace.
- **NFR-003 — Observabilidade:** logs estruturados com request/event/correlation IDs, métricas de idade da outbox, fila, tentativas, falha/custo IA e DLQ; sem conteúdo sensível.
- **NFR-004 — Concorrência:** alteração de estado usa versão otimista; duas revisões concorrentes não podem ambas vencer.
- **NFR-005 — Recuperabilidade:** documentar operação de retry/replay, indisponibilidade Rabbit/IA e backup/restauração local; verificar restauração antes de encerrar milestone operacional.
- **NFR-006 — Avaliação:** medir baseline de IA em dataset sintético versionado; reportar tamanho, método e limitações junto das métricas.
- **NFR-007 — Modularidade:** dependências entre módulos são verificadas automaticamente; detalhes internos de módulo não são importados por outros módulos.

## 9. Cenários de aceite prioritários

1. **Acesso cruzado:** dado usuário do workspace A, quando pede ID de B em path/filtro/body, então não lê nem altera B e resposta não confirma existência.
2. **Requester limitado:** dado requester autenticado, quando lista, então vê somente próprias solicitações; tentar revisar/classificar retorna proibido.
3. **Idempotência:** mesmo subject/workspace/chave/corpo retorna mesmo request; chave/corpo divergente retorna conflito.
4. **Concorrência:** duas revisões usam mesma versão; uma vence, a outra recebe conflito e não sobrescreve a decisão.
5. **Outbox:** broker indisponível após commit; solicitação existe e outbox pendente publica após recuperação.
6. **Roteamento:** publish sem binding recebe retorno e permanece pendente/visível para operação, mesmo se houve publisher confirm.
7. **Redelivery:** worker cai após commit antes do ack; redelivery não duplica efeito de domínio.
8. **Falha ambígua de IA:** timeout após envio não dispara retry automático; tentativa fica `OUTCOME_UNKNOWN`, classificação manual funciona.
9. **Limite de custo:** reserva que excede limite mensal bloqueia provedor real; fake segue disponível.
10. **Saída inválida:** JSON/schema/enum/limite inválido não vira sugestão revisável.
11. **Aprovação:** sugestão válida só entra em `OPEN` após decisão de agent/admin e histórico registra ambos os valores.
12. **Sem dados reais:** fixtures, logs, capturas e avaliação não incluem dados pessoais ou corporativos.

## 10. Referências normativas e decisão pendente

Decisões fixadas para esta versão: Keycloak OIDC local; papéis mantidos pela aplicação; entrada autenticada; OpenAI como adaptador opcional com fake padrão e teto US$5/mês; outbox própria; Spring Modulith para verificação; avaliação sem limiar prévio. Não reabrir como tarefa bloqueadora.

Questões a resolver antes de operação além do demo: retenção/exclusão para qualquer eventual piloto, região e orçamento cloud, modelo OpenAI concreto e seu preço atual, limites de texto e paginação com base na avaliação de usabilidade. Não são necessárias para implementar o slice local.
