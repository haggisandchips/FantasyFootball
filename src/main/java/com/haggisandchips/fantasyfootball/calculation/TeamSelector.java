package com.haggisandchips.fantasyfootball.calculation;

import com.haggisandchips.fantasyfootball.Controls;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.PlayerLine;
import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.Team;
import com.haggisandchips.fantasyfootball.util.PermutationGenerator;
import com.haggisandchips.fantasyfootball.util.PermutationGeneratorImpl;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Slf4j
public class TeamSelector {

  public static Map<Position, Map<Integer, Set<PlayerLine>>> buildPermutations(
      final Strategy strategyOption, final Map<Position, List<Player>> players,
      final Map<Position, Double> minimumThresholds) {
    final Map<Position, Map<Integer, Set<PlayerLine>>> permutations =
        new TreeMap<>();

    for (final Position position : players.keySet()) {
      final List<Player> pool = players.get(position);
      final double threshold = minimumThresholds.get(position);
      final List<Player> filtered = pool.stream()
          .filter(player -> strategyOption.getPlayerStat().apply(player) >= threshold)
          .toList();

      log.info("Killer Team {} pool: kept {} of {} players (minimum {} {})",
          position, filtered.size(), pool.size(), threshold, strategyOption.name());

      players.put(position, filtered);
    }

    for (final Position position : players.keySet()) {

      final Map<Integer, Set<PlayerLine>> playerLines = new TreeMap<>();
      permutations.put(position, playerLines);

      final PermutationGenerator<Player> generator =
          new PermutationGeneratorImpl<>(players.get(position), position.getNumber());

      while (generator.hasMore()) {
        final List<Player> tempPlayers = generator.getNext();

        final PlayerLine playerLine = new PlayerLine(position, tempPlayers);
        final Integer score = playerLine.getPoints();

        final Set<PlayerLine> scoreLines;
        if (playerLines.containsKey(score)) {
          scoreLines = playerLines.get(score);
        } else {
          scoreLines = new TreeSet<>();
          playerLines.put(score, scoreLines);
        }
        scoreLines.add(playerLine);
      }
    }

    if (log.isDebugEnabled()) {
      for (Map.Entry<Position, Map<Integer, Set<PlayerLine>>> entry : permutations.entrySet()) {
        int count = 0;
        for (Set<PlayerLine> playerLines : entry.getValue().values()) {
          count += playerLines.size();
        }
        log.debug(
            String.format(
                "Found %d permutations of %ss.", count, entry.getKey().name().toLowerCase()));
      }
    }

    return permutations;
  }

  public static boolean isValidTeam(final Team team) {
    final Map<String, Integer> teamCounts = new HashMap<>();

    for (final Player player : team.getPlayers()) {
      final int newCount = teamCounts.merge(player.getTeam(), 1, Integer::sum);

      if (newCount > Controls.MAXIMUM_PLAYERS_FROM_TEAM) {
        return false;
      }
    }

    return true;
  }
}
