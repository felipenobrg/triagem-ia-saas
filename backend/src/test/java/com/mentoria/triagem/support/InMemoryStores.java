package com.mentoria.triagem.support;

import com.mentoria.triagem.conversations.ConversationStatus;
import com.mentoria.triagem.conversations.TranscriptAccess;
import com.mentoria.triagem.conversations.internal.Conversation;
import com.mentoria.triagem.conversations.internal.ConversationStore;
import com.mentoria.triagem.conversations.internal.JudgeVerdictRecord;
import com.mentoria.triagem.identity.Membership;
import com.mentoria.triagem.identity.MembershipRepository;
import com.mentoria.triagem.identity.Role;
import com.mentoria.triagem.requests.internal.RequestStore;
import com.mentoria.triagem.requests.internal.ServiceRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementacoes em memoria das portas de persistencia, usadas apenas nos testes de dominio.
 * Elas nao substituem o teste de integracao com PostgreSQL: concorrencia real, indices e
 * transacao sao verificados em {@code PersistenceAndMessagingIT}.
 */
public final class InMemoryStores {

  private InMemoryStores() {}

  public static class Memberships implements MembershipRepository {
    private final Map<String, Membership> byKey = new HashMap<>();

    private static String key(String workspaceId, String subject) {
      return workspaceId + "::" + subject;
    }

    @Override
    public void upsert(Membership membership) {
      byKey.put(key(membership.workspaceId(), membership.subject()), membership);
    }

    @Override
    public Optional<Membership> find(String workspaceId, String subject) {
      return Optional.ofNullable(byKey.get(key(workspaceId, subject)));
    }

    @Override
    public List<Membership> activeMembers(String workspaceId) {
      return byKey.values().stream()
          .filter(m -> m.workspaceId().equals(workspaceId) && m.active())
          .toList();
    }

    @Override
    public long activeAdminCount(String workspaceId) {
      return byKey.values().stream()
          .filter(m -> m.workspaceId().equals(workspaceId) && m.active() && m.role() == Role.ADMIN)
          .count();
    }
  }

  public static class Requests implements RequestStore {
    private final Map<String, ServiceRequest> byId = new HashMap<>();
    private final Map<String, IdempotencyRecord> idempotency = new HashMap<>();

    @Override
    public void save(ServiceRequest request) {
      byId.put(request.id(), request);
      request.markPersisted();
    }

    @Override
    public Optional<ServiceRequest> findById(String workspaceId, String requestId) {
      return Optional.ofNullable(byId.get(requestId))
          .filter(request -> request.workspaceId().equals(workspaceId));
    }

    @Override
    public List<ServiceRequest> findByWorkspace(String workspaceId) {
      return byId.values().stream()
          .filter(request -> request.workspaceId().equals(workspaceId))
          .sorted(Comparator.comparing(ServiceRequest::createdAt).thenComparing(ServiceRequest::id))
          .toList();
    }

    @Override
    public Optional<IdempotencyRecord> findIdempotency(String scopedKey) {
      return Optional.ofNullable(idempotency.get(scopedKey));
    }

    @Override
    public void rememberIdempotency(String scopedKey, String requestId, int bodyHash) {
      idempotency.putIfAbsent(scopedKey, new IdempotencyRecord(requestId, bodyHash));
    }

    @Override
    public Optional<String> findActiveRequestForConversation(
        String workspaceId, String conversationId) {
      return byId.values().stream()
          .filter(request -> request.workspaceId().equals(workspaceId))
          .filter(request -> conversationId.equals(request.conversationId()))
          .filter(request -> !request.status().isTerminal())
          .map(ServiceRequest::id)
          .findFirst();
    }
  }

  public static class Conversations implements ConversationStore {
    private final Map<String, Conversation> byId = new HashMap<>();
    private final List<JudgeVerdictRecord> verdicts = new ArrayList<>();
    private final List<TranscriptAccess> accessLog = new ArrayList<>();
    private final Map<String, String> idempotency = new HashMap<>();

    @Override
    public void save(Conversation conversation) {
      byId.put(conversation.id(), conversation);
      conversation.markPersisted();
    }

    @Override
    public Optional<Conversation> find(String workspaceId, String conversationId) {
      return Optional.ofNullable(byId.get(conversationId))
          .filter(conversation -> conversation.workspaceId().equals(workspaceId));
    }

    @Override
    public List<Conversation> findByAuthor(String workspaceId, String authorSubject) {
      return byId.values().stream()
          .filter(
              c -> c.workspaceId().equals(workspaceId) && c.authorSubject().equals(authorSubject))
          .sorted(Comparator.comparing(Conversation::createdAt).thenComparing(Conversation::id))
          .toList();
    }

    @Override
    public long countActiveByAuthor(String workspaceId, String authorSubject) {
      return byId.values().stream()
          .filter(
              c -> c.workspaceId().equals(workspaceId) && c.authorSubject().equals(authorSubject))
          .filter(
              c ->
                  c.status() == ConversationStatus.ACTIVE
                      || c.status() == ConversationStatus.AWAITING_CONFIRMATION)
          .count();
    }

    @Override
    public List<Conversation> findInactive(Instant cutoff) {
      return byId.values().stream()
          .filter(
              c ->
                  c.status() == ConversationStatus.ACTIVE
                      || c.status() == ConversationStatus.AWAITING_CONFIRMATION)
          .filter(c -> c.lastAuthorMessageAt().isBefore(cutoff))
          .toList();
    }

    @Override
    public void recordVerdict(JudgeVerdictRecord verdict) {
      verdicts.add(verdict);
    }

    @Override
    public List<JudgeVerdictRecord> verdictsOf(String conversationId) {
      return verdicts.stream().filter(v -> v.conversationId().equals(conversationId)).toList();
    }

    @Override
    public void recordAccess(String workspaceId, String conversationId, String actor, Instant at) {
      accessLog.add(new TranscriptAccess(conversationId, actor, at));
    }

    @Override
    public List<TranscriptAccess> accessLog(String conversationId) {
      return accessLog.stream()
          .filter(entry -> entry.conversationId().equals(conversationId))
          .toList();
    }

    @Override
    public Optional<String> findIdempotentConversation(String scopedKey) {
      return Optional.ofNullable(idempotency.get(scopedKey));
    }

    @Override
    public void rememberIdempotency(String scopedKey, String conversationId) {
      idempotency.putIfAbsent(scopedKey, conversationId);
    }
  }
}
