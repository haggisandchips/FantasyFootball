package com.haggisandchips.fantasyfootball.calculation;

import com.haggisandchips.fantasyfootball.domain.Fixture;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Position;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

// Suggests who out of a 15-man squad should start (any legal FPL formation), and who should
// captain/vice-captain them - a purely advisory overlay for the My Squad pitch (see ui.PitchView),
// never mutating the squad itself. Deliberately separate from StartingElevenSelector (which picks
// KillerTeamFinder's from-scratch dream XI, ranked by cost-tiebroken score) since the ranking here
// is explicitly different - fixture difficulty, venue, form and points-per-game as successive
// tiebreakers instead of cost - and the two must stay independent so this never affects the killer
// team.
public final class OptimalElevenSelector {

  private static final int GOALKEEPERS = 1;

  private static final int MIN_DEFENDERS = 3;

  private static final int MAX_DEFENDERS = 5;

  private static final int MIN_MIDFIELDERS = 2;

  private static final int MAX_MIDFIELDERS = 5;

  private static final int MIN_FORWARDS = 1;

  private static final int MAX_FORWARDS = 3;

  private static final int OUTFIELDERS = 10;

  // Worse than any real FPL fixture difficulty (1-5) - a team with no fixture this window (a blank
  // gameweek) always loses a fixture-difficulty tiebreak against one that actually has a game.
  private static final int NO_FIXTURE_DIFFICULTY = 6;

  // Score (points) desc, then fixture difficulty asc (easier wins), then home over away, then form
  // desc, then points-per-game desc - a genuine tie after all of that is settled by coin toss (see
  // shuffleTiedGroups), not by this comparator.
  private static final Comparator<Player> RANKING = Comparator
      .comparingInt(Player::getPoints).reversed()
      .thenComparingInt(OptimalElevenSelector::fixtureDifficultyRank)
      .thenComparingInt(OptimalElevenSelector::homeRank)
      .thenComparing(Player::getForm, Comparator.reverseOrder())
      .thenComparing(Player::getPointsPerGame, Comparator.reverseOrder());

  private OptimalElevenSelector() {
  }

  public record Result(
      List<Player> startingEleven, List<Player> substitutes, Player captain, Player viceCaptain) {
  }

  private record Formation(int defenders, int midfielders, int forwards) {
  }

  public static Result select(final List<Player> squad) {

    final Random random = new Random();
    final Map<Position, List<Player>> byPosition = squad.stream().collect(Collectors.groupingBy(Player::getPosition));

    final List<Player> goalkeepers = ranked(byPosition.getOrDefault(Position.GOALKEEPER, List.of()), random);
    final List<Player> defenders = ranked(byPosition.getOrDefault(Position.DEFENDER, List.of()), random);
    final List<Player> midfielders = ranked(byPosition.getOrDefault(Position.MIDFIELDER, List.of()), random);
    final List<Player> forwards = ranked(byPosition.getOrDefault(Position.FORWARD, List.of()), random);

    final Formation formation = bestFormation(defenders, midfielders, forwards);

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

    // The captain must actually be playing, so elect from the starting XI itself (any position),
    // using the same ranking - #1 captains, #2 vice-captains.
    final List<Player> captaincyOrder = ranked(startingEleven, random);
    final Player captain = captaincyOrder.isEmpty() ? null : captaincyOrder.get(0);
    final Player viceCaptain = captaincyOrder.size() < 2 ? null : captaincyOrder.get(1);

    return new Result(startingEleven, substitutes, captain, viceCaptain);
  }

  // Tries every formation FPL allows (1 GK, 3-5 DEF, 2-5 MID, 1-3 FWD, 11 total) and keeps whichever
  // fields the highest total score - a tie (identical total across formations) keeps whichever was
  // found first, which only happens when every player-level tiebreak above was already exhausted
  // too (equal points sums can't otherwise arise once genuine ties are coin-tossed within a
  // position), so no further tiebreak is needed here.
  private static Formation bestFormation(
      final List<Player> defenders, final List<Player> midfielders, final List<Player> forwards) {

    final int[] defenderPoints = prefixPoints(defenders);
    final int[] midfielderPoints = prefixPoints(midfielders);
    final int[] forwardPoints = prefixPoints(forwards);

    final int maxDefenders = Math.min(MAX_DEFENDERS, defenders.size());
    final int maxMidfielders = Math.min(MAX_MIDFIELDERS, midfielders.size());

    Formation best = new Formation(
        Math.min(MIN_DEFENDERS, defenders.size()),
        Math.min(MIN_MIDFIELDERS, midfielders.size()),
        Math.min(MIN_FORWARDS, forwards.size()));
    int bestPoints = -1;

    for (int def = MIN_DEFENDERS; def <= maxDefenders; def++) {
      for (int mid = MIN_MIDFIELDERS; mid <= maxMidfielders; mid++) {
        final int fwd = OUTFIELDERS - def - mid;
        if (fwd < MIN_FORWARDS || fwd > MAX_FORWARDS || fwd > forwards.size()) {
          continue;
        }

        final int points = defenderPoints[def] + midfielderPoints[mid] + forwardPoints[fwd];
        if (points > bestPoints) {
          best = new Formation(def, mid, fwd);
          bestPoints = points;
        }
      }
    }

    return best;
  }

  private static List<Player> ranked(final List<Player> players, final Random random) {

    final List<Player> result = new ArrayList<>(players);
    result.sort(RANKING);
    shuffleTiedGroups(result, random);

    return result;
  }

  // Within a run of players tied on every criterion above, order is otherwise arbitrary (an
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

    return first.getPoints() == second.getPoints()
        && fixtureDifficultyRank(first) == fixtureDifficultyRank(second)
        && homeRank(first) == homeRank(second)
        && first.getForm().compareTo(second.getForm()) == 0
        && first.getPointsPerGame().compareTo(second.getPointsPerGame()) == 0;
  }

  private static int fixtureDifficultyRank(final Player player) {

    final Fixture fixture = player.getNextFixture();
    return fixture == null ? NO_FIXTURE_DIFFICULTY : fixture.getDifficulty();
  }

  private static int homeRank(final Player player) {

    final Fixture fixture = player.getNextFixture();
    return fixture != null && fixture.isHome() ? 0 : 1;
  }

  private static int[] prefixPoints(final List<Player> ranked) {

    final int[] prefix = new int[ranked.size() + 1];
    for (int i = 0; i < ranked.size(); i++) {
      prefix[i + 1] = prefix[i] + ranked.get(i).getPoints();
    }

    return prefix;
  }
}
