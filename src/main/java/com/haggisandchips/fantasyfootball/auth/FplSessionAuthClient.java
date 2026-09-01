package com.haggisandchips.fantasyfootball.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// Replays a bearer token pasted in from a real, already-logged-in browser rather than performing a
// login itself - Premier League's actual login page is a JS-driven identity provider that a plain
// HttpClient can't drive. Confirmed the X-Api-Authorization header alone is sufficient (no session
// cookie needed). The token itself comes from FplTokenHolder, populated by the paste dialog (see
// ui.FplLoginDialog).
@Component
@RequiredArgsConstructor
@Conditional(LiveFplCondition.class)
public class FplSessionAuthClient implements FplAuthClient {

  private static final Duration TIMEOUT = Duration.ofSeconds(10L);

  private final FplTokenHolder tokenHolder;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Override
  public String authenticatedGet(final URI uri) throws IOException, InterruptedException {

    final HttpRequest request = authenticatedRequestBuilder(uri).GET().build();
    return send(request);
  }

  @Override
  public String authenticatedPost(final URI uri, final String jsonBody) throws IOException, InterruptedException {

    final HttpRequest request = authenticatedRequestBuilder(uri)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
        .build();
    return send(request);
  }

  private HttpRequest.Builder authenticatedRequestBuilder(final URI uri) throws IOException {

    final String authorization = tokenHolder.get().orElse(null);
    if (authorization == null || authorization.isBlank()) {
      throw new IOException("Not logged in to FPL - use Account -> Log in to FPL... in the app to authenticate");
    }

    return HttpRequest.newBuilder(uri)
        .timeout(TIMEOUT)
        .header("X-Api-Authorization", authorization);
  }

  private String send(final HttpRequest request) throws IOException, InterruptedException {

    final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      // Body included (not just the status) because a rejected mutating request - e.g. a transfer
      // FPL considers invalid - explains why in its response body, not just the status code.
      final String expiryHint = response.statusCode() == 401 || response.statusCode() == 403
          ? " - your FPL session has likely expired; log out and back in via Account -> Log in to FPL..."
          : "";
      throw new IOException(String.format(
          "Authenticated request to %s failed with status %d: %s%s",
          request.uri(), response.statusCode(), response.body(), expiryHint));
    }

    return response.body();
  }
}
