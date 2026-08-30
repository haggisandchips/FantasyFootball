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

  final int points;

  final BigDecimal form;

  final BigDecimal pointsPerGame;

  public Team(final List<PlayerLine> playerLines) {
    this.playerLines = playerLines;

    BigDecimal costNow = new BigDecimal("0"), form = new BigDecimal("0"), pointsPerGame = new BigDecimal("0");
    int score = 0;
    for (final PlayerLine playerLine : playerLines) {
      costNow = costNow.add(playerLine.getCostNow());
      score += playerLine.getPoints();
      form = form.add(playerLine.getForm());
      pointsPerGame = pointsPerGame.add(playerLine.getPointsPerGame());
    }

    this.costNow = costNow;
    this.points = score;
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

  // TODO Remove if not used once team transfers is implemented
  public String getPlayerNames() {
    final StringBuilder playerNames = new StringBuilder();
    for (final PlayerLine playerLine : getPlayerLines()) {
      for (final Player player : playerLine.getPlayers()) {
        if (!playerNames.isEmpty()) {
          playerNames.append(", ");
        }
        playerNames.append(player.getName()).append(" (").append(player.getTeam()).append(")");
      }
    }

    return playerNames.toString();
  }

  // TODO Remove if not used once team transfers is implemented
  public Team makeSubstitutions(final Map<Player, Player> substitutions) {
    final List<PlayerLine> newPlayerLines = new ArrayList<>();
    for (final PlayerLine playerLine : playerLines) {
      final List<Player> newPlayers = new ArrayList<>();
      for (final Player player : playerLine.players) {
        newPlayers.add(substitutions.getOrDefault(player, player));
      }

      newPlayerLines.add(new PlayerLine(playerLine.getPosition(), newPlayers));
    }

    return new Team(newPlayerLines);
  }

  public int compareTo(final Team other) {
    return this.getCostNow().compareTo(other.getCostNow());
  }

  public String toString() {
    final StringBuilder builder = new StringBuilder(String.format("Team (Points=%d, Form=%.1f, Points per game=%.1f, Cost Now=%.1f, Team Average=%d) [\n", getPoints(), getForm(), getPointsPerGame(), getCostNow(), getPoints() * 11 / 15));

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
