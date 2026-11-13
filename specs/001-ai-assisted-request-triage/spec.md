# Spec 001: Triagem assistida de solicitações

> **Para estudar:** esta spec é um caso fictício para praticar análise de requisitos. Leia primeiro atores, estados e cenários; depois explique, com suas palavras, por que a IA pode pedir complemento mas não pode encerrar nem classificar definitivamente um pedido. Nenhuma regra abaixo foi validada em atendimento real.

- **Estado:** pronta para revisão dos participantes; nada implementado. A [Spec 002](../002-service-workflow/spec.md) evolui o atendimento depois da base manual; a [Spec 003](../003-ai-chat-resolution/spec.md) acrescenta a conversa com IA como porta de entrada alternativa.
- **Data:** 2026-11-09
- **Alvo:** MVP didático local, com dados sintéticos
- **Fonte de verdade:** este documento define comportamento. [`plan.md`](plan.md) define a abordagem técnica, [`api-contract.md`](api-contract.md) o contrato HTTP e [`tasks.md`](tasks.md) rastreia execução.

## 1. Problema e objetivo

Equipes internas de suporte técnico recebem solicitações em texto livre. Queremos que um agente de IA faça a primeira análise: se identificar um pedido técnico compreensível, encaminha à revisão humana com sugestão de categoria, urgência e resumo; se o texto for vago ou não apresentar problema técnico identificável, pede um complemento ao solicitante. A IA não descarta pedidos nem decide a classificação final.

O produto é um exercício de arquitetura e engenharia para dois desenvolvedores Java: domínio, limites modulares, contratos, consistência, operação assíncrona, IA controlada e entrega ponta a ponta. O primeiro incremento executável é local, autenticado e sem RAG. Não será usado por equipes reais nem com dados de trabalho.

Exemplo sintético: Ana não consegue acessar um ambiente de desenvolvimento. Ela abre uma solicitação no workspace A; a análise inicial reconhece um problema de acesso, sugere `ACCESS`/`HIGH` e coloca o pedido na fila de revisão humana. Uma pessoa agente confirma ou corrige a classificação antes de qualquer atendimento começar.

## 2. Escopo

### Incluído no MVP local

- Workspaces sintéticos, membros e papéis `REQUESTER`, `AGENT` e `ADMIN`.
- Login OIDC por Keycloak local; associação de usuários sem signup público.
- Solicitação autenticada, fila por workspace, detalhe, histórico e transições auditadas.
- Triagem assíncrona inicial pela IA, com decisão de encaminhar à revisão humana ou pedir um complemento; provedor falso por padrão e adaptador opcional OpenAI.
- Revisão humana obrigatória, classificação manual e caminho de recuperação quando a IA ou a infraestrutura falha.
- PostgreSQL, Flyway, RabbitMQ, outbox própria, inbox/idempotência e retry limitado.
- API REST documentada, interface React/TypeScript mínima, Docker Compose e CI.
- Avaliação inicial em conjunto inteiramente sintético, sem meta de qualidade pré-fixada.

### Fora do MVP

RAG/pgvector, MCP, entrada pública ou anônima, anexos, cobrança, notificações externas, integrações de clientes, microserviços, Kubernetes, event sourcing, banco de leitura separado e qualquer dado real. AWS é etapa opcional posterior, isolada e temporária, após aceite local e revisão de custo e segurança. Prioridade, prazo, responsável e reabertura pertencem à Spec 002; a conversa assistida pertence à Spec 003.

## 3. Atores e autorização

| Papel | Permissões |
| --- | --- |
| `REQUESTER` | Criar solicitações, ler somente as próprias, complementar informação pedida na triagem ou pedir revisão humana. |
| `AGENT` | Ler fila e solicitações do workspace; revisar sugestões; classificar manualmente; avançar estados operacionais válidos. |
| `ADMIN` | Permissões de agente, mais gerenciar membros, papéis e configuração do workspace. |
| Worker | Consumir eventos internos e executar somente a triagem do workspace/solicitação indicados em evento validado. |

