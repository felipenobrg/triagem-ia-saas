package com.mentoria.triagem;

import com.mentoria.triagem.conversations.ConversationLimits;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ConversationLimits.class)
public class AppConfiguration {

  /** Relogio injetavel: testes de tempo nao dependem do relogio da maquina. */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
