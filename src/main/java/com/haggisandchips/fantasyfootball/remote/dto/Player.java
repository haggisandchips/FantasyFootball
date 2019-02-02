package com.haggisandchips.fantasyfootball.remote.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Player {

  @JsonProperty("web_name")
  private String name;
}
