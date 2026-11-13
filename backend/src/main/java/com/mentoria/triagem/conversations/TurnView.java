package com.mentoria.triagem.conversations;

import java.time.Instant;

/** Projecao de um turno. Texto imutavel, sequencia unica por conversa (BR3-003). */
public record TurnView(
    int sequence, TurnAuthor author, String text, TurnState state, Instant at) {}
