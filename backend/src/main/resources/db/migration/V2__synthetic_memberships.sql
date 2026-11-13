-- Material de estudo: membros exclusivamente sinteticos do workspace de demonstracao.
-- Nenhuma pessoa, conta ou dado real. Os IDs correspondem aos usuarios do realm
-- infra/keycloak/realm-triagem.json: com Keycloak, o subject do token (claim "sub") e este UUID.
-- Papeis de negocio ficam na aplicacao, nao no token (ADR-0005).

INSERT INTO membership (workspace_id, subject, role, active) VALUES
  ('ws-demo',  '11111111-1111-1111-1111-111111111111', 'REQUESTER', TRUE),  -- ana
  ('ws-demo',  '22222222-2222-2222-2222-222222222222', 'AGENT',     TRUE),  -- bruno
  ('ws-demo',  '33333333-3333-3333-3333-333333333333', 'ADMIN',     TRUE),  -- carla
  ('ws-outro', '44444444-4444-4444-4444-444444444444', 'REQUESTER', TRUE),  -- dani
  ('ws-outro', '33333333-3333-3333-3333-333333333333', 'ADMIN',     TRUE)   -- carla administra o outro workspace
ON CONFLICT (workspace_id, subject) DO NOTHING;

-- Apelidos usados apenas quando o perfil "devstub" esta ativo (identidade pelo header
-- X-Dev-Subject, sem Keycloak). Nao tem efeito quando o token OIDC e exigido.
INSERT INTO membership (workspace_id, subject, role, active) VALUES
  ('ws-demo',  'requester-ana', 'REQUESTER', TRUE),
  ('ws-demo',  'agent-bruno',   'AGENT',     TRUE),
  ('ws-demo',  'admin-carla',   'ADMIN',     TRUE),
  ('ws-outro', 'requester-dani','REQUESTER', TRUE),
  ('ws-outro', 'admin-eva',     'ADMIN',     TRUE)
ON CONFLICT (workspace_id, subject) DO NOTHING;
