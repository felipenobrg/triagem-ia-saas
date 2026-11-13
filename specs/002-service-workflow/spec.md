# Spec 002: Gestão do atendimento

> **Para estudar:** esta evolução fictícia concentra regras de atribuição, prioridade, tempo e histórico. Antes de codificar, simule com a dupla dois agentes concorrendo pelo mesmo pedido e um prazo pausado na sexta-feira. Os números de prioridade e prazo não vêm de operação real.

- **Estado:** proposta pronta para revisão dos participantes; nada implementado.
- **Data:** 2026-11-09
- **Depende de:** identidade, isolamento, criação, classificação manual, auditoria e concorrência da [Spec 001](../001-ai-assisted-request-triage/spec.md). A rota IA → humano/complemento não bloqueia o primeiro incremento desta spec: durante a implementação, a classificação manual com bypass explícito permite exercitar o atendimento. Solicitações criadas pela conversa da [Spec 003](../003-ai-chat-resolution/spec.md) entram neste fluxo pela mesma fila humana.
- **Fonte de verdade:** este documento define as regras; [`api-contract.md`](api-contract.md), [`plan.md`](plan.md) e [`tasks.md`](tasks.md) detalham a entrega.

## 1. Problema e resultado esperado

Classificar um pedido não resolve o atendimento. A equipe precisa saber quem assumiu o trabalho, qual prioridade foi decidida, quando o prazo vence, por que houve espera, como uma resolução pode ser contestada e se dois pedidos relatam o mesmo problema. O objetivo didático é implementar regras de domínio, autorização, tempo, concorrência e histórico verificáveis, com dados exclusivamente sintéticos.

Exemplo: Ana abre um pedido de acesso no workspace A. Um agente confirma impacto e urgência, gerando prioridade P2; Bruno assume o pedido. Ele faz uma pergunta a Ana e o prazo pausa. A resposta retoma a contagem. Bruno resolve, Ana contesta dentro do prazo permitido e o pedido volta à fila com um novo ciclo de atendimento. Outro agente pode vincular um relato duplicado sem expor o pedido de Ana a outros solicitantes.

## 2. Escopo e dependências

Incluído: responsável, transferência, impacto confirmado, prioridade derivada com override humano, prazo de resolução em horário útil, espera por resposta, reabertura e vínculo de duplicados. Tudo no mesmo monólito e PostgreSQL da Spec 001, sem novo serviço externo. Uma interface mínima mostra responsável, prioridade, prazo, motivo de espera e histórico.

A IA da Spec 001 faz primeiro o roteamento humano/complemento e, quando encaminha ao humano, sugere categoria, urgência e resumo. Ampliar a saída da IA para prioridade ou responsável exige avaliação e mudança contratual posterior.

Fora de escopo: notificações externas, plantão 24/7, feriados, múltiplos calendários, SLA contratual, cobrança, automação de atribuição, fechamento automático, mesclagem ou exclusão de pedidos, RAG e qualquer uso real. O prazo aqui é meta didática de resolução, não promessa comercial.

## 3. Atores e vocabulário

Mantêm-se os papéis `REQUESTER`, `AGENT` e `ADMIN` da Spec 001. `REQUESTER` lê e responde apenas aos próprios pedidos e pode contestar a própria resolução. `AGENT` assume, transfere quando é o responsável atual, decide impacto e prioridade, registra espera e resolução e vincula duplicados no workspace. `ADMIN` executa essas ações e pode transferir qualquer pedido do workspace. Toda operação revalida membership ativo no momento do comando.

