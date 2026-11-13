# Plano técnico — Spec 003

> **Para estudar:** este plano propõe uma abordagem; nada foi executado. Para cada decisão, pergunte: que teste prova isso, o que acontece se o provedor cair no meio e onde o texto da conversa poderia escapar sem que ninguém perceba.

- **Estado:** proposta para revisão dos participantes; nada implementado.
- **Spec:** [Spec 003](spec.md) · contrato em [`api-contract.md`](api-contract.md) · execução em [`tasks.md`](tasks.md)

## 1. Visão de arquitetura e limites

Um módulo novo, `conversations`, dentro do mesmo monólito modular. Ele é dono da conversa, dos turnos, dos vereditos e do contexto do problema. Ele **não** é dono do chamado: para escalar, chama a API pública de `requests`, que aplica as invariantes da Spec 001 e grava o chamado em `NEEDS_REVIEW`.

Duas portas de IA distintas, com prompt, schema, telemetria e configuração separados: `ChatProvider` (atendente) e `ConversationJudge` (JEV). Ambas têm fake determinístico como padrão, obrigatório em teste. A chamada ao `ChatProvider` é síncrona dentro da requisição HTTP, com timeout curto; a chamada ao `ConversationJudge` é assíncrona, via outbox e RabbitMQ, como a triagem da Spec 001.

A escolha é deliberada: o usuário precisa da resposta agora, mas a decisão de escalar pode esperar segundos e merece retry controlado, sem acoplar custo e latência de duas chamadas de modelo a um clique.

## 2. Módulos, ownership e dependências permitidas

| Módulo | É dono de | Expõe | Pode depender de |
| --- | --- | --- | --- |
| `conversations` | `conversation`, `conversation_turn`, `judge_verdict`, `problem_context`, custo por conversa | Comandos de conversa e leitura autorizada da transcrição por chamado | `identity`, API pública de `requests`, `messaging` |
| `requests` | Chamado, estado, histórico (Spec 001) | Comando "criar chamado a partir de conversa" com contexto opcional | `identity` |
| `identity` | Workspace, membership, papel | Consulta de autorização | — |
| `messaging` | Outbox, inbox, publicação e consumo | Publicação e consumo idempotente | — |

`conversations` nunca escreve em tabela de `requests`, e `requests` nunca lê turno de conversa: a leitura do técnico passa por uma consulta autorizada exposta por `conversations`, acionada pela rota do chamado. O teste de arquitetura do Spring Modulith verifica essas dependências (`NFR3-007`).

## 3. Contratos e modelo de dados

- `conversation`: `id`, `workspace_id`, `author_subject`, `status`, `version`, `assistant_turn_count`, `last_author_message_at`, `escalated_request_id`, `cost_cents_consumed`, timestamps.
- `conversation_turn`: `conversation_id`, `sequence` (único por conversa), `author` (`REQUESTER`/`ASSISTANT`), `text`, `state` (`GENERATED`/`FAILED`/`OUTCOME_UNKNOWN`), telemetria do provedor em colunas próprias.
- `judge_verdict`: `conversation_id`, `turn_sequence`, `verdict`, `reason_code`, `confidence`, `state`, telemetria. Unicidade por `(conversation_id, turn_sequence)`.
- `problem_context`: `conversation_id`, `request_id`, `summary`, `symptoms`, `attempted_steps`, `suggested_category`, `suggested_urgency`, `escalation_reason`, `generated` (booleano).
- Índice parcial único garantindo **um chamado não terminal por conversa** (`FR3-016`).

Todas as tabelas carregam `workspace_id` e participam dos índices. Migrações Flyway novas e aditivas. O campo `source` do chamado ganha o valor `CONVERSATION`, rejeitado quando enviado pelo cliente.

## 4. Consistência, concorrência e idempotência