Keycloak autentica a identidade. Associação e papel no workspace são mantidos pela aplicação e consultados em cada operação; claims enviados pelo navegador não concedem acesso. Toda rota de recurso verifica workspace e permissão. Não existe token público de intake.

## 4. Linguagem do domínio

- **Workspace:** limite de isolamento, associação e autorização.
- **Solicitação:** texto original imutável, estado operacional e histórico.
- **Fila do agente de IA:** solicitações em `RECEIVED`/`TRIAGE_PENDING` aguardando ou recebendo análise inicial.
- **Fila de revisão humana:** solicitações em `NEEDS_REVIEW`, com sugestão ou com motivo seguro de encaminhamento sem sugestão.
- **Fila de atendimento:** solicitações em `OPEN` ou `IN_PROGRESS`, após decisão humana.
- **Triagem:** tentativa de produzir uma sugestão; não é o estado da solicitação.
- **Complemento:** texto adicional do solicitante, separado do original imutável. Há no máximo uma rodada automática de pedido de complemento.
- **Sugestão:** categoria, urgência e resumo não confiáveis até a revisão humana.
- **Revisão:** decisão de uma pessoa agente, registrada e vinculada à versão da sugestão.
- **Outbox:** registro durável da intenção de publicar evento, gravado na transação de negócio.

Categorias iniciais: `ACCESS`, `INCIDENT`, `QUESTION`, `SERVICE_REQUEST`, `OTHER`. Urgências: `LOW`, `NORMAL`, `HIGH`, `CRITICAL`. A sugestão deve ser conservadora: ausência de evidência explícita não autoriza elevar urgência. Não há SLA nem automação operacional baseada nesses valores nesta spec.

**Faz sentido encaminhar ao humano** quando o texto apresenta problema ou pedido técnico concreto, com sintoma ou objetivo compreensível para uma pessoa agente — por exemplo: "Ao entrar na conta de teste, recebo acesso negado". Texto vago ("Não funciona") ou sem pedido técnico identificável recebe uma pergunta objetiva de reformulação. A IA nunca pede senha, token ou outro segredo. Se não conseguir decidir com segurança, encaminha ao humano. A rota não é julgamento sobre mérito, prioridade ou direito a atendimento.

## 5. Requisitos funcionais

### Identidade, workspace e acesso

- **FR-001 — Autenticar:** a API aceita somente access tokens OIDC válidos emitidos pelo realm local configurado.
- **FR-002 — Autorizar por membership:** cada operação exige membership ativo e papel permitido no workspace. ID de workspace informado pelo cliente é seletor, nunca prova de autorização.
- **FR-003 — Isolar leitura:** requester vê apenas as próprias solicitações; agent/admin veem recursos do workspace autorizado. Respostas e filtros não revelam a existência de recurso fora do escopo.
- **FR-004 — Administrar membros:** somente admin associa ou remove membros e altera papel. Deve existir pelo menos um admin ativo por workspace.

### Solicitações e ciclo de vida

- **FR-005 — Criar idempotentemente:** a criação exige `Idempotency-Key`, escopada por subject autenticado, workspace e operação. Repetição com o mesmo corpo devolve a mesma confirmação; mesma chave com corpo diferente retorna conflito. Não persistir a chave em claro; reter o registro por pelo menos 24 horas.
- **FR-006 — Preservar origem:** o texto original é imutável. Correções são registros separados, sem apagar a origem.
- **FR-007 — Consultar:** agent/admin filtram a fila por estado, categoria, urgência e data; requester lista somente as próprias solicitações. O detalhe inclui histórico paginado e autorizado.
- **FR-008 — Transicionar:** estado operacional e tentativa de triagem são separados. Transição inválida ou sobre versão desatualizada retorna conflito e não altera dados.
- **FR-009 — Classificar manualmente:** agent/admin pode levar uma solicitação a `OPEN` sem sugestão de IA, registrando categoria, urgência e autor. Fora de `NEEDS_REVIEW` o bypass é explícito e exige motivo (por exemplo, broker indisponível); a UI comum recebe primeiro a rota da IA.
- **FR-010 — Auditar:** registrar ator (pessoa ou sistema), instante, ação, versão, estado anterior e novo e correlation ID. O histórico não é editável pela API comum.

