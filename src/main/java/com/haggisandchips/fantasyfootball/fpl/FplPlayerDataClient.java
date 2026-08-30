package com.haggisandchips.fantasyfootball.fpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.http.HttpGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FplPlayerDataClient implements PlayerDataClient {

  private static final URI BOOTSTRAP_STATIC = URI.create("https://fantasy.premierleague.com/api/bootstrap-static/");

  private final HttpGateway httpGateway;

  private final ObjectMapper objectMapper;

  @Override
  public List<Player> getAllPlayers() throws IOException, InterruptedException {

    final String body = httpGateway.get(BOOTSTRAP_STATIC);

    return objectMapper.readValue(body, Statistics.class).getPlayers();
  }
}