- **Enviar mensagem:** uma transação grava o turno do autor; a chamada ao provedor acontece **fora** da transação; uma segunda transação grava o turno do assistente, incrementa a versão e insere `ConversationEvaluationRequested.v1` na outbox. Entre as duas, um crash deixa a mensagem do autor gravada sem resposta — estado visível e recuperável, melhor do que uma transação longa segurando conexão durante a chamada de rede.
- **Aplicar veredito:** o worker lê o evento, registra inbox por `(consumidor, eventId)`, chama o JEV, valida a saída e aplica a transição **somente** se `turn_sequence` ainda for o último turno de assistente e a conversa não for terminal. Caso contrário, grava o veredito como `OBSOLETE`.
- **Escalar:** uma única transação grava o chamado (via API de `requests`), o contexto, o vínculo, a auditoria, o novo estado da conversa e `ConversationEscalated.v1`. O índice parcial único impede dois chamados; a segunda tentativa recebe conflito, ou o mesmo resultado quando repete a chave idempotente.
- **Concorrência:** `expectedVersion` em toda escrita da conversa. Confirmação, pedido de humano, nova mensagem e aplicação de veredito disputam a mesma versão, então apenas uma vence — é isso que faz o pedido de humano prevalecer sobre um veredito tardio (`BR3-006`).
- **Idempotência:** chave escopada por subject, workspace e operação, com hash da chave e do corpo canonicalizado, como na Spec 001.

## 5. Falhas, timeout, retry, DLQ e recuperação

| Falha | Tratamento |
| --- | --- |
| `ChatProvider` indisponível ou `5xx` | Turno do assistente `FAILED`; resposta explica; autor pode reenviar ou pedir humano |
| Timeout após envio ao chat | Turno `OUTCOME_UNKNOWN`; sem retry automático (chamada possivelmente cobrada) |
| Saída de chat inválida | Descartada; nada do modelo é gravado como resposta; turno `FAILED` |
| JEV indisponível | Retry limitado e atrasado; conversa segue utilizável; limite de turnos escala |
| Veredito inválido | `REJECTED_INVALID` e escalonamento com `JUDGE_INVALID_OUTPUT` |
| Evento com schema desconhecido | DLQ, sem inferir payload |
| Orçamento esgotado | Nenhuma chamada real; escalonamento com `AI_BUDGET_BLOCKED` |
| Geração de contexto falha | Escalonamento acontece; chamado sem sugestão, com motivo seguro |
| Broker indisponível | Conversa continua; escalonamento manual pelo autor ou admin continua disponível |

O encerramento por inatividade (`FR3-006`) é derivado de `last_author_message_at` na leitura e consolidado por uma varredura idempotente; nenhuma decisão de produto depende de um job rodar na hora certa.

## 6. Segurança, privacidade, observabilidade e custo

A transcrição é o dado mais sensível do sistema. Controles propostos: acesso restrito ao autor até o escalonamento; leitura do técnico apenas pela rota do chamado, com auditoria; nenhum texto de turno em log, métrica, evento ou mensagem de erro; limites de tamanho, de turnos, de conversas ativas e de taxa por subject.

Prompt injection é tratada como entrada hostil: o prompt do JEV recebe a conversa como dado, não como instrução; a saída é validada contra enums e limites; e as razões determinísticas de escalonamento (pedido do autor, limite de turnos, orçamento, veredito inválido) não passam pelo modelo. Pedido de credencial na saída invalida a resposta.

Custo: chat e JEV reservam do mesmo teto mensal de US$5 da aplicação, antes da chamada, com reconciliação depois. Cada conversa tem custo máximo próprio; ao atingi-lo, escala. O fake é padrão e não gera custo. Métricas: turnos por conversa, taxa de escalonamento, motivos de escalonamento, vereditos por rota e confiança, falsos resolvidos, latência por provedor, custo por conversa, DLQ e idade da outbox.

## 7. Testes e evidências por requisito