Estados da solicitação:

| De | Para | Quem e condição |
| --- | --- | --- |
| `RECEIVED` | `TRIAGE_PENDING` | Sistema, ao agendar a triagem automática |
| `RECEIVED`, `TRIAGE_PENDING`, `NEEDS_REVIEW`, `NEEDS_CLARIFICATION` | `OPEN` | Agent/admin por classificação manual; fora da fila humana exige motivo de bypass |
| `TRIAGE_PENDING` | `NEEDS_REVIEW` | Sistema, quando a IA encaminha ao humano, está incerta ou falha; sugestão é opcional |
| `TRIAGE_PENDING` | `NEEDS_CLARIFICATION` | Sistema, quando falta informação; registra pergunta validada, sem descartar o pedido |
| `NEEDS_CLARIFICATION` | `TRIAGE_PENDING` | Requester envia complemento; gravar texto e nova intenção de triagem atomicamente |
| `NEEDS_CLARIFICATION` | `NEEDS_REVIEW` | Requester pede pessoa agente, ou agent/admin faz override; sem sugestão obrigatória |
| `NEEDS_REVIEW` | `OPEN` | Agent/admin aprova, ou edita e aprova, a sugestão |
| `NEEDS_REVIEW` | `RECEIVED` | Agent/admin rejeita a sugestão; registrar rejeição e permitir triagem manual |
| `OPEN` | `IN_PROGRESS` | Agent/admin |
| `IN_PROGRESS` | `OPEN`, `RESOLVED` | Agent/admin; retorno a `OPEN` exige motivo registrado |

`RESOLVED` é terminal nesta spec; a [Spec 002](../002-service-workflow/spec.md) introduz reabertura com janela e novo ciclo. Rejeitar sugestão e voltar a `RECEIVED` não agenda nova triagem automaticamente: reprocesso exige comando explícito elegível.

Após uma rodada de complemento, se a IA ainda não conseguir encaminhar com segurança, o pedido vai a `NEEDS_REVIEW` sem nova pergunta automática. Falha, saída inválida, incerteza ou cota esgotada também encaminham ao humano sem sugestão. Indisponibilidade do broker permite bypass manual explícito, com o evento pendente visível para operação. Nenhuma rota da IA fecha, apaga ou rejeita o pedido definitivamente.

Tentativa de triagem: `QUEUED`, `PROCESSING`, `SUCCEEDED`, `RETRY_SCHEDULED`, `FAILED`, `OUTCOME_UNKNOWN`, `OBSOLETE`. `OBSOLETE` significa que uma decisão humana venceu enquanto a tentativa processava; resultado tardio não gera sugestão revisável nem altera a solicitação. Timeout após o envio ao provedor pode significar que a chamada foi processada: marcar `OUTCOME_UNKNOWN` e não repetir automaticamente uma chamada possivelmente cobrada. A solicitação permanece utilizável manualmente.

### Triagem por IA

