# Spec 002: Gestão do atendimento

- **Estado:** proposta pronta para revisão dos participantes; nada implementado.
- **Data:** 2026-11-09
- **Depende de:** identidade, isolamento, criação, classificação manual, auditoria e concorrência da [Spec 001](../001-ai-assisted-request-triage/spec.md). RabbitMQ e IA não bloqueiam o primeiro incremento desta spec.
- **Fonte de verdade:** este documento define regras; [contrato HTTP](api-contract.md), [plano](plan.md) e [tarefas](tasks.md) detalham entrega.

## 1. Problema e resultado esperado

Classificar um pedido não resolve o atendimento. A equipe precisa saber quem assumiu o trabalho, qual prioridade foi decidida, quando o prazo vence, por que houve espera, como uma resolução pode ser contestada e se dois pedidos relatam o mesmo problema. O objetivo didático é implementar regras de domínio, autorização, tempo, concorrência e histórico verificáveis, com dados exclusivamente sintéticos.

Exemplo: Ana abre um pedido de acesso no workspace A. Um agente confirma impacto e urgência, gerando prioridade P2; Bruno assume o pedido. Ele faz uma pergunta a Ana e o prazo pausa. A resposta retoma a contagem. Bruno resolve, Ana contesta dentro do prazo permitido e o pedido volta à fila com um novo ciclo de atendimento. Outro agente pode vincular um relato duplicado sem expor o pedido de Ana a outros solicitantes.

## 2. Escopo e dependências

Incluído: responsável, transferência, impacto confirmado, prioridade derivada com override humano, prazo de resolução em horário útil, espera por resposta, reabertura e vínculo de duplicados. Tudo no monólito e PostgreSQL da Spec 001, sem novo serviço externo. Uma interface mínima mostra responsável, prioridade, prazo, motivo de espera e histórico. A IA da Spec 001 continua sugerindo apenas categoria, urgência e resumo; ampliar a saída da IA exige avaliação e mudança contratual posterior.

Fora de escopo: notificações externas, plantão 24/7, feriados, múltiplos calendários, SLA contratual, cobrança, automação de atribuição, fechamento automático, mesclagem ou exclusão de pedidos, RAG e uso real. O prazo aqui é uma meta didática de resolução, não promessa comercial.

## 3. Atores e vocabulário

Mantêm-se os papéis `REQUESTER`, `AGENT` e `ADMIN` da Spec 001. `REQUESTER` só lê e responde aos próprios pedidos; pode contestar a própria resolução. `AGENT` assume, transfere quando é responsável atual, decide impacto/prioridade, registra espera/resolução e vincula duplicados no workspace. `ADMIN` pode executar essas ações e transferir qualquer pedido do workspace. Toda operação revalida membership ativo no momento do comando.

- **Responsável:** membro ativo `AGENT`/`ADMIN` encarregado do pedido. Pode ser vazio; o pedido continua visível na fila autorizada.
- **Impacto informado:** percepção opcional do solicitante no momento da criação; não é decisão de prioridade.
- **Impacto confirmado:** `SINGLE_USER`, `TEAM` ou `WORKSPACE`, decidido por agente/admin antes de abrir o atendimento.
- **Prioridade:** `P1` (mais urgente) a `P4`, calculada por impacto confirmado e urgência aceita. Override humano exige motivo.
- **Ciclo de atendimento:** começa ao entrar em `OPEN`; uma reabertura inicia outro ciclo, preservando os anteriores.
- **Tempo útil:** segunda a sexta, 09:00–18:00 em `America/Fortaleza`; sem feriados no MVP.
- **Duplicado:** vínculo direcional de um pedido secundário a um pedido canônico do mesmo workspace; os dois continuam pedidos independentes.

## 4. Requisitos funcionais

