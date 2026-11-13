package com.mentoria.triagem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Material de estudo da mentoria. Monolito modular: cada subpacote direto deste
 * pacote e um modulo Spring Modulith, e o teste de arquitetura verifica os limites.
 */
@Modulithic
@SpringBootApplication
public class TriagemApplication {

  public static void main(String[] args) {
    SpringApplication.run(TriagemApplication.class, args);
  }
}
