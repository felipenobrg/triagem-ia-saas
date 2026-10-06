# Modelo de ameaças do MVP

- **Estado:** hipóteses e controles planejados; nenhum controle foi validado em aplicação ainda.
- **Escopo:** local, dados sintéticos, Keycloak OIDC, API, PostgreSQL, RabbitMQ, worker e adapter IA opcional.
- **Fora do MVP:** intake público, RAG, MCP e cloud pública; cada um exige avaliação específica.

## Ativos e fronteiras

Ativos: identidade/refresh access local, memberships, solicitação, histórico, sugestão e revisão, mensagem/evento, chave OpenAI, cap/custos, logs e configuração. Fronteiras: navegador→Keycloak/API; API/worker→PostgreSQL; dispatcher/worker↔RabbitMQ; worker→OpenAI quando opt-in.

| Risco | Exemplo | Controles e evidência exigida |
| --- | --- | --- |
| Token inválido/forjado | Claim role inventada ou issuer diferente | Validar assinatura, issuer, audience e expiração; negativos de integração. Membership/papel consultado na app DB. |
| IDOR/cross-tenant | Trocar workspace/request ID ou cursor | Caso de uso verifica membership e proprietário; todas queries tenant scoped; FKs compostas; testes negativos por papel/rota/filtro. Resposta não enumera existência. |
| Escalada de papel | Requester altera role no body | Ignorar role do cliente; endpoint admin-only; audit de mudança; teste de autorização. |
| Replay/conflito de escrita | Duplo clique cria duas solicitações ou revisão sobrescreve outra | Idempotency-Key com escopo e body hash; versionamento otimista; unique constraints e testes de concorrência. |
| Prompt injection no texto | Solicitação instrui modelo a ignorar política | IA sem ferramentas/autoridade, texto tratado como dado, schema allowlist, limites e aprovação humana; fixtures adversariais sintéticas. |
| Saída malformada | JSON extra, enum inválido, resumo longo ou workflow injection | Schema estrito mais validação de domínio e tamanho no servidor; rejeitar saída como sugestão; caminho manual permanece. |
| Custo descontrolado | Loop de retry ou várias chamadas concorrentes ultrapassam cap | Fake default; live opt-in; reserva de custo concorrente antes da chamada, modelo/preço conhecidos, cap US$5/mês, bloqueio conservador, limites externos na conta; teste acima do limite. |
| Segredo exposto | API key em Git, frontend, erro ou log | Env ignorada/secret store; scans; key só no backend; redaction; rotação e teste de configuração. |
| Perda em banco/broker | Request com outbox que não publica; confirm mas mensagem sem binding | Mesma transação; confirm + `mandatory=true` + tratamento de `basic.return`; métricas de idade/retorno; teste sem binding e recuperação. |
| Redelivery/dados duplicados | Worker persiste mas cai antes de ACK | Inbox unique e efeito na mesma transação; ACK depois do commit; testes crash/redelivery. At-least-once declarado. |
| Duplicação de inferência paga | Worker cai após resposta OpenAI antes de persistir | Timeout/resultado ambíguo `OUTCOME_UNKNOWN`; sem retry automático; operador confirma possível duplicação, nova tentativa auditada. |
| Poison event | Versão inválida entra em loop infinito | Schema version allowlist; retry limitado; DLQ; replay explícito/auditado sem conteúdo sensível. |
| Vazamento por observabilidade | Texto livre em log/evento/trace | Payload de evento só IDs; logs sem request/prompt/output; teste por inspeção e redaction. |
| Supply chain/config | Dependência/imagem vulnerável ou porta exposta | Lock/wrapper versionado, análise CI, serviços locais bind em interface adequada, atualizar dependências antes de sandbox. |

## Casos de abuso a verificar

1. Requester tenta ler/revisar/classificar pedido de outro requester ou workspace.
2. Agent tenta administrar membership; usuário força role/workspace em JSON ou token.
3. Reutilizar idempotency key com payload divergente; executar duas revisões da mesma versão.
4. Enviar texto muito longo, HTML, Unicode de controle, prompt injection ou dados sintéticos semelhantes a segredo.
5. Provedor fake/real retorna campo extra, categoria inválida, conteúdo longo, timeout, 429, 5xx ou resultado ambíguo.
6. RabbitMQ retorna sem rota, confirma publicação, entrega duplicado, redeliver após commit, ou envia schema desconhecido.
7. Simular workers concorrentes próximos do cap e verificar que reserva impede exceder limite.
8. Buscar corpo original, token, prompt, resposta completa ou chave em eventos, logs, métricas e exceções: nenhum deve conter.

## Operação e aceitação

Demo não deve usar dado pessoal/corporativo. Para qualquer AWS sandbox futuro: rever threat model com região/preço atuais, ingress e identidades autorizadas, budget/alertas, secrets, backup e restore, retenção, contato responsável e teardown. Evidência local não prova segurança de piloto real.
