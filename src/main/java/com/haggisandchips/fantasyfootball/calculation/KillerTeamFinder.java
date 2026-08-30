package com.haggisandchips.fantasyfootball.calculation;

import com.haggisandchips.fantasyfootball.Controls;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.PlayerLine;
import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.Team;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Brute-force search for the highest scoring, affordable, valid 15-man squad buildable from
// scratch - unrelated to transfers (see TransferSelector), which works from an existing squad.
@Slf4j
public final class KillerTeamFinder {

  private KillerTeamFinder() {
  }

  public static Team find(
      final Strategy strategy,
      final Map<Position, Map<Integer, Set<PlayerLine>>> permutations,
      final BigDecimal maxBudget) {

    log.debug(String.format("Calculating Killer Team by %s", strategy.name()));

    Team killerTeam = null;

    for (final Set<PlayerLine> goalkeeperLines : permutations.get(Position.GOALKEEPER).values()) {
      int gg = 0;
      for (PlayerLine goalkeeperLine : goalkeeperLines) {
        if (++gg > Controls.MAX_PERMUTATIONS_PER_SCORE) {
          break;
        }

        for (final Set<PlayerLine> defenderLines : permutations.get(Position.DEFENDER).values()) {
          int dd = 0;
          for (PlayerLine defenderLine : defenderLines) {
            if (++dd > Controls.MAX_PERMUTATIONS_PER_SCORE) {
              break;
            }

            for (final Set<PlayerLine> midfielderLines :
                permutations.get(Position.MIDFIELDER).values()) {
              int mm = 0;
              for (PlayerLine midfielderLine : midfielderLines) {
                if (++mm > Controls.MAX_PERMUTATIONS_PER_SCORE) {
                  break;
                }

                for (final Set<PlayerLine> forwardLines :
                    permutations.get(Position.FORWARD).values()) {
                  int ff = 0;
                  for (PlayerLine forwardLine : forwardLines) {
                    if (++ff > Controls.MAX_PERMUTATIONS_PER_SCORE) {
                      break;
                    }

                    List<PlayerLine> playerLines = new ArrayList<>();
                    playerLines.add(goalkeeperLine);
                    playerLines.add(defenderLine);
                    playerLines.add(midfielderLine);
                    playerLines.add(forwardLine);

                    final Team currentTeam = new Team(playerLines);
                    if (isAffordable(maxBudget, currentTeam)) {
                      if (killerTeam == null
                          || isBetter(strategy, currentTeam, killerTeam)) {
                        if (TeamSelector.isValidTeam(currentTeam)) {
                          killerTeam = currentTeam;

                          if (log.isDebugEnabled()) {
                            log.debug(String.format("New (%s based) Killer Team found: %s", strategy.name(), killerTeam));
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    return killerTeam;
  }

  private static boolean isAffordable(final BigDecimal maxBudget, final Team currentTeam) {

    BigDecimal moneyAvailable = maxBudget;

    final List<Player> players = currentTeam.getPlayers();

    for (final Player player : players) {
      moneyAvailable = moneyAvailable.subtract(player.getCostNow());
    }

    return moneyAvailable.compareTo(new BigDecimal("0")) >= 0;
  }

  private static boolean isBetter(
      final Strategy strategy, final Team currentTeam, final Team killerTeam) {

    final int comparison = strategy.compare(currentTeam, killerTeam);
    return comparison > 0
        || (comparison == 0
        && currentTeam.getCostNow().compareTo(killerTeam.getCostNow()) < 0);
  }
}
