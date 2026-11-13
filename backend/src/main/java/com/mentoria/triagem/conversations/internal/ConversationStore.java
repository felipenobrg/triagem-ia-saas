package com.mentoria.triagem.conversations.internal;

import com.mentoria.triagem.conversations.TranscriptAccess;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Porta de persistencia do modulo conversations. A implementacao de producao e
 * {@link JdbcConversationStore}; os testes de dominio usam uma implementacao em memoria,
 * que vive apenas no codigo de teste.
 */
public interface ConversationStore {

  /**
   * Insere ou atualiza a conversa e grava os turnos novos. A atualizacao e condicional a
   * {@link Conversation#persistedVersion()}.
   */
  void save(Conversation conversation);

  Optional<Conversation> find(String workspaceId, String conversationId);

  List<Conversation> findByAuthor(String workspaceId, String authorSubject);

  long countActiveByAuthor(String workspaceId, String authorSubject);

  /** Conversas abertas cuja ultima mensagem do autor e anterior ao corte (FR3-006). */
  List<Conversation> findInactive(Instant cutoff);

  void recordVerdict(JudgeVerdictRecord verdict);

  List<JudgeVerdictRecord> verdictsOf(String conversationId);

  void recordAccess(String workspaceId, String conversationId, String actor, Instant at);

  List<TranscriptAccess> accessLog(String conversationId);

  Optional<String> findIdempotentConversation(String scopedKey);

  void rememberIdempotency(String scopedKey, String conversationId);
}
