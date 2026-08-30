package com.haggisandchips.fantasyfootball.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public class Squad {

  private final BigDecimal squadValue;

  private final BigDecimal moneyAvailable;

  private final int freeTransfers;

  private final Team team;
}
