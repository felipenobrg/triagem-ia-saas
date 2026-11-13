package com.mentoria.triagem.conversations;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites da conversa. Sao valores propostos na Spec 003 e ainda nao validados por evidencia:
 * a dupla deve revisar na spec antes de tratar qualquer um deles como definitivo (T3001).
 */
@ConfigurationProperties(prefix = "app.conversation")
public record ConversationLimits(
    Integer maxAssistantTurns,
    Integer maxActiveConversations,
    Integer maxMessageLength,
    Double resolvedConfidenceThreshold,
    Integer inactivityHours,
    Integer reopenDays) {

  public ConversationLimits {
    maxAssistantTurns = maxAssistantTurns == null ? 10 : maxAssistantTurns;
    maxActiveConversations = maxActiveConversations == null ? 5 : maxActiveConversations;
    maxMessageLength = maxMessageLength == null ? 2000 : maxMessageLength;
    resolvedConfidenceThreshold =
        resolvedConfidenceThreshold == null ? 0.70 : resolvedConfidenceThreshold;
    inactivityHours = inactivityHours == null ? 72 : inactivityHours;
    reopenDays = reopenDays == null ? 7 : reopenDays;
  }

  public static ConversationLimits defaults() {
    return new ConversationLimits(null, null, null, null, null, null);
  }
}
