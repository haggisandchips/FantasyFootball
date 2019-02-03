package com.haggisandchips.fantasyfootball.remote.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

import java.math.BigDecimal;

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

  @Setter(AccessLevel.NONE)
  private BigDecimal costNow;

  private BigDecimal sellingPrice;

  private BigDecimal form;

  @JsonProperty("selected_by_percent")
  private BigDecimal selectedByPercent;

  private int minutes;

  @JsonProperty("total_points")
  private int points;

  private Status status;

  private String news;

  @JsonProperty("chance_of_playing_next_round")
  private int chanceOfPlayingNextRound;

  @JsonProperty("now_cost")
  public void setCostNow(int costNow) {

    this.costNow = convertMoney(costNow);
  }

  private BigDecimal convertMoney(int value) {

    return new BigDecimal(value).divide(new BigDecimal(10));
  }
}
