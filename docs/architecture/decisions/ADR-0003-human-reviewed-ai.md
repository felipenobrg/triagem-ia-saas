# ADR-0003: IA sugere; pessoa decide

> **Para estudar:** compare o encaminhamento para humano com o pedido de complemento usando exemplos sintéticos. Procure um caso em que a IA retenha indevidamente um pedido útil e defina como o usuário chega a uma pessoa. Nenhuma qualidade de modelo foi medida aqui.

- **Estado:** escolhido para MVP
- **Data:** 2026-10-06

## Contexto

Texto livre não é confiável e modelos podem classificar errado, pedir complemento desnecessário, retornar conteúdo inválido ou sofrer prompt injection. O objetivo é colocar o agente de IA antes da fila humana sem permitir que ele esconda ou encerre pedidos definitivamente.

## Decisão

Provedor substituível `TriageProvider`; fake default; OpenAI opt-in, sintético e com cap de aplicação US$5/mês. A IA retorna `HUMAN_REVIEW` com categoria/urgência/resumo, ou `REQUEST_CLARIFICATION` com pergunta, sempre validado no servidor. A primeira rota leva à revisão humana; a segunda pede uma única rodada de complemento ao requester. Requester pode pedir humano diretamente. Incerteza, falha, saída inválida e limite de complemento encaminham ao humano sem sugestão. Uma pessoa `AGENT`/`ADMIN` aprova, edita/aprova, rejeita ou classifica manualmente. IA nunca abre atendimento, descarta pedido ou define classificação final. Timeout ambíguo não é retry cego. Avaliar roteamento e classificação em conjunto sintético antes de propor limiar.

## Alternativas

- Aprovação automática: tira controle humano e pode encaminhar incorretamente.
- SDK ligado ao domínio: dificulta fake e substituição.
- Fixar precisão alvo antes de medir: não há baseline ou caso de uso que sustente número.
- RAG no primeiro slice: amplia risco de dados e avaliação antes de provar valor de inferência sem retrieval.

## Consequências

Sugestão, complemento e revisão são entidades distintas; UX mostra a etapa atual, pergunta ou rascunho e sua origem. O sistema precisa expor fila de exceções para revisão humana e permitir bypass manual auditado durante falha de infraestrutura. Orçamento precisa de reserva atômica, preço/modelo configurado e limite na conta externa. Avaliação sintética não demonstra resultado em operação real.

## Reavaliar

Rever provider/modelo/cap antes de chamada real conforme preço e API atuais. RAG só por spec nova quando hipótese mensurável surgir; consultar [roadmap](../../roadmap.md).
