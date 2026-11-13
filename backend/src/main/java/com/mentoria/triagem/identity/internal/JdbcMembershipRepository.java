package com.mentoria.triagem.identity.internal;

import com.mentoria.triagem.identity.Membership;
import com.mentoria.triagem.identity.MembershipRepository;
import com.mentoria.triagem.identity.Role;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Membership em PostgreSQL. Consultado em cada operacao de negocio (BR-001). */
@Component
public class JdbcMembershipRepository implements MembershipRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcMembershipRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void upsert(Membership membership) {
    jdbc.update(
        """
        INSERT INTO membership (workspace_id, subject, role, active, updated_at)
        VALUES (:workspaceId, :subject, :role, :active, now())
        ON CONFLICT (workspace_id, subject)
        DO UPDATE SET role = :role, active = :active, updated_at = now()
        """,
        new MapSqlParameterSource()
            .addValue("workspaceId", membership.workspaceId())
            .addValue("subject", membership.subject())
            .addValue("role", membership.role().name())
            .addValue("active", membership.active()));
  }

  @Override
  public Optional<Membership> find(String workspaceId, String subject) {
    return jdbc
        .query(
            "SELECT * FROM membership WHERE workspace_id = :workspaceId AND subject = :subject",
            Map.of("workspaceId", workspaceId, "subject", subject),
            (rs, row) ->
                new Membership(
                    rs.getString("workspace_id"),
                    rs.getString("subject"),
                    Role.valueOf(rs.getString("role")),
                    rs.getBoolean("active")))
        .stream()
        .findFirst();
  }

  @Override
  public List<Membership> activeMembers(String workspaceId) {
    return jdbc.query(
        "SELECT * FROM membership WHERE workspace_id = :workspaceId AND active ORDER BY subject",
        Map.of("workspaceId", workspaceId),
        (rs, row) ->
            new Membership(
                rs.getString("workspace_id"),
                rs.getString("subject"),
                Role.valueOf(rs.getString("role")),
                rs.getBoolean("active")));
  }

  @Override
  public long activeAdminCount(String workspaceId) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM membership"
                + " WHERE workspace_id = :workspaceId AND active AND role = 'ADMIN'",
            Map.of("workspaceId", workspaceId),
            Long.class);
    return count == null ? 0 : count;
  }
}
