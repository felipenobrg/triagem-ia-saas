package com.mentoria.triagem.requests;

import java.time.Instant;

/** Trilha de auditoria da solicitacao (FR-010). Nunca editavel pela API comum. */
public record HistoryEntry(
    String actor,
    String action,
    RequestStatus fromStatus,
    RequestStatus toStatus,
    long version,
    String reason,
    String correlationId,
    Instant at) {}
