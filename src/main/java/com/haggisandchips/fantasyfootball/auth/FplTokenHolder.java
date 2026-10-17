package com.haggisandchips.fantasyfootball.auth;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

// Single in-memory source of truth for the current FPL bearer token. Populated by the paste
// dialog (see ui.FplLoginDialog) and deliberately never persisted, so every launch starts logged
// out. Always registered (not gated behind live/stub mode) so the desktop UI's Account menu has
// something to log in/out against.
@Component
public class FplTokenHolder {

  private final AtomicReference<String> token = new AtomicReference<>();

  public Optional<String> get() {

    return Optional.ofNullable(token.get());
  }

  public void set(final String value) {

    token.set(value);
  }

  public void clear() {

    token.set(null);
  }
}
