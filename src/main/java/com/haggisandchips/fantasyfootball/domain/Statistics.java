package com.haggisandchips.fantasyfootball.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class Statistics {

  @JsonProperty("elements")
  private List<Player> players;
}
