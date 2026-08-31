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

  // How often to log search progress, as a fraction of the upfront combination count.
  private static final int PROGRESS_LOG_STEPS = 10;

  private KillerTeamFinder() {
  }

  public static Team find(
      final Strategy strategy,
      final Map<Position, Map<Integer, Set<PlayerLine>>> permutations,
      final BigDecimal maxBudget,
      final KillerTeamSearchProgressListener progressListener) {

    log.debug(String.format("Calculating Killer Team by %s", strategy.name()));

    final long totalCombinations = countCandidateTeams(permutations);
    log.info("Calculating killer team ({}) - {} candidate combination(s) to evaluate", strategy, totalCombinations);
    progressListener.onProgress(strategy, 0, totalCombinations);

    // The lesser of a fixed cap and 10% of the total - see TransferSelector for why.
    final long progressStep = Math.max(
        1, Math.min(Controls.MAX_KILLER_TEAM_PROGRESS_LOG_STEP, totalCombinations / PROGRESS_LOG_STEPS));
    long evaluated = 0;
    long nextLogAt = progressStep;

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

                    evaluated++;
                    if (evaluated >= nextLogAt) {
                      log.info("Progress: {}/{} candidate combinations evaluated ({}%)",
                          evaluated, totalCombinations, Math.min(100, evaluated * 100 / totalCombinations));
                      progressListener.onProgress(strategy, evaluated, totalCombinations);
                      nextLogAt += progressStep;
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

    progressListener.onProgress(strategy, evaluated, totalCombinations);
    return killerTeam;
  }

  // Upper bound on how many candidate teams find() will build: the product, across the four
  // positions, of how many player-lines that position contributes - each position's own count is
  // summed across its score buckets, each bucket capped at MAX_PERMUTATIONS_PER_SCORE exactly like
  // the nested loops above, so this matches the real (capped) iteration count precisely.
  private static long countCandidateTeams(final Map<Position, Map<Integer, Set<PlayerLine>>> permutations) {

    long total = 1;
    for (final Position position : Position.values()) {
      long positionCount = 0;
      for (final Set<PlayerLine> lines : permutations.get(position).values()) {
        positionCount += Math.min(lines.size(), Controls.MAX_PERMUTATIONS_PER_SCORE);
      }
      total *= positionCount;
    }

    return total;
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
