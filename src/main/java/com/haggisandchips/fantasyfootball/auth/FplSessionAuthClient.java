package com.haggisandchips.fantasyfootball.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// Replays a bearer token captured from a real, already-logged-in browser rather than performing a
// login itself - Premier League's actual login page is a JS-driven identity provider that a plain
// HttpClient can't drive. Confirmed the X-Api-Authorization header alone is sufficient (no session
// cookie needed). Automating capture of this token (e.g. via an embedded browser view) is a
// planned follow-up once there's a desktop UI to host it.
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "fpl.auth", name = "enabled", havingValue = "true")
public class FplSessionAuthClient implements FplAuthClient {

  private static final Duration TIMEOUT = Duration.ofSeconds(10L);

  private final FplApiAuthConfig apiAuthConfig;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Override
  public String authenticatedGet(final URI uri) throws IOException, InterruptedException {

    final String authorization = apiAuthConfig.getAuthorization();
    if (authorization == null || authorization.isBlank()) {
      throw new IOException(
          "FPL_AUTH_ENABLED is true but FPL_API_AUTHORIZATION is not set - log in to fantasy.premierleague.com "
              + "in your browser, find a request to fantasy.premierleague.com/api/me/ in dev tools, and copy "
              + "its X-Api-Authorization request header value into FPL_API_AUTHORIZATION");
    }

    final HttpRequest request = HttpRequest.newBuilder(uri)
        .timeout(TIMEOUT)
        .header("X-Api-Authorization", authorization)
        .GET()
        .build();

    final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      throw new IOException(String.format(
          "Authenticated request to %s failed with status %d - the captured token has likely expired; "
              + "capture a fresh FPL_API_AUTHORIZATION from your browser", uri, response.statusCode()));
    }

    return response.body();
  }
}