- **Responsável:** membro ativo `AGENT`/`ADMIN` encarregado do pedido. Pode ser vazio; o pedido continua visível na fila autorizada.
- **Impacto informado:** percepção opcional do solicitante na criação; não é decisão de prioridade.
- **Impacto confirmado:** `SINGLE_USER`, `TEAM` ou `WORKSPACE`, decidido por agent/admin antes de abrir o atendimento.
- **Prioridade:** `P1` (mais urgente) a `P4`, calculada a partir de impacto confirmado e urgência aceita. Override humano exige motivo.
- **Ciclo de atendimento:** começa ao entrar em `OPEN`; uma reabertura inicia outro ciclo e preserva os anteriores.
- **Tempo útil:** segunda a sexta, 09:00–18:00 em `America/Fortaleza`; sem feriados no MVP.
- **Duplicado:** vínculo direcional de um pedido secundário a um pedido canônico do mesmo workspace. Os dois continuam pedidos independentes.

## 4. Requisitos funcionais

- **FR2-001 — Assumir:** agent/admin ativo assume pedido `OPEN` sem responsável usando `expectedVersion`. Duas tentativas concorrentes não podem vencer; a perdedora recebe conflito.
- **FR2-002 — Transferir e desatribuir:** o responsável atual ou um admin transfere para agent/admin ativo do mesmo workspace, com motivo. Admin pode desatribuir. Quando o membership do responsável é desativado ou rebaixado, os pedidos ativos atribuídos a ele ficam sem responsável na mesma operação de negócio, com auditoria; remover o último admin continua proibido.
- **FR2-003 — Registrar impacto e prioridade:** o solicitante pode informar impacto inicial; agent/admin confirma o impacto ao classificar. O sistema deriva a prioridade pela matriz abaixo, a partir de impacto confirmado e urgência humana. Override é permitido só a agent/admin, com motivo não vazio, e registra valor calculado e valor escolhido.
- **FR2-004 — Manter prioridade:** agent/admin pode atualizar impacto, urgência ou override em pedido `OPEN`, `IN_PROGRESS` ou `WAITING_REQUESTER` usando `expectedVersion`. A mudança recalcula o prazo do ciclo sem apagar tempo decorrido nem violação passada. O histórico registra valores antes e depois e o motivo.
- **FR2-005 — Medir prazo:** ao entrar em `OPEN`, criar ciclo com início, prioridade e prazo de resolução. O relógio útil é determinístico, calculado no servidor e exibido na API. `P1=4h`, `P2=8h`, `P3=24h`, `P4=40h` úteis. Fora do horário útil não consome prazo.
- **FR2-006 — Esperar resposta:** responsável ou admin move `IN_PROGRESS` para `WAITING_REQUESTER` com pergunta não vazia, e o prazo pausa nesse instante. Somente o solicitante do pedido pode responder; a resposta retoma o prazo e move para `IN_PROGRESS`. Admin pode cancelar a espera com motivo não vazio quando o solicitante não puder responder. Pergunta e resposta são registros separados do texto original e passam por limites de tamanho.
- **FR2-007 — Resolver e contestar:** responsável ou admin resolve `IN_PROGRESS` com nota de resolução não vazia. O solicitante ou agent/admin pode reabrir de `RESOLVED` até 7 dias corridos após a resolução, com motivo não vazio; depois disso, abre-se novo pedido. A reabertura leva a `OPEN` e cria novo ciclo, mantendo o responsável apenas se seu membership e papel ainda forem válidos.
- **FR2-008 — Vincular duplicados:** agent/admin pode vincular pedido secundário não resolvido a um canônico do mesmo workspace, sem auto-resolver, mesclar texto, copiar decisão ou alterar prazo. Um secundário tem no máximo um canônico; canônico não pode ser secundário; não há auto-vínculo nem cadeia. Desvincular exige motivo. A operação usa as versões esperadas dos dois pedidos e gera auditoria.
- **FR2-009 — Consultar e auditar:** a fila de agentes inclui filtros por responsável, prioridade, prazo vencido e estado. O histórico paginado mostra atribuições, mudanças de prioridade, pausas, respostas, ciclos, reabertura e vínculos. O requester não recebe identificador nem conteúdo de outro pedido ligado como duplicado, mesmo no mesmo workspace.

### Matriz de prioridade

