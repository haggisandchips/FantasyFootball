package com.haggisandchips.fantasyfootball.auth;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

// Single in-memory source of truth for the current FPL bearer token, backed by TokenStore for
// persistence across launches. Populated by the paste dialog (see ui.FplLoginDialog) and
// initialised at startup from whatever TokenStore last had saved. Always registered (not gated
// behind live/stub mode) so the desktop UI's Account menu has something to log in/out against.
@Component
@RequiredArgsConstructor
public class FplTokenHolder {

  private final TokenStore tokenStore;

  private final AtomicReference<String> token = new AtomicReference<>();

  @PostConstruct
  void init() {

    token.set(tokenStore.load().orElse(null));
  }

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
