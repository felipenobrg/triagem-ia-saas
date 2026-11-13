# Estado da implementação

> **Para estudar:** esta página separa requisito **implementado e coberto por teste executado** de requisito **apenas especificado**. Ao ler, confira você mesmo: rode `./mvnw test` no `backend` e procure o teste citado. Se uma linha disser "implementado" sem teste correspondente, a linha está errada — corrija-a.

- **Data:** 2026-11-09
- **Fatia entregue:** domínio das Specs 001 e 003 sobre PostgreSQL, RabbitMQ e Keycloak reais, com provedores de IA **falsos**.
- **Evidência de teste:** `cd backend && ./mvnw test` → **47 testes, 0 falhas** nesta máquina (JDK 21). Inclui `FullStackFlowTest`, que sobe PostgreSQL e RabbitMQ via Testcontainers.
- **Evidência de ambiente:** `docker compose up -d --build` subiu os quatro serviços; o fluxo do chatbot foi executado com **token real do Keycloak** (`sub` = UUID do realm, `aud` = `triagem-backend`), com escalonamento assíncrono observado, leitura da transcrição pelo agente auditada, `403` para usuário de outro workspace e `401` sem token. No fim: `outbox_message` sem pendências, duas conversas (uma `ESCALATED`, uma `RESOLVED_CONFIRMED`) e exatamente **um** chamado.
- **Nenhuma tarefa foi marcada como concluída** em `tasks.md`: falta revisão independente e execução por outra pessoa em clone limpo (T504/T3502).

## O que está implementado

### Identidade e isolamento

| Requisito | Comportamento | Evidência |
| --- | --- | --- |
| `FR-001` | Resource server OIDC valida assinatura, expiração, **issuer** e **audience**. Issuer esperado e URL das chaves são configuráveis em separado, porque dentro do Compose o endereço interno do Keycloak difere do que aparece no token | Verificado contra o Compose: token válido passa, ausência de token responde `401` |
| `FR-002`, `FR-003`, `BR-001` | Autorização por membership ativo em PostgreSQL; papel **nunca** vem do token; requester só lê o que é dele | `RequestFlowTest.requesterLimitado`, `acessoCruzadoNaoRevelaRecurso`, `FullStackFlowTest.semMembershipResponde403` |
| `FR-004` | Workspace mantém pelo menos um admin ativo | `RequestFlowTest.ultimoAdminNaoPodeSerRemovido` |

### Solicitações (Spec 001)

| Requisito | Comportamento | Evidência |
| --- | --- | --- |
| `FR-005` | Criação idempotente; chave guardada como hash com escopo subject + workspace + operação | `RequestFlowTest.criacaoIdempotente` |
| `FR-006` | Texto original imutável; complemento em tabela própria | `RequestFlowTest.textoOriginalEhImutavel` |
| `FR-008`, `NFR-004` | Tabela de transições e versão otimista com `UPDATE ... WHERE version = ?` | `RequestFlowTest.transicaoInvalida`, `revisoesConcorrentes`, `FullStackFlowTest.conflitoDeVersaoNoBanco` |
| `FR-009`, `FR-010` | Classificação manual com bypass justificado; histórico auditável | `RequestFlowTest.bypassExigeMotivo`, `aprovacaoHumanaAbreAtendimento` |
| `FR-011`–`FR-013`, `BR-004` | Rota da IA validada no servidor; só pessoa aprova, edita ou rejeita | `RequestFlowTest.analiseInicialLevaAFilaHumanaComSugestao`, `textoVagoPedeComplementoUmaVez`, `rejeicaoVoltaAReceived` |
| `FR-014` | Porta `TriageProvider` com fake determinístico | Toda a suíte |
| `FR-023`, `FR-024`, `BR-010` | Uma rodada de complemento; pedido direto de pessoa agente; falha e incerteza vão ao humano sem sugestão | `textoVagoPedeComplementoUmaVez`, `requesterPedePessoaAgente` |

### Consistência e mensageria

| Requisito | Comportamento | Evidência |
| --- | --- | --- |
| `FR-018` | Solicitação, histórico e evento de outbox na mesma transação | `FullStackFlowTest.analiseInicialChegaPorEvento` (resposta sai em `RECEIVED`, estado muda depois) |
| `FR-019` | Dispatcher com lease, publisher confirms e `mandatory=true`; só marca publicado após confirmação sem retorno. Mensagem sem binding **continua pendente e visível** | `FullStackFlowTest.eventoSemBindingFicaPendenteEVisivel` |
| `FR-020`, `FR-022` | Inbox única por consumidor + evento; ack após persistência; entrega at-least-once declarada | `analiseInicialChegaPorEvento` (conta linhas de `inbox_message`) |
| `FR-021` | Retry limitado com `next_attempt_at` na outbox; retry de consumo limitado e DLQ configurada (`triagem.dlq`) | Filas e DLQ observadas no Compose; `eventoSemBindingFicaPendenteEVisivel` cobre o retry do publisher |
| `BR-008`, `BR3-009` | Payload de evento só com IDs, sequência e correlation | Inspeção da coluna `payload` e revisão do código de publicação |

