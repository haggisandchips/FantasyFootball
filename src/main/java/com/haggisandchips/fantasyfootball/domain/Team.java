package com.haggisandchips.fantasyfootball.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
public class Team implements Comparable<Team> {

  final List<PlayerLine> playerLines;

  final BigDecimal costNow;

  final double points;

  final BigDecimal form;

  final BigDecimal pointsPerGame;

  // Whether this team's own PlayerLines were built weighting by fixture difficulty (see PlayerLine's
  // own field of the same name) - stored so makeSubstitutions can keep rebuilding new PlayerLines
  // consistently with however this team itself was built, without needing the flag threaded through
  // every call site that substitutes players in and out (see TransferSelector).
  final boolean considerFixtures;

  public Team(final List<PlayerLine> playerLines, final boolean considerFixtures) {
    this.playerLines = playerLines;
    this.considerFixtures = considerFixtures;

    BigDecimal costNow = new BigDecimal("0"), form = new BigDecimal("0"), pointsPerGame = new BigDecimal("0");
    double points = 0;
    for (final PlayerLine playerLine : playerLines) {
      costNow = costNow.add(playerLine.getCostNow());
      points += playerLine.getPoints();
      form = form.add(playerLine.getForm());
      pointsPerGame = pointsPerGame.add(playerLine.getPointsPerGame());
    }

    this.costNow = costNow;
    this.points = points;
    this.form = form;
    this.pointsPerGame = pointsPerGame;
  }

  public List<Player> getPlayers() {
    final List<Player> players = new ArrayList<>();
    for (final PlayerLine playerLine : getPlayerLines()) {
      players.addAll(playerLine.getPlayers());
    }

    return players;
  }

  public Team makeSubstitutions(final Map<Player, Player> substitutions) {
    final List<PlayerLine> newPlayerLines = new ArrayList<>();
    for (final PlayerLine playerLine : playerLines) {
      final List<Player> newPlayers = new ArrayList<>();
      for (final Player player : playerLine.players) {
        newPlayers.add(substitutions.getOrDefault(player, player));
      }

      newPlayerLines.add(new PlayerLine(playerLine.getPosition(), newPlayers, considerFixtures));
    }

    return new Team(newPlayerLines, considerFixtures);
  }

  // Rebuilds this team's PlayerLines from scratch under a different considerFixtures setting -
  // SquadProvider implementations always load a squad's baseline Team fixture-unaware (so the My
  // Squad tab's own totals never move just because a Transfers/Killer Team toggle is flipped
  // elsewhere), so this is how TransferSelector gets a fixture-aware (or -unaware) view of that same
  // squad to compare candidates against, on demand, per whatever the toggle currently says.
  public Team withFixtureConsideration(final boolean considerFixtures) {
    final List<PlayerLine> rebuilt = new ArrayList<>();
    for (final PlayerLine playerLine : playerLines) {
      rebuilt.add(new PlayerLine(playerLine.getPosition(), playerLine.getPlayers(), considerFixtures));
    }

    return new Team(rebuilt, considerFixtures);
  }

  public int compareTo(final Team other) {
    return this.getCostNow().compareTo(other.getCostNow());
  }

  public String toString() {
    final StringBuilder builder = new StringBuilder(String.format("Team (Points=%.1f, Form=%.1f, Points per game=%.1f, Cost Now=%.1f, Team Average=%.1f) [\n", getPoints(), getForm(), getPointsPerGame(), getCostNow(), getPoints() * 11 / 15));

    boolean firstLine = true;
    for (PlayerLine playerLine : playerLines) {
      if (!firstLine) {
        builder.append("\n   ----------\n");
      }
      firstLine = false;

      boolean first = true;
      for (final Player player : playerLine.getPlayers()) {
        if (!first) {
          builder.append("\n");
        }
        first = false;

        builder.append(String.format("   %s @ %s (Points: %d, Form: %.1f, Points per game: %.1f)", player.getName(), player.getTeam(), player.getPoints(), player.getForm(), player.getPointsPerGame()));
      }
    }

    builder.append("]");

    return builder.toString();
  }
}
