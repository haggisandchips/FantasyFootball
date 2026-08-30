package com.haggisandchips.fantasyfootball.http;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class JdkHttpGateway implements HttpGateway {

  private static final Duration TIMEOUT = Duration.ofSeconds(5L);

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Override
  public String get(final URI uri) throws IOException, InterruptedException {

    final HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();

    return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
  }
}
