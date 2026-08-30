package com.haggisandchips.fantasyfootball.remote.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Statistics;
import com.haggisandchips.fantasyfootball.remote.FantasyClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FantasyClientImpl implements FantasyClient {

  private final ObjectMapper objectMapper;

  @Override
  public List<Player> getAllPlayers() throws IOException, URISyntaxException, InterruptedException {

    final HttpRequest request = HttpRequest.newBuilder(new URI("https://fantasy.premierleague.com/api/bootstrap-static/"))
        .timeout(Duration.ofSeconds(5L))
        .GET().build();

    final HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    final Statistics statistics = objectMapper.readValue(response.body(), Statistics.class);

    return statistics.getPlayers();
  }
}
