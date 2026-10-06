# Plano técnico — Spec 001

- **Estado:** proposta técnica pronta para revisão; aplicação ainda não existe.
- **Restrição principal:** entregar vertical slices locais, cada um com comportamento, testes, documentação e demonstração.
- **Spec:** [`spec.md`](spec.md) · **API:** [`api-contract.md`](api-contract.md) · **Modelo:** [`../../docs/architecture/domain-and-data.md`](../../docs/architecture/domain-and-data.md)

## 1. Arquitetura alvo do MVP

Monólito modular Spring Boot com PostgreSQL como fonte de verdade. Spring Modulith verifica dependências/ciclos entre módulos na build. RabbitMQ executa a triagem lenta, com outbox/inbox próprios porque controlar consistência e redelivery é objetivo de aprendizagem. CQRS é leve: comandos protegem invariantes; consultas otimizam DTOs de fila/detalhe no mesmo banco. Sem event sourcing ou banco de leitura separado.

### Módulos e propriedade

| Módulo | Responsabilidade / dados próprios | Dependências permitidas |
| --- | --- | --- |
| `identity` | Resolver principal autenticado e acesso a workspace/membership/papel | Adaptador OIDC e porta consultada pelos casos de uso |
| `requests` | Aggregate de solicitação, estados, auditoria, fila e comandos manuais | Porta de autorização; publica intenção de triagem via API local/outbox |
| `triage` | Tentativas, sugestão, validação, aprovação, orçamento e porta do provedor | `requests` por API pública, não por tabela interna |
| `messaging` | Outbox, dispatcher Rabbit, inbox, retry, DLQ e métricas | Contratos de evento versionados e casos de uso públicos |
| `api` | REST, validação de transporte, mapeamento de erros e OpenAPI | APIs públicas de aplicação |

