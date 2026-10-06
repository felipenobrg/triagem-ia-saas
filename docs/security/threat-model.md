# Modelo de ameaças inicial

- **Estado:** baseline documental; revisar junto da especificação e antes de publicar um sandbox.
- **Ativos:** isolamento por workspace, solicitações, credenciais de intake, identidade/membership, knowledge chunks, prompts/respostas, secrets de provider, mensagens, logs e custo de IA/cloud.
- **Fronteiras:** navegador/requester → API pública; identidade → aplicação; aplicação → PostgreSQL/RabbitMQ; worker → provedor de IA; aplicação → armazenamento vetorial; operador → console/cloud; futuramente, cliente MCP → ferramentas.

## Ameaças e controles

| Ameaça | Exemplo | Controles obrigatórios / evidência |
| --- | --- | --- |
| Acesso cross-tenant (IDOR) | Alterar request/workspace ID e obter dados de outra equipe | Workspace resolvido da identidade; autorização na aplicação e filtros do repositório; FKs compostas quando cabível; testes negativos em todas as rotas e RAG |
| Abuso do intake público | Spam, scraping, payload enorme, token vazado | Token revogável e escopado; hash em repouso; rate limits/quotas; limites de bytes/campos; resposta não enumerável; rotação e telemetria sem credencial |
| Prompt injection via pedido/documento | Texto tenta instruir modelo a revelar conteúdo ou acionar operação | IA sem autorização/execução; delimitar conteúdo como dado; schema estrito; human review; filtros tenant antes da inferência; testes com payloads maliciosos sintéticos |
| Vazamento via RAG | Vector search devolve chunk de outro workspace | Predicado de workspace aplicado antes do ranking/geração; testes de vizinho semântico cross-tenant; citations originadas somente dos chunks autorizados; remoção/reindex comprováveis |
| Resposta de IA malformada ou manipulada | Valores fora de enumeração, conteúdo excessivo, campo que tenta aprovar | Schema allowlist e limites; descartar resposta inválida; nenhum campo de workflow/tenant é controlado pelo modelo; estado manual recuperável |
| Replay/duplicação de eventos | RabbitMQ redelivery duplica sugestão ou status | Event ID estável, inbox/unique constraint, operação idempotente; confirmação após commit; teste de entrega duplicada e crash/recovery |
| Perda entre banco e broker | Commit no banco sem publish ou publish duplicado | Transactional outbox, publisher confirms, dispatcher recuperável, métrica de idade, runbook de replay; não alegar exactly-once |
| Secret exposure | Token ou API key em Git, imagem, log ou erro | Secret manager/env fora do repo, secret scanning, rotação, redaction, menor privilégio, exceções nunca incluem valor secreto |
| Custo/denial-of-wallet | Intake automatizado dispara inferências caras ou retries | Cota por workspace, limites por chamada, rate limit, bounded retries/DLQ, monitoramento de uso e alertas de sandbox |
| Retenção ou exclusão incompleta | Request apagado, mas chunks/cache/logs permanecem | Política documentada, deletion workflow com tombstone e prova de remoção em índice; retenção/backup/logs definidos antes de uso real |
| Ferramenta MCP indevida (futuro) | Tool executa SQL ou aceita workspace arbitrário | Fora do MVP; read-only por padrão, identidade propagada, auth em toda chamada, resultados limitados, allowlist e audit; sem SQL/shell |
| Supply chain | Dependência vulnerável ou imagem alterada | Versões fixas, dependabot/scan, imagem reproduzível, SBOM/assinatura quando viável, atualização e revisão de CVEs antes do sandbox |

## Abuse cases a testar

1. Usuário envia workspace de terceiro em body, path, filtro, evento e argumento MCP.
2. Agente tenta revisar uma sugestão de workspace alheio ou de outra versão.
3. Request contém HTML, script, prompt injection, caracteres de controle e conteúdo acima do limite.
4. Documento recuperado solicita revelar prompts, secrets ou dados de outro tenant.
5. Provedor retorna JSON incompleto, texto livre, valores extras, resumo enorme, referência inventada ou erro/timeout.
6. Evento chega duas vezes, fora de ordem, com schema futuro, ou é redeliverado após worker persistir e cair antes do ack.
7. Credencial pública é revogada mas ainda usada; medir cache/propagação de revogação.
8. Busca e logs tentam revelar se um request de terceiro existe.

## Dados e logs

Até a política de retenção ser implementada, usar apenas fixtures sintéticas. Por padrão, registrar identificadores técnicos aleatórios, código de falha, status, latência, contagem de caracteres/tokens, versão de prompt/schema e correlation ID. Não registrar corpo da solicitação, token, segredo, prompt completo, documento ou resposta integral do modelo.

## Critério para abrir sandbox

Antes de provisionar ou publicar acesso: definir região e custo máximo, autenticação e usuários autorizados, rede e ingress, política de secrets, logging, backup/restore, dados sintéticos, contato/owner, alertas e sequência comprovada de teardown. Validar serviço, preço, quota e extensão `pgvector` disponíveis no momento da implementação; a documentação inicial não prova essas condições.
