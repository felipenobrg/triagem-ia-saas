package com.mentoria.triagem.conversations;

import java.time.Instant;
import java.util.List;

/**
 * Projecao da conversa para o autor e para o tecnico (depois do escalonamento).
 * Vereditos do JEV nao sao expostos ao requester alem do efeito observavel.
 */
public record ConversationView(
    String conversationId,
    String workspaceId,
    String authorSubject,
    ConversationStatus status,
    long version,
    int assistantTurnCount,
    int turnsRemaining,
    String escalatedRequestId,
    String escalationReason,
    boolean confirmationPending,
    List<TurnView> turns,
    Instant createdAt,
    Instant lastAuthorMessageAt) {}
