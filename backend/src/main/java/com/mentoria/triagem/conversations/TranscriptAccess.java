package com.mentoria.triagem.conversations;

import java.time.Instant;

/** Registro de leitura da transcricao por uma pessoa agente (FR3-018). */
public record TranscriptAccess(String conversationId, String actor, Instant at) {}
