package com.haggisandchips.fantasyfootball.fpl;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

// The raw shape of one entry from FPL's fixtures endpoint - an API transport detail, not a domain
// concept (see domain.Fixture for the per-player shape it's reduced to).
@Data
class FixtureEntry {

  @JsonProperty("team_h")
  private int teamHome;

  @JsonProperty("team_a")
  private int teamAway;

  @JsonProperty("team_h_difficulty")
  private int teamHomeDifficulty;

  @JsonProperty("team_a_difficulty")
  private int teamAwayDifficulty;

  // Fixtures are fetched with future=1 (already excludes finished ones), but kept here too since
  // nothing about that query param is enforced server-side.
  private boolean finished;

  // The gameweek this fixture falls in - null for one not yet scheduled into one (postponed/TBC),
  // which attachNextFixtures then simply ignores when working out each team's next gameweek.
  private Integer event;

  // Left as a raw ISO-8601 string (rather than parsed to an Instant) - lexicographic order matches
  // chronological order for that format, which is all the sort in FplPlayerDataClient needs. Null
  // for a fixture that hasn't been scheduled yet (postponed/TBC).
  @JsonProperty("kickoff_time")
  private String kickoffTime;
}