- **FR2-001 — Assumir:** agente/admin ativo assume pedido `OPEN` sem responsável usando `expectedVersion`. Duas tentativas concorrentes não podem vencer. Repetição com versão antiga retorna conflito.
- **FR2-002 — Transferir e desatribuir:** responsável atual ou admin transfere para agente/admin ativo do mesmo workspace, com motivo. Admin pode desatribuir. Quando membership do responsável é desativado/rebaixado, os pedidos ativos atribuídos a ele ficam sem responsável na mesma operação de negócio, com auditoria; remover o último admin continua proibido.
- **FR2-003 — Registrar impacto e prioridade:** solicitante pode informar impacto inicial; agente/admin confirma impacto ao classificar. Sistema deriva prioridade pela matriz abaixo a partir de impacto confirmado e urgência humana. Override é permitido apenas por agente/admin com motivo não vazio e registra valor calculado e escolhido.
- **FR2-004 — Manter prioridade:** agente/admin pode atualizar impacto, urgência ou override em pedido `OPEN`, `IN_PROGRESS` ou `WAITING_REQUESTER` usando `expectedVersion`. Mudança recalcula o prazo do ciclo sem apagar tempo já decorrido ou violação passada. Histórico registra valores antes/depois e motivo.
- **FR2-005 — Medir prazo:** ao entrar em `OPEN`, criar ciclo com início, prioridade e prazo de resolução. Relógio útil é determinístico, calculado no servidor e exibido na API. `P1=4h`, `P2=8h`, `P3=24h`, `P4=40h` úteis. Fora do horário útil não consome prazo.
- **FR2-006 — Esperar resposta:** responsável ou admin pode mover `IN_PROGRESS` para `WAITING_REQUESTER` com pergunta não vazia. O prazo pausa nesse instante. Somente o solicitante do pedido pode responder; a resposta retoma o prazo e move para `IN_PROGRESS`. Admin pode cancelar a espera com motivo não vazio, retomando prazo e atendimento quando o solicitante não puder responder. A pergunta e resposta são registros separados do texto original e passam por limites de tamanho.
- **FR2-007 — Resolver e contestar:** responsável ou admin resolve `IN_PROGRESS` com nota de resolução não vazia. O solicitante ou agente/admin pode reabrir de `RESOLVED` até 7 dias corridos após resolução, com motivo não vazio; depois disso, deve abrir novo pedido. Reabertura leva a `OPEN` e cria novo ciclo. Mantém o responsável apenas se seu membership e papel ainda forem válidos.
- **FR2-008 — Vincular duplicados:** agente/admin pode vincular pedido secundário não resolvido a canônico do mesmo workspace, sem auto-resolver, mesclar texto, copiar decisão ou alterar SLA. Um secundário tem no máximo um canônico; canônico não pode ser secundário; sem auto-vínculo ou cadeia. Desvincular exige motivo. A operação usa versões esperadas dos dois pedidos e auditoria.
- **FR2-009 — Consultar e auditar:** fila de agentes inclui filtros por responsável, prioridade, prazo vencido e estado. Histórico paginado mostra atribuições, mudanças de prioridade, pausas, respostas, ciclos, reabertura e vínculos. Requester não recebe identificador ou conteúdo de outro pedido ligado como duplicado, mesmo se for do mesmo workspace.

### Matriz de prioridade

| Impacto confirmado \ Urgência aceita | LOW | NORMAL | HIGH | CRITICAL |
| --- | --- | --- | --- | --- |
| `SINGLE_USER` | P4 | P4 | P3 | P2 |
| `TEAM` | P4 | P3 | P2 | P1 |
| `WORKSPACE` | P3 | P2 | P1 | P1 |

Urgência aceita vem da classificação manual ou da aprovação humana da sugestão. A IA não define prioridade. Se o impacto ainda não foi confirmado, o pedido não pode entrar em `OPEN` sob a Spec 002. O override não muda a matriz: guarda prioridade calculada, prioridade efetiva, motivo e autor.

## 5. Regras e transições

- **BR2-001:** workspace, autoria, membership e papel são verificados na operação; ID no path, body, evento ou cursor não concede acesso.
- **BR2-002:** responsável deve ter membership ativo e papel `AGENT`/`ADMIN` no mesmo workspace. Desativação/rebaixamento não pode deixar atribuição inválida.
- **BR2-003:** decisão manual prevalece sobre resultado de IA tardio; processamento assíncrono não muda responsável, prioridade, estado ou prazo após decisão humana incompatível.
- **BR2-004:** histórico, ciclos e texto original são preservados. Cada mudança de prioridade fecha o trecho anterior com seu prazo; nenhuma alteração ou reabertura apaga violação que ocorreu nesse trecho.
- **BR2-005:** pausa só existe em `WAITING_REQUESTER` após pergunta registrada; resposta do solicitante ou cancelamento pelo admin encerra a pausa exatamente uma vez. Falha de entrega externa não existe neste MVP.
- **BR2-006:** prazo vencido é derivado de tempo útil consumido, não de um job. Job de alerta futuro deve ser idempotente e não altera o cálculo.
- **BR2-007:** vínculo duplicado não autoriza leitura cruzada; secundário e canônico preservam estado, prazo e resolução próprios.

Transições adicionais à Spec 001:

| De | Para | Quem e condição |
| --- | --- | --- |
| `IN_PROGRESS` | `WAITING_REQUESTER` | Responsável/admin, pergunta registrada e versão atual |
| `WAITING_REQUESTER` | `IN_PROGRESS` | Solicitante responde; fechar pausa atomicamente |
| `WAITING_REQUESTER` | `IN_PROGRESS` | Admin cancela espera com motivo; fechar pausa atomicamente |
| `RESOLVED` | `OPEN` | Solicitante do pedido ou agente/admin, até 7 dias corridos, com motivo; novo ciclo |

