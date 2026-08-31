package com.haggisandchips.fantasyfootball.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Comparator;
import java.util.function.Function;

@RequiredArgsConstructor
@Getter
public enum Strategy {

  SCORE(Comparator.comparing(Team::getPoints), player -> (double) player.getPoints()),
  FORM(Comparator.comparing(Team::getForm).thenComparing(Team::getPoints), player -> player.getForm().doubleValue()),
  POINTS_PER_GAME(
      Comparator.comparing(Team::getPointsPerGame).thenComparing(Team::getPoints),
      player -> player.getPointsPerGame().doubleValue());

  private final Comparator<Team> comparator;

  // The per-player stat this strategy ranks/filters by - single source of truth shared by
  // TeamSelector.buildPermutations (position threshold filtering) and KillerTeamTab (the live
  // "N players meet this" count next to each threshold field).
  private final Function<Player, Double> playerStat;

  public int compare(final Team team1, final Team team2) {

    return comparator.compare(team1, team2);
  }
}
