package com.haggisandchips.fantasyfootball.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
public class PlayerLine implements Comparable<PlayerLine> {

  final List<Player> players;
  final BigDecimal costNow;
  final double points;
  final BigDecimal form;
  final BigDecimal pointsPerGame;
  private final Position position;

  // considerFixtures governs squad-building's whole relationship with fixtures (see Team's own
  // field of the same name, which this is always built consistently with) - false means points/
  // form/points-per-game are exactly the player's own season-to-date values, fixtures ignored
  // entirely; true multiplies each by Player.getFixtureDifficultyMultiplier() (fixture count *and*
  // difficulty, the same signal Starting/OptimalElevenSelector use for picking who starts). A plain
  // double (not int) even in the false case, since the flag is a runtime choice, not a compile-time
  // one - the multiplier is 1.0 exactly when fixtures aren't considered, so points still lands on a
  // whole number then, just typed the same way either way.
  public PlayerLine(final Position position, final List<Player> players, final boolean considerFixtures) {
    this.position = position;
    this.players = players;

    BigDecimal costNow = new BigDecimal("0"), form = new BigDecimal("0"), pointsPerGame = new BigDecimal("0");
    double points = 0;
    for (final Player player : players) {

      final double multiplier = considerFixtures ? player.getFixtureDifficultyMultiplier() : 1.0;

      costNow = costNow.add(player.getCostNow());
      points += player.getPoints() * multiplier;
      form = form.add(player.getForm().multiply(BigDecimal.valueOf(multiplier)));
      pointsPerGame = pointsPerGame.add(player.getPointsPerGame().multiply(BigDecimal.valueOf(multiplier)));
    }

    this.costNow = costNow;
    this.points = points;
    this.form = form;
    this.pointsPerGame = pointsPerGame;
  }

  // Ordered primarily by cost ascending - KillerTeamFinder relies on that to take the cheapest few
  // lines per Strategy.lineStat bucket. Cost alone isn't enough to make this a valid ordering for a TreeSet
  // though: TreeSet uses compareTo (not equals/hashCode) to decide uniqueness, so two genuinely
  // different player combinations that happen to sum to the same cost would otherwise compare equal
  // and silently collapse into a single entry, dropping a legitimately distinct candidate team from
  // consideration. Breaking the tie by player id (sorted, so list order doesn't matter) guarantees two
  // combinations only ever compare equal when they really are the same set of players.
  public int compareTo(final PlayerLine other) {

    final int costComparison = this.getCostNow().compareTo(other.getCostNow());
    if (costComparison != 0) {
      return costComparison;
    }

    final List<Integer> theseIds = players.stream().map(Player::getFantasyId).sorted().toList();
    final List<Integer> otherIds = other.players.stream().map(Player::getFantasyId).sorted().toList();

    for (int i = 0; i < theseIds.size(); i++) {
      final int idComparison = theseIds.get(i).compareTo(otherIds.get(i));
      if (idComparison != 0) {
        return idComparison;
      }
    }

    return 0;
  }
}
