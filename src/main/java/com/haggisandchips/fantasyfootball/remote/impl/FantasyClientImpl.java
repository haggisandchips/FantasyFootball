package com.haggisandchips.fantasyfootball.remote.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.PlayerLine;
import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Statistics;
import com.haggisandchips.fantasyfootball.domain.Team;
import com.haggisandchips.fantasyfootball.remote.FantasyClient;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class FantasyClientImpl implements FantasyClient {

  // A hand-maintained stand-in for the real my-team endpoint - see my-squad.example.json.
  private static final String MY_SQUAD_FILE = "my-squad.json";

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

  // TODO Fetch the authenticated user's actual squad, e.g. from
  // https://fantasy.premierleague.com/api/my-team/{teamId}/ once OAuth-style authentication
  // is in place. Until then, this reads my-squad.json (if present) as a manual stand-in.
  @Override
  public Squad getMySquad(final List<Player> allPlayers) throws IOException {

    final File squadFile = new File(MY_SQUAD_FILE);
    if (!squadFile.exists()) {
      log.warn("{} not found - returning an empty squad. Copy my-squad.example.json to {} to test transfer suggestions with your own team.",
          MY_SQUAD_FILE, MY_SQUAD_FILE);
      return new Squad(BigDecimal.ZERO, BigDecimal.ZERO, 0, new Team(List.of()));
    }

    final MySquadConfig config = objectMapper.readValue(squadFile, MySquadConfig.class);

    final Map<String, Player> playersByName = allPlayers.stream()
        .collect(Collectors.toMap(player -> player.getName().toLowerCase(), player -> player, (first, second) -> first));

    final List<Player> squadPlayers = new ArrayList<>();
    for (final String name : config.getPlayers()) {
      final Player player = playersByName.get(name.toLowerCase());
      if (player == null) {
        log.warn("Could not find a player named '{}' in the live player data - skipping", name);
        continue;
      }

      // The real selling price depends on your purchase history and isn't available without
      // fetching the authenticated my-team endpoint, so approximate it with the current buy price.
      player.setSellingPrice(player.getCostNow());
      squadPlayers.add(player);
    }

    final Map<Position, List<Player>> squadByPosition =
        squadPlayers.stream().collect(Collectors.groupingBy(Player::getPosition));

    final List<PlayerLine> playerLines = new ArrayList<>();
    for (final Position position : Position.values()) {
      playerLines.add(new PlayerLine(position, squadByPosition.getOrDefault(position, List.of())));
    }

    final Team myTeam = new Team(playerLines);
    final BigDecimal squadValue =
        squadPlayers.stream().map(Player::getCostNow).reduce(BigDecimal.ZERO, BigDecimal::add);

    return new Squad(squadValue, config.getMoneyAvailable(), config.getFreeTransfers(), myTeam);
  }

  @Data
  private static class MySquadConfig {

    private int freeTransfers;

    private BigDecimal moneyAvailable;

    private List<String> players;
  }
}
