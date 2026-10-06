# ADR-0002: Outbox transacional com RabbitMQ para triagem

- **Status:** Accepted for the educational MVP
- **Date:** 2026-10-06

## Context

Classificação por IA tem latência e falhas externas. A submissão deve ser rápida e durável; request e intenção de triagem não podem divergir se o processo cair entre commit no banco e publish.

## Decision

Gravar request e outbox event na mesma transação PostgreSQL. Um dispatcher publica evento persistente em RabbitMQ e marca como publicado após publisher confirm. Worker processa com chave idempotente, persiste sugestão/falha e só então confirma a mensagem. Usar retries limitados, dead-letter queue, correlation IDs e replay auditado. Garantia esperada: at-least-once; consumidores idempotentes.

## Alternatives considered

- **Chamada síncrona ao provedor na requisição:** rejeitada por acoplar disponibilidade/latência da IA à intake.
- **Publicar direto no broker depois do commit:** rejeitada porque crash entre as operações pode perder evento.
- **Kafka/streaming platform:** não selecionada; exige mais operação e não há necessidade de replay/stream throughput demonstrada.
- **Polling só na tabela de requests:** possível simplificação, mas reduz oportunidade de praticar broker e controle explícito de entrega, que são objetivos deste projeto.

## Consequences

- Introduz eventual consistency, dispatcher, broker topology, retry e DLQ que precisam ser observados e operados.
- Outbox cresce se publicação parar; alertar por idade/quantidade e documentar retenção.
- Publisher confirm evita marcar mensagem não aceita como publicada, mas não torna provider inference exactly-once.
- O produto deve permanecer utilizável via triagem manual quando o worker/broker/provider parar.

## Revisit when

Métricas mostrarem que um job runner simples seria mais adequado, ou custo/complexidade do RabbitMQ superar o valor didático e operacional. Reavaliar broker conforme necessidade e não apenas popularidade.
