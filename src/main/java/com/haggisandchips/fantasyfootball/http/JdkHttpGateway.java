package com.haggisandchips.fantasyfootball.http;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;

@Component
public class JdkHttpGateway implements HttpGateway {

  private static final Duration TIMEOUT = Duration.ofSeconds(5L);

  private final HttpClient httpClient = BrowserRequests.newClient();

  @Override
  public String get(final URI uri) throws IOException, InterruptedException {

    final HttpRequest request = BrowserRequests.builder(uri, false).timeout(TIMEOUT).GET().build();

    return BrowserRequests.send(httpClient, request).body();
  }
}