`OPEN → IN_PROGRESS` e `IN_PROGRESS → RESOLVED` continuam da Spec 001; na Spec 002, resolução exige responsável/admin e nota. `IN_PROGRESS → OPEN` exige motivo e mantém o mesmo ciclo. `RESOLVED` fora da janela é terminal. Espera não pode ser iniciada por um agente que não seja responsável, exceto admin.

### Cálculo de tempo

Cada ciclo guarda `openedAt`, prioridade efetiva, minutos úteis consumidos, pausas fechadas/aberta e `resolvedAt` opcional. O prazo corresponde aos minutos úteis restantes no calendário fixo. Enquanto uma pausa está aberta, `dueAt` é `null` e a API mostra minutos úteis restantes; ao responder, calcula novo `dueAt`. Ao mudar prioridade, fechar o trecho anterior, preservar eventual violação e recalcular minutos restantes como `max(0, orçamento da nova prioridade − minutos já consumidos)`, a partir do instante da mudança; não reiniciar o relógio. `breachedAt` é o primeiro vencimento observado nos trechos do ciclo, derivável do histórico mesmo sem job. Ao resolver, fechar consumo e marcar violação se aplicável. Reabrir cria ciclo novo, mantendo todos os dados e violações do anterior. Testes usam relógio injetável e instantes UTC; horário de verão não é premissa do calendário fixo. A janela de reabertura inclui o instante exato `resolvedAt + 7 dias corridos`.

## 6. Concorrência, falhas e recuperação

Todos os comandos de escrita recebem `expectedVersion` e atualizam o pedido condicionalmente. Atribuir, transferir, classificar, mudar prioridade, pausar, responder, resolver e reabrir registram auditoria na mesma transação. Operações de duplicado bloqueiam/atualizam os dois pedidos em ordem estável de ID e validam as duas versões; conflito não deixa vínculo parcial. Desativar membership e desatribuir pedidos afetados é uma única operação transacional da aplicação, com locks/ordenação definidos na implementação.

O cálculo de prazo é puro e reexecutável; indisponibilidade de worker/Rabbit/IA não pode impedir operação manual. Retry de comando HTTP deve usar leitura de estado após timeout ambíguo antes de nova escrita; `expectedVersion` evita sobrescrever decisão, mas não substitui idempotência de respostas ou auditoria. Eventos futuros de prazo/atribuição não carregam texto de pergunta, resposta ou resolução.

## 7. Requisitos de qualidade e cenários de aceite

- **NFR2-001:** testes de concorrência com PostgreSQL real cobrem dupla atribuição, classificação versus IA tardia, remoção de responsável e vínculo simultâneo.
- **NFR2-002:** tabela de testes de relógio cobre abertura antes/depois do horário útil, sexta→segunda, pausa/resposta, troca de prioridade, resolução após vencimento e reabertura.
- **NFR2-003:** testes negativos cobrem papéis, autoria, workspace, cursor/filtro e IDs de duplicados; respostas não revelam pedido alheio.
- **NFR2-004:** cada transição produz uma trilha auditável sem texto livre em logs/eventos; teste de integração verifica atomicidade e histórico.

1. **Dupla atribuição:** dois agentes usam a mesma versão; um assume, outro recebe `409 VERSION_CONFLICT`.
2. **Membro removido:** admin desativa responsável; pedido ativo fica sem responsável, histórico registra a mudança e último admin não pode ser removido.
3. **Prioridade:** impacto `TEAM` + urgência `HIGH` gera P2; override P1 sem motivo é rejeitado; com motivo registra ambos.
4. **Prazo:** abrir sexta às 17:00 com P1 vence segunda às 12:00; uma hora útil antes e três depois do fim de semana.
5. **Pausa:** pergunta em `WAITING_REQUESTER` congela consumo; resposta do solicitante o retoma; resposta de outro requester retorna `404`.
6. **Espera sem resposta:** admin cancela espera com motivo; prazo retoma uma vez e resposta tardia do requester recebe conflito.
7. **Reabertura:** contestação dentro de 7 dias abre ciclo novo; depois retorna `409 REOPEN_WINDOW_EXPIRED`; ciclo anterior continua consultável.
8. **Duplicado:** vínculo entre workspaces, cadeia ou auto-vínculo falha sem alteração; requester não vê vínculo nem ID do pedido alheio.
9. **IA tardia:** classificação humana vence e sugestão tardia não muda estado, prioridade ou prazo.

## 8. Questões para revisão dos participantes

Validar se o calendário local de 09:00–18:00, os quatro prazos, a janela de 7 dias e a matriz representam um exercício útil. São decisões didáticas propostas, sem evidência de operação real; participantes podem alterá-las na spec antes de implementar o comportamento afetado. Nenhuma métrica de SLA real é alegada.
