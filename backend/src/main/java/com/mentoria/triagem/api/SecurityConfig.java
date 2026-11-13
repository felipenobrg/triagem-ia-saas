package com.mentoria.triagem.api;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Autenticacao OIDC (FR-001).
 *
 * <p>O token e emitido pelo Keycloak com o issuer publico (por exemplo
 * {@code http://localhost:8081/realms/triagem}), mas o backend, dentro do Compose, alcanca o
 * Keycloak por outro endereco. Por isso as chaves vem de {@code app.auth.jwk-set-uri}
 * (interno) e o issuer e validado contra {@code app.auth.issuer} (externo, o que aparece no
 * token). Audience tambem e exigida: token de outro publico nao serve aqui.
 *
 * <p>Papel de negocio <b>nunca</b> vem do token: quem autoriza e o membership no workspace
 * (ADR-0005, FR-002).
 */
@Configuration
@Profile("!devstub")
public class SecurityConfig {

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${app.auth.jwk-set-uri}") String jwkSetUri,
      @Value("${app.auth.issuer}") String issuer,
      @Value("${app.auth.audience:}") String audience) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    OAuth2TokenValidator<Jwt> validators =
        new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(),
            JwtValidators.createDefaultWithIssuer(issuer),
            audienceValidator(audience));
    decoder.setJwtValidator(validators);
    return decoder;
  }

  private static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
    if (audience == null || audience.isBlank()) {
      return token -> OAuth2TokenValidatorResult.success();
    }
    return token -> {
      List<String> audiences = token.getAudience();
      if (audiences != null && audiences.contains(audience)) {
        return OAuth2TokenValidatorResult.success();
      }
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error(
              "invalid_token",
              "o token nao foi emitido para " + audience,
              null));
    };
  }

  @Bean
  SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            registry ->
                registry
                    // Health check nao expoe dado de negocio (NFR-001).
                    .requestMatchers("/api/v1/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter())));
    return http.build();
  }

  private static org.springframework.core.convert.converter.Converter<
          Jwt, org.springframework.security.authentication.AbstractAuthenticationToken>
      converter() {
    return jwt ->
        new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
            jwt, List.of(), jwt.getClaimAsString(JwtClaimNames.SUB));
  }
}
