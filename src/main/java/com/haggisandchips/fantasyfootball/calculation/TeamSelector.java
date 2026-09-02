package com.haggisandchips.fantasyfootball.calculation;

import com.haggisandchips.fantasyfootball.Controls;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.PlayerLine;
import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.Team;
import com.haggisandchips.fantasyfootball.util.CombinationGenerator;
import com.haggisandchips.fantasyfootball.util.CombinationGeneratorImpl;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Slf4j
public class TeamSelector {

  public static Map<Position, Map<BigDecimal, Set<PlayerLine>>> buildCombinations(
      final Strategy strategyOption, final Map<Position, List<Player>> players,
      final Map<Position, Double> minimumThresholds) {
    final Map<Position, Map<BigDecimal, Set<PlayerLine>>> combinations =
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
      if (rawCombinations > Controls.MAX_RAW_COMBINATIONS_PER_POSITION) {
        throw new IllegalStateException(String.format(
            "%s minimum threshold of %s keeps %d players, which would generate %,d combinations "
                + "(limit %,d) - raise the minimum threshold to shrink the pool.",
            position, threshold, filtered.size(), rawCombinations, Controls.MAX_RAW_COMBINATIONS_PER_POSITION));
      }

      players.put(position, filtered);
    }

    for (final Position position : players.keySet()) {

      // Bucketed by the chosen strategy's own aggregate (Strategy.lineStat) - not always total
      // points - so KillerTeamFinder.isBetter (which compares by that same strategy) evaluates
      // combinations in the order that actually matters for the strategy in play, and so
      // MAX_COMBINATIONS_PER_BUCKET's per-bucket cap (see PlayerLine.compareTo) trims by cost within
      // groups that are actually tied on the stat being optimised for, rather than tied on points
      // while potentially differing widely on form/points-per-game.
      final Map<BigDecimal, Set<PlayerLine>> playerLines = new TreeMap<>();
      combinations.put(position, playerLines);

      final CombinationGenerator<Player> generator =
          new CombinationGeneratorImpl<>(players.get(position), position.getNumber());

      while (generator.hasMore()) {
        final List<Player> tempPlayers = generator.getNext();

        final PlayerLine playerLine = new PlayerLine(position, tempPlayers);
        final BigDecimal statValue = strategyOption.getLineStat().apply(playerLine);

        final Set<PlayerLine> statLines = playerLines.computeIfAbsent(statValue, key -> new TreeSet<>());
        statLines.add(playerLine);
      }
    }

    if (log.isDebugEnabled()) {
      for (Map.Entry<Position, Map<BigDecimal, Set<PlayerLine>>> entry : combinations.entrySet()) {
        int count = 0;
        for (Set<PlayerLine> playerLines : entry.getValue().values()) {
          count += playerLines.size();
        }
        log.debug(
            String.format(
                "Found %d combinations of %ss.", count, entry.getKey().name().toLowerCase()));
      }
    }

    return combinations;
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
  // Strategy.lineStat total (see PlayerLine's constructor: sum of points/form/points-per-game
  // weighted by fixture multiplier), how many r-player subsets reach it, capped at
  // Controls.MAX_COMBINATIONS_PER_BUCKET per total (matching the cap KillerTeamFinder applies per
  // bucket). Computed via a subset-sum counting DP rather than enumerating the underlying
  // C(pool.size(), r) combinations (see buildCombinations) - a wide pool can put that count in the
  // hundreds of millions, but the DP's cost only depends on the (tiny, real stat totals are a
  // bounded range) number of distinct running sums encountered, so it stays fast regardless of pool
  // size. Lets KillerTeamTab show the live "combinations" estimate without ever materializing a
  // single PlayerLine.
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

      total *= countCappedSubsets(strategyOption, filtered, position.getNumber(), Controls.MAX_COMBINATIONS_PER_BUCKET);
    }

    return total;
  }

  // ways[k] maps a running stat-total to how many k-player subsets of the pool processed so far
  // reach it, saturated at cap + 1 (once a total has more than `cap` subsets, KillerTeamFinder only
  // ever uses `cap` of them, so there's no need to keep counting exactly). Standard 0/1-knapsack-style
  // subset counting: iterating k from r down to 1 for each player means ways[k - 1] is always read in
  // the state it was in *before* that player, so nothing gets counted against itself twice. Keys are
  // stripped of trailing zeros before use so two sums that are numerically equal but arrived via a
  // different scale (e.g. a fresh BigDecimal.ZERO vs. one built up through several adds) are always
  // treated as the same bucket, matching buildCombinations' own TreeMap (whose ordering, unlike a
  // HashMap's equals/hashCode, already treats them as equal).
  private static long countCappedSubsets(
      final Strategy strategyOption, final List<Player> pool, final int r, final int cap) {

    final List<Map<BigDecimal, Long>> ways = new ArrayList<>(r + 1);
    for (int k = 0; k <= r; k++) {
      ways.add(new HashMap<>());
    }
    ways.get(0).put(BigDecimal.ZERO, 1L);

    for (final Player player : pool) {
      final BigDecimal value = strategyOption.getWeightedPlayerStat().apply(player);

      for (int k = r; k >= 1; k--) {
        for (final Map.Entry<BigDecimal, Long> entry : ways.get(k - 1).entrySet()) {
          final BigDecimal key = entry.getKey().add(value).stripTrailingZeros();
          ways.get(k).merge(key, entry.getValue(),
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
