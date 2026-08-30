/**
 *
 */
package com.haggisandchips.fantasyfootball.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Status {
  AVAILABLE,
  INJURED,
  UNAVAILABLE,
  DOUBTFUL,
  SUSPENDED,
  NOT_AVAILABLE;

  @JsonCreator
  public static Status fromValue(String value) {

    switch (value) {
      case "a":
        return AVAILABLE;
      case "i":
        return INJURED;
      case "d":
        return DOUBTFUL;
      case "u":
        return UNAVAILABLE;
      case "n":
        return NOT_AVAILABLE;
      case "s":
        return SUSPENDED;
      default:
        throw new IllegalArgumentException(value);
    }
  }
}
