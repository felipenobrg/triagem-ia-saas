package com.mentoria.triagem.identity;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API publica do modulo identity. Autorizacao e sempre resolvida aqui, a partir de membership
 * ativo no workspace (FR-002, BR-001). Papel de negocio nunca vem do token (ADR-0005).
 */
@Service
public class MembershipService {

  private final MembershipRepository repository;

  public MembershipService(MembershipRepository repository) {
    this.repository = repository;
  }

  /** Associa ou altera membership. Mantem a regra do ultimo admin ativo (FR-004). */
  @Transactional
  public Membership upsert(Membership membership) {
    Membership previous = repository.find(membership.workspaceId(), membership.subject()).orElse(null);
    boolean losingAdmin =
        previous != null
            && previous.active()
            && previous.role() == Role.ADMIN
            && (!membership.active() || membership.role() != Role.ADMIN);
    if (losingAdmin && repository.activeAdminCount(membership.workspaceId()) <= 1) {
      throw new LastAdminException("workspace precisa de pelo menos um admin ativo");
    }
    repository.upsert(membership);
    return membership;
  }

  public Optional<Membership> find(String workspaceId, String subject) {
    return repository.find(workspaceId, subject);
  }

  public List<Membership> activeMembers(String workspaceId) {
    return repository.activeMembers(workspaceId);
  }

  public long activeAdminCount(String workspaceId) {
    return repository.activeAdminCount(workspaceId);
  }

  /**
   * Exige membership ativo e papel permitido. O workspaceId recebido do cliente e apenas
   * seletor: quem autoriza e este metodo (FR-002).
   */
  public Membership requireMember(String workspaceId, String subject, Role... allowedRoles) {
    Membership membership =
        repository
            .find(workspaceId, subject)
            .filter(Membership::active)
            .orElseThrow(() -> new NotAuthorizedException("sem membership ativo no workspace"));
    Set<Role> allowed =
        allowedRoles.length == 0 ? EnumSet.allOf(Role.class) : EnumSet.copyOf(List.of(allowedRoles));
    if (!allowed.contains(membership.role())) {
      throw new NotAuthorizedException("papel insuficiente para a operacao");
    }
    return membership;
  }

  public Membership requireAgentOrAdmin(String workspaceId, String subject) {
    return requireMember(workspaceId, subject, Role.AGENT, Role.ADMIN);
  }
}
