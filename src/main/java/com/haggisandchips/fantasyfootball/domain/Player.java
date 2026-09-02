package com.haggisandchips.fantasyfootball.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.haggisandchips.fantasyfootball.Controls;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

import static java.math.RoundingMode.UNNECESSARY;

@Data
public class Player {

  @JsonProperty("id")
  private int fantasyId;

  @JsonProperty("element_type")
  private Position position;

  @JsonProperty("first_name")
  private String firstName;

  @JsonProperty("second_name")
  private String secondName;

  @JsonProperty("web_name")
  private String name;

  private String team;

  // FPL's stable per-team identifier used in shirt image URLs (see ui.PitchPlayer) - distinct from
  // "team" above (a team id the existing points algorithm keys fixtures on - left untouched).
  @JsonProperty("team_code")
  private int teamCode;

  @Setter(AccessLevel.NONE)
  private BigDecimal costNow;

  private BigDecimal sellingPrice;

  private BigDecimal form;

  @JsonProperty("points_per_game")
  private BigDecimal pointsPerGame;

  @JsonProperty("selected_by_percent")
  private BigDecimal selectedByPercent;

  private int minutes;

  @JsonProperty("total_points")
  private int points;

  private Status status;

  private String news;

  @JsonProperty("chance_of_playing_next_round")
  private Integer chanceOfPlayingNextRound;

  // Not part of bootstrap-static - populated afterwards from a separate fixtures fetch (see
  // fpl.FplPlayerDataClient). Every fixture this player's team has in their next upcoming
  // gameweek - almost always one, but more than one for a "double gameweek" and none for a
  // "blank gameweek" (never null either way, just possibly empty). Also what squad-building
  // (transfers/killer team) weights the points/form/points-per-game stats by - see Strategy and
  // PlayerLine, which multiply by this list's size (fixture *count* only - squad-building is a
  // long-term decision, so it deliberately ignores fixture difficulty; see getFixtureDifficultyMultiplier
  // for the starting-XI-only signal that doesn't).
  private List<Fixture> nextFixtures = List.of();

  // Average FDR (1 easiest - 5 hardest) this player's team has faced across its finished fixtures
  // so far this season - null before any of the team's fixtures are finished (very start of
  // season). Not part of bootstrap-static either - populated alongside nextFixtures (see
  // fpl.FplPlayerDataClient). The personal baseline getFixtureDifficultyMultiplier compares an
  // upcoming fixture's difficulty against, so a fixture only counts as tough *relative to what this
  // player's team is actually used to facing* - a team that's had a brutal run so far isn't
  // penalised for another hard game, but a team that's had it easy is nudged down for one.
  private Double averageDifficultyFaced;

  // Starting-XI-only signal (see Starting/OptimalElevenSelector.effectivePoints - NOT used by
  // Strategy/PlayerLine/squad-building, which stick to plain fixture count) - sum, across
  // nextFixtures, of a small per-fixture multiplier: 1.0 when a fixture's difficulty matches this
  // player's own historical average (or when there's no history yet), nudged up for an easier-
  // than-usual fixture and down for a harder one, clamped to a gentle range so it's a nudge on top
  // of season points rather than a dominant factor. 0 for a blank gameweek (nothing to sum).
  public double getFixtureDifficultyMultiplier() {
    double multiplier = 0;
    for (final Fixture fixture : nextFixtures) {
      multiplier += fixtureMultiplier(fixture);
    }
    return multiplier;
  }

  private double fixtureMultiplier(final Fixture fixture) {
    if (averageDifficultyFaced == null) {
      return 1.0;
    }

    final double raw =
        1.0 + Controls.FIXTURE_DIFFICULTY_ADJUSTMENT_FACTOR * (averageDifficultyFaced - fixture.getDifficulty());
    final double clamped = Math.max(
        Controls.FIXTURE_DIFFICULTY_MULTIPLIER_MIN, Math.min(Controls.FIXTURE_DIFFICULTY_MULTIPLIER_MAX, raw));

    // Rounded to a stable, reproducible value so two players who are genuinely tied on this
    // multiplier compare as an exact double equal (not off by a floating-point rounding hair) -
    // Starting/OptimalElevenSelector's tie-break coin toss relies on effectivePoints() equality.
    return Math.round(clamped * 1000.0) / 1000.0;
  }

  @JsonProperty("now_cost")
  public void setCostNow(int costNow) {

    this.costNow = new BigDecimal(costNow).divide(new BigDecimal(10), 1, UNNECESSARY);
  }
}
