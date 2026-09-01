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

import java.util.ArrayList;
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

      final long rawCombinations = combinationCount(filtered.size(), position.getNumber());
      if (rawCombinations > Controls.MAX_RAW_PERMUTATIONS_PER_POSITION) {
        throw new IllegalStateException(String.format(
            "%s minimum threshold of %s keeps %d players, which would generate %,d combinations "
                + "(limit %,d) - raise the minimum threshold to shrink the pool.",
            position, threshold, filtered.size(), rawCombinations, Controls.MAX_RAW_PERMUTATIONS_PER_POSITION));
      }

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

  // C(n, r), computed via the standard incremental multiply-then-divide-by-i order, which keeps
  // every partial product an exact integer (each is itself a valid binomial coefficient) - safe from
  // both overflow and rounding error for the small r (2/3/5) and modest n (low thousands at most)
  // that real player pools produce.
  private static long combinationCount(final int n, final int r) {

    if (r > n || r < 0) {
      return 0;
    }

    long result = 1;
    for (int i = 1; i <= r; i++) {
      result = result * (n - r + i) / i;
    }

    return result;
  }

  // Exactly the number KillerTeamFinder.find() will evaluate for this position - for each distinct
  // points-total (see PlayerLine's constructor: sum of player.getPoints() * fixture multiplier), how
  // many r-player subsets reach it, capped at Controls.MAX_PERMUTATIONS_PER_SCORE per total (matching
  // the cap KillerTeamFinder applies per score bucket). Computed via a subset-sum counting DP rather
  // than enumerating the underlying C(pool.size(), r) combinations (see buildPermutations) - a wide
  // pool can put that count in the hundreds of millions, but the DP's cost only depends on the (tiny,
  // real point totals are a bounded range) number of distinct running sums encountered, so it stays
  // fast regardless of pool size. Lets KillerTeamTab show the live "combinations" estimate without
  // ever materializing a single PlayerLine.
  public static long countCappedCombinations(
      final Strategy strategyOption, final Map<Position, List<Player>> players,
      final Map<Position, Double> minimumThresholds) {

    long total = 1;
    for (final Position position : Position.values()) {
      final List<Player> pool = players.getOrDefault(position, List.of());
      final double threshold = minimumThresholds.get(position);
      final List<Player> filtered = pool.stream()
          .filter(player -> strategyOption.getPlayerStat().apply(player) >= threshold)
          .toList();

      total *= countCappedSubsets(filtered, position.getNumber(), Controls.MAX_PERMUTATIONS_PER_SCORE);
    }

    return total;
  }

  // ways[k] maps a running points-total to how many k-player subsets of the pool processed so far
  // reach it, saturated at cap + 1 (once a total has more than `cap` subsets, KillerTeamFinder only
  // ever uses `cap` of them, so there's no need to keep counting exactly). Standard 0/1-knapsack-style
  // subset counting: iterating k from r down to 1 for each player means ways[k - 1] is always read in
  // the state it was in *before* that player, so nothing gets counted against itself twice.
  private static long countCappedSubsets(final List<Player> pool, final int r, final int cap) {

    final List<Map<Integer, Long>> ways = new ArrayList<>(r + 1);
    for (int k = 0; k <= r; k++) {
      ways.add(new HashMap<>());
    }
    ways.get(0).put(0, 1L);

    for (final Player player : pool) {
      final int value = player.getPoints() * Controls.getFixtureMultiplier(player.getTeam());

      for (int k = r; k >= 1; k--) {
        for (final Map.Entry<Integer, Long> entry : ways.get(k - 1).entrySet()) {
          ways.get(k).merge(entry.getKey() + value, entry.getValue(),
              (existing, added) -> Math.min(cap + 1L, existing + added));
        }
      }
    }

    long total = 0;
    for (final long count : ways.get(r).values()) {
      total += Math.min(cap, count);
    }

    return total;
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