- **FR-011 — Fazer a primeira análise:** a IA retorna `HUMAN_REVIEW` com categoria, urgência e resumo curto quando identifica pedido técnico compreensível, ou `REQUEST_CLARIFICATION` com uma pergunta curta quando o texto está vago ou sem problema técnico identificável. Não exigir nem armazenar raciocínio privado do modelo. A rota é decisão de preparação; a classificação final permanece humana.
- **FR-012 — Validar no servidor:** validar rota, campos obrigatórios por rota, enums, pergunta e resumo, limites de tamanho e estado da solicitação. Schema estruturado do provedor não substitui validação de domínio. Incerteza ou resposta inválida segue ao humano sem sugestão.
- **FR-013 — Exigir aprovação humana:** a IA pode levar a `NEEDS_REVIEW` ou `NEEDS_CLARIFICATION`, mas não abre atendimento nem define classificação final. Somente agent/admin aceita, edita e aceita, ou rejeita uma sugestão válida; sem sugestão, classifica manualmente.
- **FR-014 — Manter provedor substituível:** o domínio depende da porta `TriageProvider`. O fake é padrão local e obrigatório nos testes; chamadas reais são opt-in.
- **FR-015 — Proteger custo:** OpenAI real exige `OPENAI_LIVE_ENABLED=true`, chave em variável local não versionada e orçamento mensal de aplicação de US$5. Reservar custo máximo antes da chamada, reconciliar o uso informado após a resposta e bloquear novas chamadas ao atingir o limite. Se o custo não puder ser estimado com segurança, bloquear a chamada. O limite da aplicação não substitui o limite configurado na conta do provedor.
- **FR-016 — Minimizar e rastrear:** enviar apenas o texto necessário; guardar provedor, modelo, versões de schema e prompt, uso e custo reportados, tempo, status e correlation ID. Não registrar segredo, prompt completo ou corpo original em logs.
- **FR-017 — Medir baseline:** manter conjunto sintético rotulado pelos dois participantes, com exemplos suficientes e insuficientes, divergências reconciliadas e métricas de encaminhamento/clarificação, macro-F1 por categoria, matriz de confusão, erro de urgência, validade de schema, latência e custo. Medir especialmente pedidos úteis retidos para complemento. Apresentar baseline antes de escolher meta; não alegar precisão sem amostra e método.
- **FR-023 — Complementar ou pedir pessoa agente:** em `NEEDS_CLARIFICATION`, o requester autor pode enviar um complemento imutável e acionar uma única nova análise; também pode pedir revisão humana direta. A pergunta da IA não pode pedir credenciais ou segredos. Não aceitar texto de outro requester nem duplicar evento por repetição da mesma chave.
- **FR-024 — Encaminhar falha e incerteza:** saída inválida, incerteza, falha terminal, `OUTCOME_UNKNOWN` ou cota bloqueada não deixam o pedido escondido na fila da IA. Encaminhar a `NEEDS_REVIEW` com motivo seguro e sem sugestão, exceto quando a infraestrutura ainda tem retry conhecido e limitado em curso.

### Eventos e entrega

- **FR-018 — Commit atômico:** persistir solicitação, auditoria necessária e evento de outbox na mesma transação PostgreSQL.
- **FR-019 — Publicar com segurança:** evento persistente, estável e versionado contém IDs e correlation, nunca o texto da solicitação. O publicador usa publisher confirms e `mandatory=true`, trata `basic.return` de mensagem não roteável e só marca publicação após confirmação sem retorno.
- **FR-020 — Consumir idempotentemente:** inbox com unicidade por consumidor e event ID; confirmar no RabbitMQ somente após o resultado ou a falha recuperável estar persistido.
- **FR-021 — Recuperar de falhas:** retries persistidos, limitados e com atraso para erros sabidamente transitórios. Mensagem inválida ou poison vai à DLQ. Replay é ação explícita, autorizada, idempotente e auditada. Falha do broker não impede consulta nem classificação manual.
- **FR-022 — Declarar semântica:** documentar entrega *at-least-once*; não prometer exactly-once. Cobrir crash após a chamada de IA e antes de persistir a resposta, cuja resolução pode ser `OUTCOME_UNKNOWN`.

## 6. Regras de negócio

