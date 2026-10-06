# ADR-0003: IA sugere; pessoa decide

- **Estado:** escolhido para MVP
- **Data:** 2026-10-06

## Contexto

Texto livre não é confiável e modelos podem classificar errado, retornar conteúdo inválido ou sofrer prompt injection. O objetivo de produto é triagem assistida, com controle e alternativa manual.

## Decisão

Provedor substituível `TriageProvider`; fake default; OpenAI opt-in, sintético e com cap de aplicação US$5/mês. Saída limitada a categoria/urgência/resumo, validada no servidor. Uma pessoa `AGENT`/`ADMIN` aprova, edita/aprova ou rejeita. IA nunca muda estado operacional. Timeout ambíguo não é retry cego. Avaliar baseline em conjunto sintético antes de propor limiar.

## Alternativas

- Aprovação automática: tira controle humano e pode encaminhar incorretamente.
- SDK ligado ao domínio: dificulta fake e substituição.
- Fixar precisão alvo antes de medir: não há baseline ou caso de uso que sustente número.
- RAG no primeiro slice: amplia risco de dados e avaliação antes de provar valor de inferência sem retrieval.

## Consequências

Sugestão e revisão são entidades distintas; UX mostra rascunho e fonte. Orçamento precisa de reserva atômica, preço/modelo configurado e limite na conta externa. Avaliação sintética não demonstra resultado em operação real.

## Reavaliar

Rever provider/modelo/cap antes de chamada real conforme preço e API atuais. RAG só por spec nova quando hipótese mensurável surgir; consultar [roadmap](../../roadmap.md).
