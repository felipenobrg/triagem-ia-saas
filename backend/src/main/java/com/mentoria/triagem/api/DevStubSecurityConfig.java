package com.mentoria.triagem.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Perfil {@code devstub}: sem Keycloak, identidade vem do header {@code X-Dev-Subject}.
 *
 * <p>Existe para testes e para rodar a aplicacao sem subir o Keycloak. <b>Nao</b> satisfaz o
 * FR-001 e nao deve ser usado fora do estudo local: qualquer pessoa pode se declarar outra.
 */
@Configuration
@Profile("devstub")
public class DevStubSecurityConfig {

  @Bean
  SecurityFilterChain devStubSecurity(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(registry -> registry.anyRequest().permitAll());
    return http.build();
  }
}
