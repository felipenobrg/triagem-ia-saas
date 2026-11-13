# ADR-0007: Conversa com IA e avaliador separado decidindo o escalonamento

> **Para estudar:** pegue uma conversa sintética em que o assistente "acha" que resolveu e o usuário continua travado. Discuta quem pode declarar isso resolvido, que evidência o técnico precisa receber no chamado e qual teste provaria que nada foi encerrado sozinho. Nenhuma taxa de resolução, satisfação ou economia foi medida aqui.

- **Estado:** proposta para revisão dos participantes; implementação pendente
- **Data:** 2026-11-09

## Contexto

Na [Spec 001](../../../specs/001-ai-assisted-request-triage/spec.md) o solicitante escreve um texto, a IA roteia e uma pessoa classifica. Duas limitações didáticas aparecem: o usuário não tem como tentar resolver sozinho antes de ocupar a fila humana, e o técnico recebe um texto curto, sem o que já foi tentado.

A [Spec 003](../../../specs/003-ai-chat-resolution/spec.md) propõe uma conversa com um agente de IA e, separado dele, um avaliador — chamado **JEV** no vocabulário do exercício — que julga a conversa e decide a rota: continuar, propor confirmação de resolução ao autor ou escalar. Isso levanta a questão que a constituição trata como central: até onde a IA pode decidir?

## Decisão proposta

Dois papéis de IA em portas distintas, `ChatProvider` (atendente) e `ConversationJudge` (JEV), com prompt, schema, telemetria e configuração próprios, fake determinístico por padrão e provedor real apenas por opt-in, dentro do mesmo teto de US$5/mês da aplicação e com custo máximo por conversa.

O JEV decide **apenas a rota operacional da conversa**. Ele não resolve, não classifica em definitivo, não cria atendimento e não encerra nada. Uma conversa só termina como resolvida por ato explícito do autor. Inatividade gera um estado reversível (`CLOSED_INACTIVE`), nunca "resolvido", e o autor ainda pode pedir pessoa humana por uma janela definida.

Quando a conversa não resolve, o escalonamento cria um chamado em `NEEDS_REVIEW` com contexto estruturado (resumo, sintomas, passos tentados, categoria e urgência sugeridas) numa única transação com vínculo, auditoria e evento. A revisão humana da Spec 001 continua obrigatória, e prioridade, prazo e responsável seguem a [Spec 002](../../../specs/002-service-workflow/spec.md).

A resposta do atendente é síncrona; a avaliação do JEV é assíncrona, por outbox e RabbitMQ, com retry limitado, DLQ e veredito obsoleto quando um turno novo ou uma decisão do autor chega primeiro. O escalonamento também tem gatilhos determinísticos, fora do modelo: pedido do autor, limite de turnos, orçamento bloqueado, veredito inválido e falha do provedor. A transcrição é privada ao autor até o escalonamento e, depois dele, legível por agent/admin apenas pelo chamado, com auditoria de acesso.

## Alternativas consideradas

- **Um único modelo atendendo e julgando:** menos chamadas e menos latência, mas o mesmo modelo que tentou resolver declararia o próprio sucesso, e prompt e telemetria ficariam misturados. Rejeitado para este exercício; pode ser medido depois contra o avaliador separado.
- **Decisão determinística sem avaliador:** totalmente testável (limite de turnos, pedido explícito, timeout), mas não exercita avaliação de IA e deixa escapar o caso em que a conversa claramente travou. Mantido como camada de guarda, não como única decisão.
- **Avaliação síncrona no mesmo request:** dobraria latência percebida e custo por turno e misturaria duas falhas diferentes numa só resposta.
- **Criar o chamado ao abrir o chat:** encheria a fila humana de problemas que a conversa resolve e tornaria impossível o desfecho "resolvido sem chamado".
- **Técnico respondendo dentro do chat:** exigiria presença, atribuição e tempo de resposta no canal; a Spec 002 já modela isso no chamado.
- **Fechamento automático por inatividade como resolução:** contraria a constituição e a [ADR-0003](ADR-0003-human-reviewed-ai.md).

## Consequências

Aparecem um módulo novo (`conversations`), duas portas de IA, estado conversacional com limites, um fluxo assíncrono adicional e um ponto de integração transacional com `requests`. O custo de IA por usuário passa a depender de quantos turnos ele gasta, então limite por conversa e teto de aplicação deixam de ser detalhe.

A transcrição é o dado mais sensível do projeto: texto livre, em volume, de uma pessoa identificada. Isso exige acesso restrito, auditoria de leitura, ausência de texto em log e evento e, antes de qualquer piloto real, uma decisão explícita sobre retenção, exclusão e base legal — nada disso está resolvido nesta ADR.

O conjunto sintético de conversas precisa medir especialmente **falsos resolvidos** (conversa dada como resolvida que volta em 7 dias) e **escalonamento tardio**, porque é aí que um avaliador otimista machuca o usuário.

## Reavaliar

Rever limite de turnos, limiar de confiança, janelas de inatividade e reabertura e custo por conversa depois do primeiro baseline. Comparar o avaliador separado com a alternativa de modelo único quando houver medição em conjunto sintético. Qualquer uso real exigiria nova análise de privacidade, retenção e segurança da transcrição.
