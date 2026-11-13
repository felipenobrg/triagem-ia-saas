package com.mentoria.triagem.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Resolve o subject da requisicao.
 *
 * <p>Caminho normal: o subject e a claim {@code sub} do access token validado pelo Keycloak
 * (FR-001). O papel de negocio nao vem do token: quem autoriza e o membership no workspace
 * (FR-002).
 *
 * <p>Com o perfil {@code devstub} ativo, o subject vem do header {@code X-Dev-Subject}. Isso
 * existe para teste e para rodar sem Keycloak, e nao satisfaz o FR-001.
 */
@Component
public class CurrentUserResolver {

  static final String DEV_SUBJECT_HEADER = "X-Dev-Subject";

  private final boolean devStubEnabled;

  public CurrentUserResolver(
      @Value("${app.auth.dev-stub-enabled:false}") boolean devStubEnabled) {
    this.devStubEnabled = devStubEnabled;
  }

  public String requireSubject(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtToken) {
      String subject = jwtToken.getToken().getSubject();
      if (subject != null && !subject.isBlank()) {
        return subject;
      }
      throw new UnauthenticatedException("token sem claim sub");
    }

    if (devStubEnabled) {
      String subject = request.getHeader(DEV_SUBJECT_HEADER);
      if (subject != null && !subject.isBlank()) {
        return subject.trim();
      }
      throw new UnauthenticatedException("header " + DEV_SUBJECT_HEADER + " ausente");
    }

    throw new UnauthenticatedException("access token OIDC ausente ou invalido");
  }

  /** Falta de identidade: responde 401. */
  public static class UnauthenticatedException extends RuntimeException {
    public UnauthenticatedException(String message) {
      super(message);
    }
  }
}