### Conversa e JEV (Spec 003)

| Requisito | Comportamento | Evidência |
| --- | --- | --- |
| `FR3-001`–`FR3-003` | Conversa com turnos imutáveis e ordenados; abertura idempotente; limites de turno, de conversas ativas e de tamanho | `ConversationFlowTest.aberturaIdempotente`, `limiteDeTurnosEscala`, `limiteDeConversasAtivas`, `FullStackFlowTest.turnosSaoPersistidos` |
| `FR3-004`, `BR3-006` | Pedido de humano escala sem consultar o JEV | `pedidoDeHumanoPrevaleceSemConsultarJev` |
| `FR3-005`, `BR3-005` | Só o autor confirma resolução; negar volta ou escala | `resolveNaConversaNaoCriaChamado`, `jevNaoEncerraSozinho`; verificado também no Compose |
| `FR3-006` | Inatividade encerra de forma reversível, com janela de reabertura | `inatividadeEncerraDeFormaReversivel`, `foraDaJanelaDeReaberturaRecusa` |
| `FR3-007` | Admin escala conversa presa, com motivo obrigatório | `adminEscalaComMotivo` |
| `FR3-008` | **Avaliação assíncrona de verdade:** o turno do assistente grava a intenção; o consumidor aplica o veredito | `FullStackFlowTest.conversaEscalaPorVereditoAssincrono` |
| `FR3-009`, `FR3-010` | Veredito validado (rota, motivo, confiança, limites); confiança baixa não resolve | `vereditoInvalidoEscala`, `confiancaBaixaNaoResolve` |
| `FR3-011` | Veredito de turno superado fica `OBSOLETE` | `vereditoObsoletoNaoMudaEstado` |
| `FR3-012`, `FR3-013` | Duas portas de IA separadas; nenhuma encerra conversa | `jevNaoEncerraSozinho`, `falhaDoJevEscala` |
| `FR3-014`–`FR3-016`, `BR3-007` | Escalonamento transacional com contexto; **índice parcial único** garante um chamado não terminal por conversa | `escalaComContextoDoProblema`, `escalonamentoNaoDuplicaChamado`, `FullStackFlowTest.conversaEscalaPorVereditoAssincrono` e `migracoesCriamSchemaESeedSintetico` |
| `FR3-017`, `BR3-008` | Chamado da conversa nasce em `NEEDS_REVIEW` e exige decisão humana | `chamadoDaConversaExigeDecisaoHumana` |
| `FR3-018`, `BR3-010` | Transcrição privada até escalar; leitura do técnico pelo chamado, gravada em `transcript_access` | `conversaEhPrivadaAntesDoEscalonamento`, `tecnicoLeTranscricaoComAuditoria`, `conversaEscalaPorVereditoAssincrono` |
| `FR3-019`, `FR3-020` | Falha não grava resposta falsa; timeout vira `OUTCOME_UNKNOWN`, sem retry automático | `provedorIndisponivelNaoGravaRespostaFalsa`, `timeoutAmbiguoMarcaOutcomeUnknown` |
| `BR3-002`, `BR3-003`, `BR3-011` | Agent/admin não escrevem na conversa; turnos nunca editados; injeção de prompt não muda estado e saída que pede credencial não é gravada | `agentNaoEscreveNaConversa`, `injecaoDePromptNaoResolveConversa`, `respostaQuePedeCredencialNaoEhGravada` |
| `NFR-001`, `NFR-007`, `NFR3-007` | Ambiente sobe por Compose com migrações versionadas; limites entre módulos verificados | `ModuleBoundariesTest`, `migracoesCriamSchemaESeedSintetico` |

## O que **não** está implementado

