package com.haggisandchips.fantasyfootball.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Position {
  GOALKEEPER(2),
  DEFENDER(5),
  MIDFIELDER(5),
  FORWARD(3);

  @Getter
  private final int number;

  @JsonCreator
  public static Position fromValue(int value) {

    switch (value) {
      case 1:
        return GOALKEEPER;
      case 2:
        return DEFENDER;
      case 3:
        return MIDFIELDER;
      case 4:
        return FORWARD;
      default:
        throw new IllegalArgumentException();
    }
  }
}
