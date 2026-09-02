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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Manual stand-in for AuthenticatedSquadProvider - reads a hand-maintained squad file (see
// my-squad.example.json) so transfer suggestions can be tried out without an FPL login. Active
// whenever FPL_MY_SQUAD_FILE is set, naming the file to read (see LiveFplCondition, the exact
// inverse of this class's own condition, for the live-mode side).
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "fpl", name = "my-squad-file")
public class FileSquadProvider implements SquadProvider {

  private final ObjectMapper objectMapper;

  private final Environment environment;

  @Override
  public Squad getMySquad(final List<Player> allPlayers) throws IOException {

    final String squadFilePath = environment.getRequiredProperty("fpl.my-squad-file");
    final File squadFile = new File(squadFilePath);
    if (!squadFile.exists()) {
      log.warn("{} not found - returning an empty squad. Copy my-squad.example.json to {} to test transfer suggestions with your own team.",
          squadFilePath, squadFilePath);
      return new Squad(BigDecimal.ZERO, BigDecimal.ZERO, 0, false, new Team(List.of(), false), List.of(), List.of(), null, null, null, null, null);
    }

    final MySquadConfig config = objectMapper.readValue(squadFile, MySquadConfig.class);

    final Map<String, Player> playersByName = allPlayers.stream()
        .collect(Collectors.toMap(player -> player.getName().toLowerCase(), player -> player, (first, second) -> first));

    final List<Player> startingEleven = resolvePlayers(config.getStartingEleven(), playersByName);
    final List<Player> substitutes = resolvePlayers(config.getSubstitutes(), playersByName);

    final List<Player> squadPlayers = new ArrayList<>();
    squadPlayers.addAll(startingEleven);
    squadPlayers.addAll(substitutes);

    final Map<Position, List<Player>> squadByPosition =
        squadPlayers.stream().collect(Collectors.groupingBy(Player::getPosition));

    // Always built fixture-unaware - see AuthenticatedSquadProvider's own comment on this.
    final List<PlayerLine> playerLines = new ArrayList<>();
    for (final Position position : Position.values()) {
      playerLines.add(new PlayerLine(position, squadByPosition.getOrDefault(position, List.of()), false));
    }

    final Team myTeam = new Team(playerLines, false);
    final BigDecimal squadValue =
        squadPlayers.stream().map(Player::getCostNow).reduce(BigDecimal.ZERO, BigDecimal::add);

    return new Squad(
        squadValue, config.getMoneyAvailable(), config.getFreeTransfers(), false, myTeam,
        startingEleven, substitutes, null, null, config.getOverallPoints(), config.getTeamName(), null);
  }

  private List<Player> resolvePlayers(final List<String> names, final Map<String, Player> playersByName) {

    final List<Player> players = new ArrayList<>();
    for (final String name : names) {
      final Player player = playersByName.get(name.toLowerCase());
      if (player == null) {
        log.warn("Could not find a player named '{}' in the live player data - skipping", name);
        continue;
      }

      // The real selling price depends on your purchase history and isn't available without
      // fetching the authenticated my-team endpoint, so approximate it with the current buy price.
      player.setSellingPrice(player.getCostNow());
      players.add(player);
    }

    return players;
  }

  @Data
  private static class MySquadConfig {

    private int freeTransfers;

    private BigDecimal moneyAvailable;

    private List<String> startingEleven;

    private List<String> substitutes;

    private Integer overallPoints;

    private String teamName;
  }
}
