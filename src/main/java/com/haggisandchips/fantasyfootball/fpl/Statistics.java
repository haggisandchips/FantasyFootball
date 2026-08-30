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
}
