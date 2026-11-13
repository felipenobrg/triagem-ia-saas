package com.mentoria.triagem.conversations.internal;

import com.mentoria.triagem.conversations.JudgeOutcome;
import com.mentoria.triagem.conversations.VerdictState;
import java.time.Instant;

/**
 * Registro de uma avaliacao do JEV, ligada ao turno avaliado. Guardado para auditoria e
 * telemetria; o texto da conversa nao e copiado para ca (FR3-022).
 */
public record JudgeVerdictRecord(
    String conversationId,
    String workspaceId,
    int turnSequence,
    JudgeOutcome outcome,
    VerdictState state,
    String rejectionReason,
    Instant at) {}