- **BR-001:** workspace e membership ativo são obrigatórios em toda leitura e escrita de negócio.
- **BR-002:** requester nunca revisa sugestão nem acessa solicitação de outro requester.
- **BR-003:** texto original, decisão humana e sugestão são registros distintos.
- **BR-004:** a IA decide somente a rota inicial (`NEEDS_REVIEW` ou `NEEDS_CLARIFICATION`); não define classificação final, não leva a `OPEN` e não altera a fila de atendimento sem decisão humana.
- **BR-005:** cada sugestão e tentativa pertence a uma única solicitação e workspace; o modelo não define identidade, workspace ou permissão.
- **BR-006:** falha de IA, broker ou cota não remove a solicitação nem impede a classificação manual.
- **BR-007:** repetição de evento não duplica sugestão, decisão ou auditoria de negócio.
- **BR-008:** payload de broker não contém texto submetido, segredos ou prompt.
- **BR-009:** todas as fixtures, avaliações e demos usam dados sintéticos identificados como tais.
- **BR-010:** pedido em clarificação continua visível ao próprio requester e a agent/admin; o requester pode pedir revisão humana sem satisfazer a IA. Depois de um complemento, não há segunda retenção automática para clarificação.

## 7. Contrato HTTP mínimo

Detalhamento e envelope de erro em [`api-contract.md`](api-contract.md). Todos os endpoints exigem Bearer OIDC, exceto health checks sem dados de negócio.

- `POST /api/v1/workspaces/{workspaceId}/requests` — criar; exige `Idempotency-Key`.
- `GET /api/v1/workspaces/{workspaceId}/requests` — fila ou solicitações próprias; paginação por cursor.
- `GET /api/v1/workspaces/{workspaceId}/requests/{requestId}` — detalhe e histórico autorizado.
- `POST .../{requestId}/reviews` — aprovar, editar e aprovar, ou rejeitar; exige versão esperada.
- `POST .../{requestId}/manual-classification` — classificar sem IA.
- `POST .../{requestId}/triage-retries` — reprocesso explícito por agent/admin, apenas quando elegível.
- `POST .../{requestId}/clarifications` — requester complementa informação uma vez; exige versão e chave idempotente.
- `POST .../{requestId}/human-review-requests` — requester pede encaminhamento humano sem complemento.
- `POST .../{requestId}/status-transitions` — transições operacionais da tabela acima.

## 8. Requisitos de qualidade

- **NFR-001 — Reprodutibilidade:** o ambiente local sobe a partir de instruções versionadas, com health checks e configuração de exemplo, sem segredo real.
- **NFR-002 — Isolamento verificável:** testes de autorização negativos cobrem rotas, consultas, eventos e relações de banco multi-workspace.
- **NFR-003 — Observabilidade:** logs estruturados com request, event e correlation IDs; métricas de idade da outbox, fila, tentativas, falha e custo de IA e DLQ, sem conteúdo sensível.
- **NFR-004 — Concorrência:** alteração de estado usa versão otimista; duas revisões concorrentes não podem ambas vencer.
- **NFR-005 — Recuperabilidade:** documentar retry e replay, indisponibilidade de Rabbit e IA e backup/restauração local; verificar a restauração antes de encerrar o milestone operacional.
- **NFR-006 — Avaliação:** medir baseline de IA em dataset sintético versionado; reportar tamanho, método e limitações junto das métricas.
- **NFR-007 — Modularidade:** dependências entre módulos são verificadas automaticamente; detalhes internos de um módulo não são importados por outro.

## 9. Cenários de aceite prioritários

