package com.mentoria.triagem.api;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check sem dados de negocio (NFR-001). Declara tambem o que ainda nao existe, para
 * que ninguem confunda esta fatia com o sistema completo da spec.
 */
@RestController
public class HealthController {

  @GetMapping("/api/v1/health")
  Map<String, Object> health() {
    return Map.of(
        "status",
        "UP",
        "study",
        true,
        "notImplemented",
        List.of(
            "IA real (so provedores fake deterministicos)",
            "orcamento e telemetria de custo de IA",
            "avaliacao e baseline de IA",
            "Spec 002 (prioridade, prazo, responsavel, reabertura, duplicados)",
            "frontend"));
  }
}
