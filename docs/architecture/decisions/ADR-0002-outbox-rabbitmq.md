# ADR-0002: Outbox transacional e RabbitMQ

- **Estado:** escolhido para MVP didático; confirmar operação em testes
- **Data:** 2026-10-06

## Contexto

Triagem pode ser lenta e depende de serviço externo. Commit do pedido e intenção de processá-lo não podem divergir. O projeto também pretende ensinar mensageria, redelivery e recuperação, mantendo um único backend.

## Decisão

Persistir request, audit e outbox na mesma transação PostgreSQL. Dispatcher com lease publica mensagem persistente e versionada, com IDs/correlation apenas. Usar publisher confirm e `mandatory=true`; tratar `basic.return` porque broker confirm não prova roteabilidade. Marcar como publicado só após confirm sem retorno. Worker usa inbox/event ID idempotente, grava resultado antes de ACK e classifica falhas. Retry limitado e agendado; poison vai a DLQ. Entrega é at-least-once.

Timeout depois do envio a provedor, sem saber se inferência completou, vira `OUTCOME_UNKNOWN`; não repetir automaticamente uma chamada potencialmente cobrada. Reprocesso é explícito, autorizado e auditado.

## Alternativas

- Chamada síncrona no request: acopla intake à latência/disponibilidade IA.
- Publicar após commit sem outbox: janela de perda entre banco e broker.
- Polling só na tabela: possível simplificação futura; Rabbit agrega valor didático explícito nesta mentoria.
- Kafka: operação maior sem necessidade de streaming/replay em escala demonstrada.

## Consequências

Mais componentes, eventual consistency e operação de backlog, returns, retries e DLQ. Idempotência não torna efeito externo exactly-once. Caminho manual preserva utilidade se dependência parar.

## Referência e revisão

[RabbitMQ: publisher confirms e returns](https://www.rabbitmq.com/docs/publishers). Reavaliar se testes e operação mostrarem job runner mais simples suficiente ou se esforço de broker impedir aprendizado do domínio.
