package com.haggisandchips.fantasyfootball.fpl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.haggisandchips.fantasyfootball.domain.Player;
import lombok.Data;

import java.util.List;

// The raw shape of the FPL bootstrap-static response - an API transport detail, not a domain concept.
@Data
class Statistics {

  @JsonProperty("elements")
  private List<Player> players;

  private List<Event> events;

  private List<TeamEntry> teams;

  @Data
  static class Event {

    private int id;

    // True for exactly one event: the next gameweek whose deadline hasn't passed yet, i.e. the
    // one transfers submitted right now actually apply to.
    @JsonProperty("is_next")
    private boolean isNext;
  }

  // One of the 20 real clubs - just enough to resolve a fixture's team_h/team_a ids to a
  // displayable short name (e.g. "ARS") when attaching next-fixture info to players.
  @Data
  static class TeamEntry {

    private int id;

    @JsonProperty("short_name")
    private String shortName;
  }
}
