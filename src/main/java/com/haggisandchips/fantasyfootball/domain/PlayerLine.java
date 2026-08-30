package com.haggisandchips.fantasyfootball.domain;

import com.haggisandchips.fantasyfootball.Controls;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
public class PlayerLine implements Comparable<PlayerLine> {

  final List<Player> players;
  final BigDecimal costNow;
  final int points;
  final BigDecimal form;
  final BigDecimal pointsPerGame;
  private final Position position;

  public PlayerLine(final Position position, final List<Player> players) {
    this.position = position;
    this.players = players;

    BigDecimal costNow = new BigDecimal("0"), form = new BigDecimal("0"), pointsPerGame = new BigDecimal("0");
    int score = 0;
    for (final Player player : players) {

      int fixtureMultiplier = Controls.getFixtureMultiplier(player.getTeam());

      costNow = costNow.add(player.getCostNow());
      score += player.getPoints() * fixtureMultiplier;
      form = form.add(player.getForm().multiply(BigDecimal.valueOf(fixtureMultiplier)));
      pointsPerGame = pointsPerGame.add(player.getPointsPerGame().multiply(BigDecimal.valueOf(fixtureMultiplier)));
    }

    this.costNow = costNow;
    this.points = score;
    this.form = form;
    this.pointsPerGame = pointsPerGame;
  }

  public int compareTo(final PlayerLine other) {
    return this.getCostNow().compareTo(other.getCostNow());
  }
}