1. **Acesso cruzado:** dado usuário do workspace A, quando informa ID de B em path, filtro ou body, então não lê nem altera B e a resposta não confirma existência.
2. **Requester limitado:** dado requester autenticado, quando lista, então vê somente as próprias solicitações; tentar revisar ou classificar retorna proibido.
3. **Idempotência:** mesmo subject, workspace, chave e corpo retornam a mesma solicitação; chave repetida com corpo divergente retorna conflito.
4. **Concorrência:** duas revisões usam a mesma versão; uma vence e a outra recebe conflito sem sobrescrever a decisão.
5. **Outbox:** broker indisponível após o commit; a solicitação existe e a outbox pendente publica depois da recuperação.
6. **Roteamento:** publish sem binding recebe retorno e permanece pendente e visível para operação, mesmo havendo publisher confirm.
7. **Redelivery:** worker cai após o commit e antes do ack; a redelivery não duplica efeito de domínio.
8. **Falha ambígua de IA:** timeout após o envio não dispara retry automático; a tentativa fica `OUTCOME_UNKNOWN` e a classificação manual funciona.
9. **Limite de custo:** reserva que excede o limite mensal bloqueia o provedor real; o fake segue disponível.
10. **Saída inválida:** JSON, schema, enum ou limite inválido não vira sugestão revisável.
11. **Aprovação:** sugestão válida só entra em `OPEN` após decisão de agent/admin, e o histórico registra o valor sugerido e o aprovado.
12. **Sem dados reais:** fixtures, logs, capturas e avaliação não incluem dado pessoal ou corporativo.
13. **Corrida manual versus IA:** classificação manual em `TRIAGE_PENDING` vence o resultado tardio do worker; a tentativa vira `OBSOLETE` e o pedido permanece `OPEN` com a decisão humana.
14. **Rejeição:** rejeitar sugestão retorna a `RECEIVED` sem novo evento automático de triagem; o caminho manual continua disponível.
15. **IA antes da pessoa:** a criação agenda a análise; resultado `HUMAN_REVIEW` põe o pedido em `NEEDS_REVIEW` com sugestão, sem abrir atendimento.
16. **Falta de contexto:** resultado `REQUEST_CLARIFICATION` põe o pedido em `NEEDS_CLARIFICATION`; o requester complementa e nova análise ocorre no máximo uma vez.
17. **Saída de segurança:** o requester pede humano, ou a IA falha ou permanece incerta após o complemento; o pedido chega a `NEEDS_REVIEW` sem sugestão e pode ser classificado manualmente.
18. **Rota explicável:** "Não funciona" gera pergunta objetiva sem pedir segredo; "Recebo acesso negado ao entrar na conta de teste" segue à revisão humana. Erro de rota no conjunto rotulado é reportado na avaliação, não ocultado como sucesso.

## 10. Segurança, privacidade e custo

Entrada autenticada apenas; nenhuma rota pública de intake. Texto submetido não trafega em evento nem em log. Prompt injection é tratada como entrada hostil: a saída do modelo é validada contra enum e limites, e pedido de credencial invalida a resposta. Os dados são sintéticos; retenção, exclusão e base legal precisariam ser especificados antes de qualquer piloto real. Custo de IA tem teto de aplicação e exige opt-in; o fake não gera custo.

## 11. Decisões fixadas e questões abertas

Fixado nesta versão: Keycloak OIDC local; papéis mantidos pela aplicação; entrada autenticada; OpenAI como adaptador opcional, com fake padrão e teto de US$5/mês; outbox própria; Spring Modulith para verificação; avaliação sem limiar prévio. Não reabrir como gate do MVP.

A resolver antes de qualquer operação além do demo: retenção e exclusão para eventual piloto, região e orçamento de cloud, modelo OpenAI concreto e seu preço atual, limites de texto e paginação conforme avaliação de usabilidade. Nada disso bloqueia o slice local.

## 12. Referências

[Constituição](../../.specify/memory/constitution.md) · [ADR-0001](../../docs/architecture/decisions/ADR-0001-modular-monolith.md) · [ADR-0002](../../docs/architecture/decisions/ADR-0002-outbox-rabbitmq.md) · [ADR-0003](../../docs/architecture/decisions/ADR-0003-human-reviewed-ai.md) · [ADR-0005](../../docs/architecture/decisions/ADR-0005-oidc-and-workspace-membership.md) · [Domínio e dados](../../docs/architecture/domain-and-data.md) · [Modelo de ameaças](../../docs/security/threat-model.md)
