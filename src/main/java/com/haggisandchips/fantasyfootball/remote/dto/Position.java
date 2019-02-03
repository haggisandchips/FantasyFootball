/** */
package com.haggisandchips.fantasyfootball.remote.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Position {
  GOALKEEPER,
  DEFENDER,
  MIDFIELDER,
  FORWARD;

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
