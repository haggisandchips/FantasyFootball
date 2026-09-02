package com.haggisandchips.fantasyfootball.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class Squad {

  private final BigDecimal squadValue;

  private final BigDecimal moneyAvailable;

  private final int freeTransfers;

  // True when FPL reports transfers as "unlimited" this gameweek (wildcard/free hit active, or
  // the pre-deadline-1 grace period) - freeTransfers is then just the suggestion-search budget
  // (see Controls.UNLIMITED_TRANSFER_SUGGESTION_BUDGET), not a real FPL-imposed limit.
  private final boolean unlimitedTransfers;

  private final Team team;

  // Display-only pick-team data - the transfer/killer-team algorithms only ever use getTeam().
  private final List<Player> startingEleven;

  private final List<Player> substitutes;

  private final Player captain;

  private final Player viceCaptain;

  // Season total ("summary_overall_points") - null when unavailable (stub mode has no FPL entry).
  private final Integer overallPoints;

  // FPL entry name (e.g. "Haggis and Chips FC") - null when unavailable (stub mode, killer team).
  private final String teamName;

  // Set only when this squad came from a real, logged-in FPL account - lets the UI offer to
  // actually submit a suggested transfer. Null in stub mode and for the killer team's scratch squad.
  private final TransferContext transferContext;
}
