package com.haggisandchips.fantasyfootball.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
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
  // "blank gameweek" (never null either way, just possibly empty). Also what team selection
  // weights the points/form/points-per-game stats by - see Strategy, PlayerLine, and
  // StartingElevenSelector.effectivePoints, all of which multiply by this list's size.
  private List<Fixture> nextFixtures = List.of();

  @JsonProperty("now_cost")
  public void setCostNow(int costNow) {

    this.costNow = new BigDecimal(costNow).divide(new BigDecimal(10), 1, UNNECESSARY);
  }
}
