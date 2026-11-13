package com.mentoria.triagem.identity;

import java.util.List;
import java.util.Optional;

/**
 * Porta de persistencia de membership. A implementacao de producao usa PostgreSQL; os testes
 * de dominio usam uma em memoria, que vive apenas no codigo de teste.
 */
public interface MembershipRepository {

  void upsert(Membership membership);

  Optional<Membership> find(String workspaceId, String subject);

  List<Membership> activeMembers(String workspaceId);

  long activeAdminCount(String workspaceId);
}
