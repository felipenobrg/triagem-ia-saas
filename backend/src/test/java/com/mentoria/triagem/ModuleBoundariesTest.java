package com.mentoria.triagem;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** NFR-007 / NFR3-007: os limites entre modulos sao verificados automaticamente. */
class ModuleBoundariesTest {

  @Test
  void modulesRespectBoundaries() {
    ApplicationModules modules = ApplicationModules.of(TriagemApplication.class);
    modules.verify();
  }
}
