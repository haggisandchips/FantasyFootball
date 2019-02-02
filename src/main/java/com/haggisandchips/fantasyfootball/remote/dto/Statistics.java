package com.haggisandchips.fantasyfootball.remote.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class Statistics {

  @JsonProperty("elements")
  private List<Player> players;
}
