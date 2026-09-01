package com.haggisandchips.fantasyfootball.auth;

import java.util.Optional;

// Persists the pasted FPL bearer token across launches, so logging in (see the ui package's
// FplLoginDialog) only has to happen once until the user explicitly logs out.
public interface TokenStore {

  Optional<String> load();

  void save(String token);

  void clear();
}