| Impacto confirmado \ Urgência aceita | LOW | NORMAL | HIGH | CRITICAL |
| --- | --- | --- | --- | --- |
| `SINGLE_USER` | P4 | P4 | P3 | P2 |
| `TEAM` | P4 | P3 | P2 | P1 |
| `WORKSPACE` | P3 | P2 | P1 | P1 |

A urgência aceita vem da classificação manual ou da aprovação humana da sugestão. A IA não define prioridade. Se o impacto ainda não foi confirmado, o pedido não pode entrar em `OPEN` sob a Spec 002. O override não muda a matriz: guarda prioridade calculada, prioridade efetiva, motivo e autor.

## 5. Regras e transições

- **BR2-001:** workspace, autoria, membership e papel são verificados na operação; ID em path, body, evento ou cursor não concede acesso.
- **BR2-002:** o responsável deve ter membership ativo e papel `AGENT`/`ADMIN` no mesmo workspace. Desativação ou rebaixamento não pode deixar atribuição inválida.
- **BR2-003:** decisão manual prevalece sobre resultado de IA tardio; processamento assíncrono não muda responsável, prioridade, estado ou prazo após decisão humana incompatível.
- **BR2-004:** histórico, ciclos e texto original são preservados. Cada mudança de prioridade fecha o trecho anterior com seu prazo; nenhuma alteração ou reabertura apaga violação ocorrida nesse trecho.
- **BR2-005:** a pausa só existe em `WAITING_REQUESTER` após pergunta registrada; resposta do solicitante ou cancelamento pelo admin encerra a pausa exatamente uma vez. Falha de entrega externa não existe neste MVP.
- **BR2-006:** prazo vencido é derivado do tempo útil consumido, não de um job. Um job de alerta futuro deve ser idempotente e não altera o cálculo.
- **BR2-007:** vínculo de duplicado não autoriza leitura cruzada; secundário e canônico preservam estado, prazo e resolução próprios.

Transições adicionais à Spec 001:

| De | Para | Quem e condição |
| --- | --- | --- |
| `IN_PROGRESS` | `WAITING_REQUESTER` | Responsável ou admin, com pergunta registrada e versão atual |
| `WAITING_REQUESTER` | `IN_PROGRESS` | Solicitante responde; fechar a pausa atomicamente |
| `WAITING_REQUESTER` | `IN_PROGRESS` | Admin cancela a espera com motivo; fechar a pausa atomicamente |
| `RESOLVED` | `OPEN` | Solicitante do pedido ou agent/admin, até 7 dias corridos, com motivo; novo ciclo |

`OPEN → IN_PROGRESS` e `IN_PROGRESS → RESOLVED` continuam da Spec 001; aqui a resolução exige responsável ou admin e nota. `IN_PROGRESS → OPEN` exige motivo e mantém o mesmo ciclo. `RESOLVED` fora da janela é terminal. A espera não pode ser iniciada por agente que não seja o responsável, exceto admin.

### Cálculo de tempo

Cada ciclo guarda `openedAt`, prioridade efetiva, minutos úteis consumidos, pausas fechadas e a pausa aberta, e `resolvedAt` opcional. O prazo corresponde aos minutos úteis restantes no calendário fixo. Enquanto uma pausa está aberta, `dueAt` é `null` e a API mostra os minutos úteis restantes; ao responder, calcula-se novo `dueAt`.

Ao mudar a prioridade: fechar o trecho anterior, preservar eventual violação e recalcular os minutos restantes como `max(0, orçamento da nova prioridade − minutos já consumidos)`, a partir do instante da mudança. O relógio não reinicia. `breachedAt` é o primeiro vencimento observado nos trechos do ciclo e é derivável do histórico mesmo sem job. Ao resolver, fechar o consumo e marcar violação se aplicável. Reabrir cria ciclo novo, mantendo todos os dados e violações do anterior. Os testes usam relógio injetável e instantes UTC; horário de verão não é premissa do calendário fixo. A janela de reabertura inclui o instante exato `resolvedAt + 7 dias corridos`.

