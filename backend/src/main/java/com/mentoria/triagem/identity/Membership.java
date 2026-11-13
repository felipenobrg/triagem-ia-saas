package com.mentoria.triagem.identity;

import java.util.Objects;

/** Associacao de um subject OIDC a um workspace, com papel e estado. */
public record Membership(String workspaceId, String subject, Role role, boolean active) {

  public Membership {
    Objects.requireNonNull(workspaceId, "workspaceId");
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(role, "role");
  }

  public Membership deactivated() {
    return new Membership(workspaceId, subject, role, false);
  }

  public Membership withRole(Role newRole) {
    return new Membership(workspaceId, subject, newRole, active);
  }
}
