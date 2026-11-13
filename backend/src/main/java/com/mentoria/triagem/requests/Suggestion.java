package com.mentoria.triagem.requests;

import java.time.Instant;

/** Sugestao nao confiavel ate a revisao humana (FR-011, BR-004). */
public record Suggestion(
    Category category, Urgency urgency, String summary, String origin, Instant createdAt) {}
