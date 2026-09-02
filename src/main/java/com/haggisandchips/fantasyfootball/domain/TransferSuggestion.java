package com.haggisandchips.fantasyfootball.domain;

import lombok.Getter;

import java.util.Map;

import static com.haggisandchips.fantasyfootball.domain.Status.AVAILABLE;

@Getter
public class TransferSuggestion {

  private final Team team;

  private final Map<Player, Player> transfers;

  private final int unavailablePlayersOut;

  public TransferSuggestion(final Team team, final Map<Player, Player> transfers) {
    this.team = team;
    this.transfers = transfers;

    int unavailablePlayersOut = 0;
    for (final Player playerOut : transfers.keySet()) {
      if (playerOut.getStatus() != AVAILABLE) {
        unavailablePlayersOut++;
      }
    }
    this.unavailablePlayersOut = unavailablePlayersOut;
  }

  public String toString() {
    final StringBuilder out = new StringBuilder();
    final StringBuilder in = new StringBuilder();

    for (final Map.Entry<Player, Player> transfer : transfers.entrySet()) {
      if (!out.isEmpty()) {
        out.append(", ");
        in.append(", ");
      }
      out.append(transfer.getKey().getName());
      in.append(transfer.getValue().getName());
    }

    return String.format(
        "Transfer Suggestion (Points=%.1f, Cost Now=%.1f) [Out: %s, In: %s, Unavailable Players Replaced: %d]",
        team.getPoints(), team.getCostNow(), out, in, unavailablePlayersOut);
  }
}
