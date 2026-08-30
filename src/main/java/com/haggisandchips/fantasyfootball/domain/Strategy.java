package com.haggisandchips.fantasyfootball.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Comparator;

@RequiredArgsConstructor
@Getter
public enum Strategy {

  SCORE(Comparator.comparing(Team::getPoints)),
  FORM(Comparator.comparing(Team::getForm).thenComparing(Team::getPoints)),
  POINTS_PER_GAME(Comparator.comparing(Team::getPointsPerGame).thenComparing(Team::getPoints));

  private final Comparator<Team> comparator;

  public int compare(final Team team1, final Team team2) {

    return comparator.compare(team1, team2);
  }
}
