package com.haggisandchips.fantasyfootball.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.function.Function;

@RequiredArgsConstructor
@Getter
public enum Strategy {

  POINTS(
      Comparator.comparing(Team::getPoints),
      player -> (double) player.getPoints(),
      playerLine -> BigDecimal.valueOf(playerLine.getPoints()),
      player -> BigDecimal.valueOf(player.getPoints() * player.getFixtureDifficultyMultiplier())),
  FORM(
      Comparator.comparing(Team::getForm).thenComparing(Team::getPoints),
      player -> player.getForm().doubleValue(),
      PlayerLine::getForm,
      player -> player.getForm().multiply(BigDecimal.valueOf(player.getFixtureDifficultyMultiplier()))),
  POINTS_PER_GAME(
      Comparator.comparing(Team::getPointsPerGame).thenComparing(Team::getPoints),
      player -> player.getPointsPerGame().doubleValue(),
      PlayerLine::getPointsPerGame,
      player -> player.getPointsPerGame().multiply(BigDecimal.valueOf(player.getFixtureDifficultyMultiplier())));

  private final Comparator<Team> comparator;

  // The per-player stat this strategy ranks/filters by - single source of truth shared by
  // TeamSelector.buildCombinations/TransferSelector (position threshold filtering) and KillerTeamTab
  // (the live "N players meet this" count next to each threshold field).
  private final Function<Player, Double> playerStat;

  // The per-combination aggregate this strategy buckets by - PlayerLine already sums points/form/
  // points-per-game across its players (weighted however that particular PlayerLine was built - see
  // its own considerFixtures), so this just picks out which of those sums
  // TeamSelector.buildCombinations should group combinations by, so that a combination strong on the
  // chosen stat is never discarded in favour of a merely cheaper one that happens to share the same
  // total points (see PlayerLine.compareTo).
  private final Function<PlayerLine, BigDecimal> lineStat;

  // The considerFixtures=true equivalent of the per-player value lineStat's sum is built from - lets
  // TeamSelector.countCappedCombinations estimate how many combinations land in each lineStat bucket
  // without having to materialize a PlayerLine for every one of them. Only valid for that case;
  // TeamSelector falls back to playerStat itself (equivalent to a considerFixtures=false PlayerLine)
  // when the toggle is off.
  private final Function<Player, BigDecimal> weightedPlayerStat;

  public int compare(final Team team1, final Team team2) {

    return comparator.compare(team1, team2);
  }
}
