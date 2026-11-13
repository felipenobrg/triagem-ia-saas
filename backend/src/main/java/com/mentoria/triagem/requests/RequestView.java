package com.mentoria.triagem.requests;

import java.time.Instant;
import java.util.List;

/** Projecao de leitura da solicitacao. Outros modulos e a API nunca recebem o agregado. */
public record RequestView(
    String requestId,
    String workspaceId,
    String requesterSubject,
    String originalText,
    RequestSource source,
    String conversationId,
    RequestStatus status,
    long version,
    Suggestion suggestion,
    Category category,
    Urgency urgency,
    String pendingQuestion,
    String humanRoutingReason,
    ProblemContext problemContext,
    List<String> clarifications,
    List<HistoryEntry> history,
    Instant createdAt) {}