| Requisito | Evidência pretendida | Executado? |
| --- | --- | --- |
| `FR3-001`–`FR3-003` | Testes de criação idempotente, ordem de turnos e limites de turno, conversa ativa e taxa | Não |
| `FR3-004`–`FR3-007` | Testes de pedido de humano, confirmação e negação, inatividade e encerramento por admin | Não |
| `FR3-008`–`FR3-011` | Testes do worker: veredito aplicado, inválido, confiança baixa e obsoleto por turno novo | Não |
| `FR3-012`–`FR3-013` | Teste de arquitetura das portas e teste de que nenhum veredito encerra conversa sozinho | Não |
| `FR3-014`–`FR3-018` | Teste transacional do escalonamento, índice parcial único, chamado em `NEEDS_REVIEW` e auditoria de leitura | Não |
| `FR3-019`–`FR3-021` | Testes de provedor fora do ar, timeout ambíguo, orçamento bloqueado e custo por conversa | Não |
| `FR3-022`, `NFR3-005` | Inspeção de logs, métricas e payloads de evento sem texto de turno | Não |
| `FR3-023`, `NFR3-006` | Execução do conjunto sintético de conversas, com relatório de falsos resolvidos e limitações | Não |
| `NFR3-001`–`NFR3-004`, `NFR3-007` | Fakes determinísticos, testes negativos de autorização, testes de concorrência e teste de modularidade | Não |

A coluna só muda com comando executado e saída anexada ao PR.

## 8. Entrega em fatias, rollback e operação

1. **Conversa sem IA:** abrir conversa, enviar mensagem, turnos imutáveis, limites, autorização e isolamento, com `ChatProvider` fake de eco.
2. **Escalonamento manual:** pedido de humano cria chamado em `NEEDS_REVIEW` com contexto montado a partir dos turnos, sem IA de contexto. Já é um fluxo ponta a ponta demonstrável.
3. **JEV fake assíncrono:** outbox, evento, worker, validação de veredito, `AWAITING_CONFIRMATION`, confirmação e negação, veredito obsoleto.
4. **Contexto estruturado:** geração validada de resumo, sintomas, passos tentados e sugestões, com caminho sem sugestão.
5. **Provedores reais opt-in:** adapters de chat e JEV, reserva de orçamento, timeout, `OUTCOME_UNKNOWN`, custo por conversa.
6. **Avaliação:** conjunto sintético de conversas, métricas e baseline reportado.
7. **Interface:** tela de chat do solicitante e visualização da transcrição pelo técnico dentro do chamado.

Cada fatia fica atrás de configuração por workspace: o chat pode ser desligado sem afetar a Spec 001, e a conversa continua escalável manualmente se o JEV for desativado. Rollback é desligar a rota, nunca apagar conversa ou chamado já gravados.

## 9. Alternativas e trade-offs

- **Um único provedor decidindo tudo (atendente que também julga):** rejeitado nesta spec. O avaliador separado é o exercício pedido, permite prompt e telemetria próprios e evita que o mesmo modelo que tentou resolver declare sucesso. Custa mais chamadas e mais latência de decisão.
- **Avaliação síncrona junto da resposta:** rejeitada; dobraria a latência percebida e o custo por turno, e misturaria duas falhas diferentes numa só requisição.
- **Resposta do chat também assíncrona (polling ou websocket):** adiada; aumentaria o aparato de entrega antes de haver domínio de conversa estável. Fica como extensão com spec própria.
- **Conversa ligada ao chamado desde o início (chamado criado ao abrir o chat):** rejeitado; encheria a fila humana de problemas que a conversa resolve e tornaria "não abrir chamado" impossível.
- **Técnico respondendo dentro do chat:** fora de escopo; exigiria presença, atribuição e tempo de resposta no canal, o que a Spec 002 já modela no chamado.
- **Fechamento automático por inatividade como "resolvido":** proibido pela constituição e pela [ADR-0003](../../docs/architecture/decisions/ADR-0003-human-reviewed-ai.md); inatividade gera estado reversível, nunca resolução.

## 10. Referências técnicas verificadas

Nenhum modelo, versão de SDK ou preço foi fixado. O modelo concreto, o custo por chamada e o limite por conversa precisam ser consultados na documentação do provedor no momento da implementação e registrados no PR que ativar o provedor real. Até lá, só o fake é usado e nenhuma afirmação de custo, latência ou qualidade é feita.