Cada módulo guarda internals privados e expõe tipos/casos de uso mínimos. Spring Modulith verifica ausência de ciclos, acesso a internals e dependências pretendidas: [verificação oficial](https://docs.spring.io/spring-modulith/reference/verification.html). Não criar módulo knowledge no MVP.

## 2. Stack e ambiente local

- Java LTS compatível com a versão Spring Boot escolhida na implementação; Maven Wrapper versionado.
- Spring Boot: Web, Validation, Security OAuth2 Resource Server, Data/JDBC conforme decisão de implementação, Actuator e Spring Modulith.
- PostgreSQL + Flyway. Restrições e chaves compostas tenant-aware onde apropriado; aplicação continua obrigada a autorizar cada operação.
- RabbitMQ com exchange/queue duráveis, mensagens persistentes, routing key versionada, DLQ e TTL/parking queue de retry quando viável.
- Keycloak local via realm importado de fixture sintética; React + TypeScript; Docker Compose; CI com build, testes e verificação de módulos.
- Configuração `.env.example` sem valores secretos. `.env`, tokens, chaves e dump local ficam ignorados pelo Git.

Compose sobe PostgreSQL, RabbitMQ, Keycloak, API e UI com health checks e comando/documentação de bootstrap. Configurar OIDC issuer/audience externamente. Spring Resource Server valida assinatura, issuer, expiração e audience; membership/role é consultado pela aplicação e não inferido de claim do navegador.

## 3. Contratos do domínio e persistência

Implementar regras de transição como funções explícitas/testáveis do aggregate/caso de uso. Escritas levam `expectedVersion`; uma atualização condicional incrementa versão e impede duas revisões concorrentes. Cada operação de negócio e auditoria correspondente ocorre na mesma transação.

Esboço lógico, não esquema SQL final:

- `workspace`, `membership(workspace_id, subject, role, active)`.
- `request(workspace_id,id,requester_subject,original_text,status,version,created_at,updated_at)`.
- `request_audit(workspace_id,request_id,actor,action,from_state,to_state,version,correlation_id,created_at,metadata)`.
- `triage_attempt(workspace_id,id,request_id,attempt_no,status,provider,model,safe_error_code,usage,cost,...)`.
- `triage_suggestion(workspace_id,id,request_id,attempt_id,schema_version,prompt_version,category,urgency,summary,validation_status,...)`.
- `triage_review(workspace_id,id,request_id,suggestion_id,reviewer_subject,decision,edited_values,...)`.
- `idempotency_record(scope_hash,key_hash,request_hash,response_ref,expires_at)`.
- `outbox_event(event_id,event_type,schema_version,aggregate_id,workspace_id,payload,status,attempts,next_attempt_at,lease_until,...)`.
- `inbox_event(consumer,event_id,processed_at,result_ref)` unique por `(consumer,event_id)`.
- `ai_budget_period(period,limit_usd,reserved_usd,reported_usd,blocked)` e `ai_usage` com valores minimizados.

Usar FK compostas `(workspace_id, request_id)` em tabelas dependentes para bloquear associação cruzada no banco. A autenticação e autorização continuam no caso de uso. Idempotency hash é calculado sobre canonicalização versionada do request; nunca guardar valor original da chave. Manter dados de request fora do evento e fora dos logs. Definir limites de texto/paginação na primeira implementação e refletir no OpenAPI.

## 4. Fluxo assíncrono e falhas

1. API valida token, membership, papel, payload e `Idempotency-Key`.
2. Uma transação grava request `RECEIVED`, evento de auditoria, outbox `TriageRequested.v1` e resultado idempotente.
3. Dispatcher reivindica lote pequeno por lease/lock, publica mensagem persistente com `mandatory=true` e `correlationId`.
4. Registrar handlers de `basic.return` e publisher confirm. Confirmação sem retorno permite marcar publicado; retorno de não roteável mantém evento pendente com alerta/erro operacional. Não confundir broker confirm com roteamento.
5. Worker valida envelope/versão, reinspeciona membership/status pelo caso de uso do sistema e obtém lock/idempotência. Faz a chamada de triagem fora da transação de banco.
6. Ao receber resposta, valida schema e regras e grava tentativa, sugestão ou falha antes do ACK. Inbox e efeito são atômicos no PostgreSQL.
7. Erro transitório conhecido (p.ex. 429 com retry-after ou indisponibilidade antes de envio) agenda retry limitado com `next_attempt_at`; erro permanente vai a DLQ. Erro de transporte depois do envio, sem saber se provedor concluiu, resulta em `OUTCOME_UNKNOWN`, sem retry automático.
8. Operador/agent autorizado pode iniciar reprocesso explícito, com aviso de possível duplicação/custo e nova tentativa auditada.

O delivery é at-least-once. Há janela inevitável entre efeito externo e persistência local: nenhuma chave de inbox elimina cobrança duplicada do provedor se o processo cair depois da resposta. Por isso timeout ambíguo é tratado como estado, não como retry cego. Retenção, reprocesso e remoção de mensagem devem ser documentados.

## 5. IA, avaliação e orçamento

Interface interna `TriageProvider` retorna objeto limitado: category, urgency, summary. Adapter fake fornece resultados determinísticos e não usa rede. Adapter OpenAI usa API de respostas com saída estruturada/JSON Schema estrito quando habilitado; o servidor valida domínio/schema mesmo assim. Referência de formato: [Structured Outputs (OpenAI)](https://developers.openai.com/api/docs/guides/structured-outputs).

Configuração live exige `OPENAI_LIVE_ENABLED=true`, `OPENAI_API_KEY`, `OPENAI_MODEL` fixado explicitamente e uso de dados sintéticos. Teto didático: US$5/mês no contador do app. Para chamadas concorrentes, reservar custo conservador antes do envio com preço e tokens máximos configurados, impedir overspend, reconciliar uso/custo reportado depois; parada fechada se preço/modelo não estiver configurado. Configurar também limite de gasto no projeto OpenAI. Fake fica como opção padrão em dev/CI.

Dataset inicial versionado em formato sem dados pessoais. Dois rótulos independentes e adjudicação documentada; separar exemplos de desenvolvimento e avaliação. Reportar macro-F1 e matriz por categoria, urgência (erro ordinal e matriz), schema-valid rate, latência e custo por item. Sumarização recebe rubric factual com amostra revisada manualmente. Resultado inicial vira baseline; só depois discutir limiar útil ao objetivo didático. Sem alegar que benchmark sintético prova desempenho em operação real.

## 6. Segurança e observabilidade

- `workspaceId` vem de rota, mas membership ativo é a prova de autorização. Toda query por ID filtra workspace; retorno para não autorizado não enumera.
- Não aceitar role/workspace claims como autorização. Não persistir access tokens.
- Segredos via env local ignorada/secret store temporário, jamais source, frontend ou log.
- Não logar texto original, prompt completo ou output integral; IDs aleatórios, estado, erro seguro, tamanhos, latência, custo e correlation.
- Métricas: backlog/idade outbox; returned/unroutable; publish confirm; redelivery; tempo de fila/processamento; tentativas, unknown, DLQ; cota reservada/consumida; taxa de schema válido.
- Logs e métricas não carregam workspace name ou conteúdo livre; usar IDs técnicos com retenção curta.

## 7. Entrega colaborativa e sequência didática

Trabalhar em fatias verticais. Em cada fatia, definir driver e reviewer; alternar a cada incremento. Os dois implementam e revisam backend e frontend ao longo do projeto, com ao menos uma autoria e uma revisão de PR em cada camada por pessoa. O driver apresenta requisito, trade-off, evidência de teste e limitação; reviewer registra riscos e alternativa. PR identifica os requisitos e autoria individual sem trailer de coautoria.

Sequência de aprendizagem: linguagem/invariantes e estados → boundaries/DDD → API e idempotência → OIDC/tenant → transação/concorrência → outbox/eventos/Rabbit/retry → adaptador IA/orçamento/avaliação → observabilidade, threat model e demo. DDD começa em linguagem ubíqua, bounded contexts e invariantes; CQRS separa intenção de alteração e leitura, sem impor complexidade de infraestrutura.

## 8. Extensões futuras, separadas por especificação

- **RAG:** somente após baseline sem retrieval. Nova spec deve cobrir corpus sintético, ingestão/reindex/deleção, filtro tenant antes da busca, referências verificáveis, prompt injection e métricas de retrieval/grounding. Pode então avaliar pgvector.
- **MCP:** spec própria, opcional, read-only inicialmente, usuário OIDC propagado, workspace autorizado em toda ferramenta, limites, audit e sem SQL/shell.
- **AWS:** sandbox temporário após demo local aceita; Terraform, orçamento revisto no dia, ingress restrito, secrets, alertas, backup/restore e teardown verificado. Sem dados reais/piloto.

## Fontes técnicas

- [Spring Modulith — Verifying Application Modules](https://docs.spring.io/spring-modulith/reference/verification.html)
- [RabbitMQ — Publisher Confirms and Returns](https://www.rabbitmq.com/docs/publishers)
- [OpenAI — Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
- [Spring Security — OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Keycloak — Getting Started with Docker](https://www.keycloak.org/getting-started/getting-started-docker)
