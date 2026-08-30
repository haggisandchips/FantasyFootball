package com.haggisandchips.fantasyfootball.squad;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.PlayerLine;
import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Team;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Stand-in for authenticated squad fetching (see SquadProvider) - reads a hand-maintained
// my-squad.json (see my-squad.example.json) until OAuth-style login is in place.
@Component
@RequiredArgsConstructor
@Slf4j
public class FileSquadProvider implements SquadProvider {

  private static final String MY_SQUAD_FILE = "my-squad.json";

  private final ObjectMapper objectMapper;

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
