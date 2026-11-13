package com.mentoria.triagem.requests.internal;

import java.util.List;
import java.util.Optional;

/**
 * Porta de persistencia do modulo requests. A implementacao de producao e
 * {@link JdbcRequestStore} (PostgreSQL); os testes de dominio usam uma implementacao em
 * memoria, que vive apenas no codigo de teste.
 */
public interface RequestStore {

  record IdempotencyRecord(String requestId, int bodyHash) {}

  /**
   * Insere ou atualiza o agregado. A atualizacao e condicional a
   * {@link ServiceRequest#persistedVersion()}: perder a corrida levanta
   * {@link com.mentoria.triagem.requests.VersionConflictException}.
   */
  void save(ServiceRequest request);

  Optional<ServiceRequest> findById(String workspaceId, String requestId);

  List<ServiceRequest> findByWorkspace(String workspaceId);

  Optional<IdempotencyRecord> findIdempotency(String scopedKey);

  void rememberIdempotency(String scopedKey, String requestId, int bodyHash);

  Optional<String> findActiveRequestForConversation(String workspaceId, String conversationId);
}
