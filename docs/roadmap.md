# Roadmap após MVP

Tudo abaixo é hipótese/estudo posterior, não capacidade atual nem parte do aceite local. Cada extensão começa por problema mensurável, spec própria, threat model e aceite.

## RAG — só se baseline justificar

Pergunta de produto: informação sintética autorizada melhora a triagem e permite mostrar evidência rastreável? Proposta de estudo:

- Corpus Markdown sintético por workspace; fontes, checksums, versão do parser/embedding, chunks e status de ingestão.
- Pipeline idempotente, limites MIME/tamanho, sanitização, status explícito e remoção/reindexação demonstrável.
- Busca híbrida/vectorial avaliada com conjunto de perguntas rotulado; filtro de tenant aplicado antes da busca/ranking e checagens após recuperação.
- Mostrar fonte/excerpt ao reviewer. Modelo não inventa citação. Retrieved text é dado não confiável e não concede acesso.
- Comparar baseline sem RAG a retrieval: recall@k/MRR ou métrica apropriada, groundedness por rubric, custo/latência e vazamento cross-tenant.
- Só então escolher pgvector e estabelecer meta; RAG não é automaticamente melhor que baseline.

Nova spec precisa de abuso de prompt injection, exclusão de chunks/index/cache/backups, isolamento, retenção, avaliação e falhas de embedding/store.

## MCP — opcional e read-only inicial

Uma spec futura pode avaliar exposição de fila ou detalhe de solicitação via tools. Exige identidade OIDC propagada, membership checada em cada chamada, escopo por workspace, allowlist, paginação/limites, redaction, auditoria e resposta mínima. Sem ferramentas de escrita, SQL ou shell no primeiro estudo.

## AWS sandbox — após aceite local

Objetivo didático: deploy, rede, secrets, logs/métricas, custo e teardown. Terraform, somente dados sintéticos, acesso restrito. No dia do provisionamento verificar região, preço/quota, recursos elegíveis, cap/alertas, owner, ingress, backup/restore, secret flow e sequência de destroy/verificação. Sem piloto ou tráfego real.

## Possíveis tópicos formativos adicionais

- OAuth2/OIDC e autorização por tenant.
- Transações, locks/versionamento e idempotência.
- Contratos REST/OpenAPI, paginação e evolução compatível.
- Testes unitários, component/integration, Testcontainers e contract tests.
- Containers, CI/CD, migrations e rollback.
- Observabilidade: logs estruturados, métricas, traces e correlação.
- Resiliência: timeout, bulkhead/limites concorrentes, circuit breaker somente onde medição justificar, DLQ e runbooks.
- Fundamentos de arquitetura: coesão/acoplamento, trade-offs, fitness functions e decisões registradas.
