package com.mentoria.triagem.conversations.internal;

import com.mentoria.triagem.conversations.TurnAuthor;
import com.mentoria.triagem.conversations.TurnState;
import java.time.Instant;

/** Turno imutavel da conversa. Nunca editado nem apagado, nem por admin (BR3-003). */
public record ConversationTurn(
    int sequence, TurnAuthor author, String text, TurnState state, Instant at) {}
