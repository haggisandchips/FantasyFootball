package com.haggisandchips.fantasyfootball.helpers;

import com.haggisandchips.fantasyfootball.Controls;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.PlayerLine;
import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.enums.Strategy;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Slf4j
public class TeamSelector {

  public static Map<Position, Map<Integer, Set<PlayerLine>>> buildPermutations(
      final Strategy strategyOption, final Map<Position, List<Player>> players) {
    final Map<Position, Map<Integer, Set<PlayerLine>>> permutations =
        new TreeMap<>();

    // Remove all players below the minimum threshold
    for (final List<Player> playersByPosition : players.values()) {
      for (Iterator<Player> iter = playersByPosition.iterator(); iter.hasNext(); ) {
        final Player player = iter.next();

        // TODO Use strategy to handle this
        switch (strategyOption) {
          case SCORE:
            if (player.getPoints() < Controls.MINIMUM_SCORE_THRESHOLD.get(player.getPosition())) {
              iter.remove();

              log.debug(
                  "Removed player {} with score {}", player.getName(), player.getPoints());
            }

            break;

          case POINTS_PER_GAME:
            final double pointsPerGame = player.getPointsPerGame().doubleValue();
            if (pointsPerGame < Controls.MINIMUM_POINTS_PER_GAME_THRESHOLD.get(player.getPosition())) {
              iter.remove();

              log.debug("Removed player {} with pointsPerGame {}", player.getName(), pointsPerGame);
            }

            break;

          case FORM:
            final double form = player.getForm().doubleValue();
            if (form < Controls.MINIMUM_FORM_THRESHOLD.get(player.getPosition())) {
              iter.remove();

              log.debug("Removed player {} with form {}", player.getName(), form);
            }

            break;

          default:
            break;
        }
      }
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
}
