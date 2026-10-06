# ADR-0003: IA gera sugestões revisadas por pessoas

- **Status:** Accepted for the educational MVP
- **Date:** 2026-10-06

## Context

Solicitações são texto não confiável; modelos podem falhar, classificar incorretamente, retornar conteúdo fora de formato ou ser influenciados por prompt injection. RAG pode melhorar contexto, mas amplia a superfície de exposição de dados.

## Decision

IA retorna apenas uma sugestão estruturada de categoria, urgência e resumo. Validar schema/enumerações/limites antes de persistir como sugestão. Um agente autorizado deve aceitar, corrigir ou rejeitar. Falha preserva pedido para fluxo manual. RAG fica para milestone posterior, usando só documentos sintéticos aprovados, filtro de workspace pré-retrieval, fontes visíveis, teste de remoção e avaliação. Nenhum uso de IA realiza ação operacional externa.

## Alternatives considered

- **Autocriar e encaminhar sem revisão:** rejeitado por tirar controle da equipe e deixar saída de modelo mudar o workflow.
- **Enviar todo conhecimento a cada chamada:** rejeitado por custo, vazamento cross-tenant e contexto desnecessário.
- **Tornar RAG obrigatório no primeiro incremento:** rejeitado até que a baseline sem retrieval e a avaliação permitam provar valor e isolamento.

## Consequences

- O modelo de dados precisa separar texto original, sugestão e decisão humana.
- UI precisa deixar claro o que é fonte, conteúdo recuperado, sugestão e edição humana.
- RAG exige corpus sintético, métricas de relevância/citação e deletion/reindex antes de qualquer habilitação.

## Revisit when

Testes/eval mostrem benefício mensurável de RAG, critérios de qualidade acordados e controles de isolamento/exclusão comprovados. Automação de escrita continua fora desta decisão.
