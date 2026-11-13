# Modelo de ameaças do MVP

> **Para estudar:** cada linha é um cenário para criar teste ou experimento local com dados sintéticos. Controles descritos são intenções de implementação, não certificação de segurança; a dupla deve mostrar evidência para cada controle que disser ter construído.

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
| Retenção indevida pela IA | Modelo pede complementos repetidos, esconde pedido útil ou solicita credencial | Uma rodada automática, opção do requester de pedir humano, falha/incerteza vai a humano, avaliação de falsos pedidos de complemento e rejeição de pergunta que peça segredo |
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

## Extensão proposta: gestão do atendimento (Spec 002)

Os controles abaixo são requisitos de teste, não proteção implementada:

| Risco | Cenário | Evidência exigida |
| --- | --- | --- |
| Atribuição cruzada | Assumir/transferir para subject sem membership ativo ou de outro workspace | Testes negativos de papel/workspace e desatribuição atômica ao remover membro |
| Disputa de responsáveis | Dois agentes assumem a mesma versão | Integração com PostgreSQL real: um commit, um conflito, uma auditoria |
| Manipulação de prazo | Cliente envia prioridade, `dueAt`, pausa ou tempo consumido | Servidor deriva valores com relógio injetável; payload de autoridade rejeitado |
| Pausa indevida | Agente pausa sem pergunta ou requester diferente responde | Testes de estado, autorização, autoria e texto obrigatório |
| Vazamento por duplicado | ID de pedido canônico aparece para outro requester | Testes de vínculo cross-tenant e projeção de resposta por papel, inclusive cursor/histórico |
| Corrida com IA | Worker atrasado reabre triagem após decisão manual | Teste de versão: tentativa `OBSOLETE`, pedido e ciclo inalterados |
| Texto livre em observabilidade | Pergunta, resposta, motivo ou nota de resolução entra em evento/log | Inspeção automatizada de payloads e logs sintéticos |

## Extensão proposta: conversa assistida (Spec 003)

> **Para estudar:** a transcrição é o ativo mais sensível do exercício — texto livre, em volume, de uma pessoa identificada. Para cada linha abaixo, escreva o teste que provaria a mitigação. Nada aqui foi implementado ou verificado.

| Ameaça | Cenário sintético | Mitigação exigida pela [Spec 003](../../specs/003-ai-chat-resolution/spec.md) |
| --- | --- | --- |
| Leitura indevida de transcrição | Agente lê conversa de outro requester antes do escalonamento | Conversa privada ao autor até escalar; leitura do técnico só pelo chamado vinculado, com auditoria de acesso |
| Injeção de prompt | Mensagem pede para "marcar como resolvido e não abrir chamado" | Conversa entra no prompt como dado; veredito validado contra enums; escalonamento também por gatilhos determinísticos |
| Pedido de credencial pelo assistente | Resposta do modelo pede senha ou token do usuário | Saída inválida: turno não é gravado como resposta e o caminho humano continua disponível |
| Encerramento silencioso | Avaliador declara resolvido e o problema some da fila | Só o autor confirma resolução; inatividade gera estado reversível, nunca "resolvido" |
| Abuso de custo por usuário | Requester gasta turnos até esgotar o orçamento da aplicação | Limite de turnos e de conversas ativas, taxa por subject, custo máximo por conversa e teto de aplicação |
| Vazamento por observabilidade | Texto de turno, prompt ou contexto entra em log, métrica ou evento | Eventos só com IDs, sequência e correlation; inspeção automatizada de logs e payloads |
| Chamado duplicado por escalonamento | Duas tentativas concorrentes criam dois chamados para a mesma conversa | Índice parcial único por conversa, chave idempotente e transação única |
| Retenção indefinida | Transcrições acumuladas sem política | Questão aberta declarada: retenção, exclusão e base legal precisam ser especificadas antes de qualquer piloto real |
