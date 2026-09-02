package com.haggisandchips.fantasyfootball.calculation;

import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Position;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

// Picks a legal starting XI (and the resulting bench) out of a 15-man squad that otherwise has no
// such split - e.g. KillerTeamFinder's from-scratch dream team. For now this is deliberately
// simple: among the formations FPL allows (1 GK, 3-5 DEF, 2-5 MID, 1-3 FWD, 11 total), pick
// whichever fields the highest-points players; ties go to the cheaper eleven, and a genuine tie
// (same points, same cost) is settled at random. More nuanced picks (bench order, auto-subs,
// captaincy) are a planned follow-up.
public final class StartingElevenSelector {

  private static final int GOALKEEPERS = 1;

  private static final int MIN_DEFENDERS = 3;

  private static final int MAX_DEFENDERS = 5;

  private static final int MIN_MIDFIELDERS = 2;

  private static final int MAX_MIDFIELDERS = 5;

  private static final int MIN_FORWARDS = 1;

  private static final int MAX_FORWARDS = 3;

  private static final int OUTFIELDERS = 10;

  private StartingElevenSelector() {
  }

  public record Result(List<Player> startingEleven, List<Player> substitutes) {
  }

  private record Formation(int defenders, int midfielders, int forwards) {
  }

  public static Result select(final List<Player> squad) {

    final Map<Position, List<Player>> byPosition = squad.stream().collect(Collectors.groupingBy(Player::getPosition));

    final Random random = new Random();
    final List<Player> goalkeepers = rankedByPoints(byPosition.getOrDefault(Position.GOALKEEPER, List.of()), random);
    final List<Player> defenders = rankedByPoints(byPosition.getOrDefault(Position.DEFENDER, List.of()), random);
    final List<Player> midfielders = rankedByPoints(byPosition.getOrDefault(Position.MIDFIELDER, List.of()), random);
    final List<Player> forwards = rankedByPoints(byPosition.getOrDefault(Position.FORWARD, List.of()), random);

    final Formation formation = bestFormation(defenders, midfielders, forwards, random);

    final int startingGoalkeepers = Math.min(GOALKEEPERS, goalkeepers.size());
    final int startingDefenders = Math.min(formation.defenders(), defenders.size());
    final int startingMidfielders = Math.min(formation.midfielders(), midfielders.size());
    final int startingForwards = Math.min(formation.forwards(), forwards.size());

    final List<Player> startingEleven = new ArrayList<>();
    startingEleven.addAll(goalkeepers.subList(0, startingGoalkeepers));
    startingEleven.addAll(defenders.subList(0, startingDefenders));
    startingEleven.addAll(midfielders.subList(0, startingMidfielders));
    startingEleven.addAll(forwards.subList(0, startingForwards));

    final List<Player> substitutes = new ArrayList<>();
    substitutes.addAll(goalkeepers.subList(startingGoalkeepers, goalkeepers.size()));
    substitutes.addAll(defenders.subList(startingDefenders, defenders.size()));
    substitutes.addAll(midfielders.subList(startingMidfielders, midfielders.size()));
    substitutes.addAll(forwards.subList(startingForwards, forwards.size()));

    return new Result(startingEleven, substitutes);
  }

  private static Formation bestFormation(
      final List<Player> defenders, final List<Player> midfielders, final List<Player> forwards,
      final Random random) {

    final double[] defenderPoints = prefixPoints(defenders);
    final double[] midfielderPoints = prefixPoints(midfielders);
    final double[] forwardPoints = prefixPoints(forwards);

    final BigDecimal[] defenderCost = prefixCost(defenders);
    final BigDecimal[] midfielderCost = prefixCost(midfielders);
    final BigDecimal[] forwardCost = prefixCost(forwards);

    final int maxDefenders = Math.min(MAX_DEFENDERS, defenders.size());
    final int maxMidfielders = Math.min(MAX_MIDFIELDERS, midfielders.size());

    Formation best = new Formation(
        Math.min(MIN_DEFENDERS, defenders.size()),
        Math.min(MIN_MIDFIELDERS, midfielders.size()),
        Math.min(MIN_FORWARDS, forwards.size()));
    double bestPoints = -1;
    BigDecimal bestCost = null;

    for (int def = MIN_DEFENDERS; def <= maxDefenders; def++) {
      for (int mid = MIN_MIDFIELDERS; mid <= maxMidfielders; mid++) {
        final int fwd = OUTFIELDERS - def - mid;
        if (fwd < MIN_FORWARDS || fwd > MAX_FORWARDS || fwd > forwards.size()) {
          continue;
        }

        final double points = defenderPoints[def] + midfielderPoints[mid] + forwardPoints[fwd];
        final BigDecimal cost = defenderCost[def].add(midfielderCost[mid]).add(forwardCost[fwd]);

        final boolean better;
        if (bestCost == null) {
          better = true;
        } else if (points != bestPoints) {
          better = points > bestPoints;
        } else {
          final int costCompare = cost.compareTo(bestCost);
          better = costCompare < 0 || (costCompare == 0 && random.nextBoolean());
        }

        if (better) {
          best = new Formation(def, mid, fwd);
          bestPoints = points;
          bestCost = cost;
        }
      }
    }

    return best;
  }

  // Season points scaled by this player's fixture-difficulty multiplier (see
  // Player.getFixtureDifficultyMultiplier - 1.0 per fixture when it matches this player's own
  // historical average difficulty, nudged up/down for an easier/harder-than-usual one, clamped to a
  // gentle range) - so a formation search here favours a favourable upcoming fixture over an
  // unfavourable one, on top of the double/blank-gameweek effect the multiplier already carries.
  private static double effectivePoints(final Player player) {

    return player.getPoints() * player.getFixtureDifficultyMultiplier();
  }

  private static List<Player> rankedByPoints(final List<Player> players, final Random random) {

    final List<Player> ranked = new ArrayList<>(players);
    ranked.sort(Comparator.comparingDouble(StartingElevenSelector::effectivePoints).reversed()
        .thenComparing(Player::getCostNow));
    shuffleTiedGroups(ranked, random);

    return ranked;
  }

  // Within a run of players tied on both points and cost, order is otherwise arbitrary (an
  // artifact of however they arrived in the list) - shuffling makes that a genuine coin toss
  // instead of silently favouring whichever came first.
  private static void shuffleTiedGroups(final List<Player> ranked, final Random random) {

    int start = 0;
    while (start < ranked.size()) {
      int end = start + 1;
      while (end < ranked.size() && isTied(ranked.get(start), ranked.get(end))) {
        end++;
      }
      if (end - start > 1) {
        Collections.shuffle(ranked.subList(start, end), random);
      }
      start = end;
    }
  }

  private static boolean isTied(final Player first, final Player second) {

    return effectivePoints(first) == effectivePoints(second)
        && first.getCostNow().compareTo(second.getCostNow()) == 0;
  }

  private static double[] prefixPoints(final List<Player> ranked) {

    final double[] prefix = new double[ranked.size() + 1];
    for (int i = 0; i < ranked.size(); i++) {
      prefix[i + 1] = prefix[i] + effectivePoints(ranked.get(i));
    }

    return prefix;
  }

  private static BigDecimal[] prefixCost(final List<Player> ranked) {

    final BigDecimal[] prefix = new BigDecimal[ranked.size() + 1];
    prefix[0] = BigDecimal.ZERO;
    for (int i = 0; i < ranked.size(); i++) {
      prefix[i + 1] = prefix[i].add(ranked.get(i).getCostNow());
    }

    return prefix;
  }
}
