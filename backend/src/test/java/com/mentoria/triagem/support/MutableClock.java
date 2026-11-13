package com.mentoria.triagem.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Relogio controlado pelo teste: tempo nunca depende da maquina (NFR2-002). */
public class MutableClock extends Clock {

  private Instant instant = Instant.parse("2026-11-09T12:00:00Z");

  @Override
  public ZoneId getZone() {
    return ZoneId.of("UTC");
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public Instant instant() {
    return instant;
  }

  public void advance(Duration duration) {
    instant = instant.plus(duration);
  }

  public void set(Instant value) {
    instant = value;
  }
}