## 6. Concorrência, falhas e recuperação

Todos os comandos de escrita recebem `expectedVersion` e atualizam o pedido condicionalmente. Assumir, transferir, classificar, mudar prioridade, pausar, responder, resolver e reabrir registram auditoria na mesma transação. Operações de duplicado bloqueiam e atualizam os dois pedidos em ordem estável de ID e validam as duas versões; conflito não deixa vínculo parcial. Desativar membership e desatribuir os pedidos afetados é uma única operação transacional da aplicação, com locks e ordenação definidos na implementação.

O cálculo de prazo é puro e reexecutável. Indisponibilidade de worker, Rabbit ou IA não impede a operação manual. Retry de comando HTTP após timeout ambíguo deve reler o estado antes de nova escrita; `expectedVersion` evita sobrescrever decisão, mas não substitui idempotência de resposta nem auditoria. Eventos futuros de prazo ou atribuição não carregam texto de pergunta, resposta ou resolução.

## 7. Requisitos de qualidade

- **NFR2-001:** testes de concorrência com PostgreSQL real cobrem dupla atribuição, classificação versus IA tardia, remoção de responsável e vínculo simultâneo.
- **NFR2-002:** tabela de testes de relógio cobre abertura antes e depois do horário útil, sexta para segunda, pausa e resposta, troca de prioridade, resolução após vencimento e reabertura.
- **NFR2-003:** testes negativos cobrem papéis, autoria, workspace, cursor, filtros e IDs de duplicados; nenhuma resposta revela pedido alheio.
- **NFR2-004:** cada transição produz trilha auditável sem texto livre em log ou evento; teste de integração verifica atomicidade e histórico.

## 8. Cenários de aceite

1. **Dupla atribuição:** dois agentes usam a mesma versão; um assume e o outro recebe `409 VERSION_CONFLICT`.
2. **Membro removido:** admin desativa o responsável; o pedido ativo fica sem responsável, o histórico registra a mudança e o último admin não pode ser removido.
3. **Prioridade:** impacto `TEAM` com urgência `HIGH` gera P2; override para P1 sem motivo é rejeitado; com motivo, registra ambos os valores.
4. **Prazo:** abrir sexta às 17:00 com P1 vence segunda às 12:00 — uma hora útil antes e três depois do fim de semana.
5. **Pausa:** pergunta em `WAITING_REQUESTER` congela o consumo; a resposta do solicitante o retoma; resposta de outro requester retorna `404`.
6. **Espera sem resposta:** admin cancela a espera com motivo; o prazo retoma uma vez e a resposta tardia do requester recebe conflito.
7. **Reabertura:** contestação dentro de 7 dias abre ciclo novo; depois retorna `409 REOPEN_WINDOW_EXPIRED` e o ciclo anterior continua consultável.
8. **Duplicado:** vínculo entre workspaces, em cadeia ou auto-vínculo falha sem alterar nada; o requester não vê o vínculo nem o ID do pedido alheio.
9. **IA tardia:** a classificação humana vence e a sugestão tardia não muda estado, prioridade ou prazo.
10. **Origem conversa:** pedido criado pela Spec 003 entra na fila humana com contexto; prioridade e prazo só existem após o impacto confirmado por uma pessoa.

## 9. Questões para revisão dos participantes

Validar se o calendário local de 09:00–18:00, os quatro prazos, a janela de 7 dias e a matriz representam um exercício útil. São decisões didáticas propostas, sem evidência de operação real; os participantes podem alterá-las na spec antes de implementar o comportamento afetado. Nenhuma métrica de SLA real é alegada.

## 10. Referências

[ADR-0006](../../docs/architecture/decisions/ADR-0006-service-workflow.md) · [Spec 001](../001-ai-assisted-request-triage/spec.md) · [Spec 003](../003-ai-chat-resolution/spec.md) · [Guia da mentoria](../../docs/mentoring-guide.md)