| Requisito | Situação |
| --- | --- |
| `FR-007` | Lista sem paginação por cursor; filtros reduzidos a estado |
| `FR-015`, `FR-016`, `FR3-021`, `FR3-022` | **Sem provedor de IA real**, sem reserva de orçamento, sem custo máximo por conversa, sem telemetria de provedor/modelo/uso |
| `FR-017`, `FR3-023`, `NFR-006`, `NFR3-006` | Nenhum conjunto sintético rodado; **nenhum baseline medido** |
| `NFR-003` | Logs estruturados mínimos; sem métricas, sem painéis, sem alerta de idade de outbox |
| `NFR-005` | Sem backup/restore documentado ou exercitado; replay de DLQ não implementado |
| `NFR-002` (parcial) | Testes negativos cobrem serviço e API; falta cobrir consulta e FK com dados de dois workspaces no banco |
| Toda a Spec 002 | Nada implementado |
| Frontend | Não existe |
| CI | Não configurado |
| Agendador de inatividade | Método existe e é testado; nada o chama periodicamente |
| `ConversationEscalated.v1` | Está no contrato, mas **não é publicado**: não há consumidor, e publicar sem binding acumularia mensagem retornada na outbox |

## Desvios conscientes nesta fatia

1. **Chave idempotente usa `hashCode`.** A chave não é persistida em claro, mas `hashCode` não é hash criptográfico. Trocar por SHA-256 quando houver mais de um ambiente.
2. **Perfil `devstub`.** Mantido para teste e para rodar sem Keycloak; o header `X-Dev-Subject` substitui o token. Não satisfaz o FR-001 e está desligado por padrão.
3. **`api.version=1.44` fixado no Surefire.** O Testcontainers do BOM do Spring Boot negocia a API 1.32 do Docker, recusada pelo Docker 29. Remover quando o BOM atualizar.
4. **Seed de membership em migração.** `V2__synthetic_memberships.sql` insere os membros sintéticos, inclusive os apelidos usados pelo perfil `devstub`. Em um ambiente real, seed de autorização não entraria em migração.
5. **Sem replay autorizado de DLQ.** A DLQ existe e recebe mensagem envenenada, mas a operação de replay (`FR-021`) não foi implementada.

Cada desvio é dívida declarada, não decisão de produto.

## Para rodar a IA de verdade (o que ainda falta decidir e configurar)

O código já tem as três portas (`TriageProvider`, `ChatProvider`, `ConversationJudge`) e valida toda saída no servidor. Para ligar um provedor real, estas informações precisam existir — e as quatro primeiras são decisão da dupla, não detalhe de implementação:

1. **Provedor e modelo por papel.** A [ADR-0003](architecture/decisions/ADR-0003-human-reviewed-ai.md) fixou OpenAI como opção opt-in. São três papéis com exigências diferentes: conversa (latência baixa, custo por turno), JEV (saída estruturada curta e estável) e geração do contexto do problema. Trocar de provedor é mudança de ADR.
2. **Preço vigente por 1k tokens de entrada e de saída, por modelo.** Sem isso não existe reserva de orçamento: `FR-015` exige estimar o custo máximo **antes** da chamada e bloquear se não for possível estimar.
3. **Tetos.** Orçamento mensal da aplicação (a spec propõe US$5), custo máximo por conversa e limite de tokens de saída por chamada. Ao atingir o teto, a conversa **escala** em vez de continuar (`FR3-021`).
4. **Política de dados do provedor.** O que é enviado (transcrição inteira? últimos N turnos?), se há retenção no provedor e se o contrato permite. Os dados aqui são sintéticos; qualquer piloto real exige essa decisão antes.
5. **Credencial e configuração** (nenhuma delas no repositório): chave de API em variável de ambiente, URL base opcional, timeout por chamada, número de tentativas para erros transitórios conhecidos.
6. **Schema estruturado e versões.** JSON Schema da saída do JEV (`verdict`, `reasonCode`, `confidence`, `summary`, `suggestedCategory`, `suggestedUrgency`) e do contexto do problema, mais as versões de prompt e de schema, que `FR-016`/`FR3-022` exigem registrar na telemetria.
7. **Limiar de confiança e limite de turnos.** Hoje são 0,70 e 10 turnos, valores propostos sem evidência. Só o baseline (T3405) justifica mantê-los ou mudá-los.
8. **Conjunto sintético rotulado** de conversas e solicitações, com desfecho esperado, para medir taxa de escalonamento, **falsos resolvidos**, escalonamento tardio, validade de schema, latência e custo.

Com isso em mãos, a implementação que falta é: adapter do provedor com saída estruturada, reserva e reconciliação de orçamento, telemetria por chamada e tratamento de `429`/`5xx` conhecidos — tarefas T403–T405 e T3401–T3403. O tratamento de timeout ambíguo (`OUTCOME_UNKNOWN`) e o caminho "falha vira caminho humano" já estão implementados e testados com os fakes.
