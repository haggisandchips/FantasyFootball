package com.haggisandchips.fantasyfootball;

import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.domain.Strategy;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;

import static com.haggisandchips.fantasyfootball.domain.Position.DEFENDER;
import static com.haggisandchips.fantasyfootball.domain.Position.FORWARD;
import static com.haggisandchips.fantasyfootball.domain.Position.GOALKEEPER;
import static com.haggisandchips.fantasyfootball.domain.Position.MIDFIELDER;
import static com.haggisandchips.fantasyfootball.domain.Strategy.FORM;
import static com.haggisandchips.fantasyfootball.domain.Strategy.POINTS_PER_GAME;
import static com.haggisandchips.fantasyfootball.domain.Strategy.SCORE;

public final class Controls {

  private static final Map<String, Integer> DOUBLE_FIXTURE_TEAMS = Map.of(
    /*  "5", 2,
      "7", 2,
      "13", 2,
      "14", 2,
      "15", 2,
      "18", 2*/);

  public static final EnumSet<Strategy> STRATEGIES = EnumSet.of(
      SCORE/*, FORM, POINTS_PER_GAME*/
  );

  public static final BigDecimal MAX_BUDGET = new BigDecimal("99.3");

  // TODO Find a better means - property weighted by cost maybe?
  public static final Map<Position, Integer> MINIMUM_SCORE_THRESHOLD = Map.of(
      GOALKEEPER, 5,
      DEFENDER, 10,
      MIDFIELDER, 13,
      FORWARD, 10
  );

  public static final Map<Position, Double> MINIMUM_POINTS_PER_GAME_THRESHOLD = Map.of(
      GOALKEEPER, 3.5,
      DEFENDER, 3.5,
      MIDFIELDER, 3.5,
      FORWARD, 3.5
  );

  public static final Map<Position, Double> MINIMUM_FORM_THRESHOLD = Map.of(
      GOALKEEPER, 1.5,
      DEFENDER, 4.0,
      MIDFIELDER, 4.0,
      FORWARD, 3.0
  );

  public static final int MAX_PERMUTATIONS_PER_SCORE = 5;

  public static final int MAXIMUM_PLAYERS_FROM_TEAM = 3;

  public static final int MAX_TRANSFER_SUGGESTIONS_LOGGED = 5;

  // Plan longer term transfer strategies towards a dream team instead of using the real free
  // transfer count. 0 uses whatever Squad.getFreeTransfers() reports.
  public static final int FREE_TRANSFERS_OVERRIDE = 0;

  // FPL reports no per-transfer point cost while a wildcard/free hit chip is active (or during the
  // one-off pre-deadline-1 grace period) - real free transfers are effectively unlimited then, but
  // TransferSelector's permutation search over N simultaneous swaps blows up combinatorially, so
  // this caps how many it's asked to consider at once rather than trying up to a full 15-man rebuild
  // (that's what the Killer Team tab is for).
  public static final int UNLIMITED_TRANSFER_SUGGESTION_BUDGET = 2;

  private Controls() {
  }

  public static int getFixtureMultiplier(String team) {

    return DOUBLE_FIXTURE_TEAMS.getOrDefault(team, 1);
  }
}
