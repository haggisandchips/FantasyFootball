package com.haggisandchips.fantasyfootball.domain;

import lombok.Value;

// A player's next scheduled fixture - opponent, venue, and FPL's own 1 (easiest) - 5 (hardest)
// fixture difficulty rating, from this player's own team's perspective (see
// fpl.FplPlayerDataClient, which attaches this to Player after a separate fixtures fetch).
@Value
public class Fixture {

  String opponent;

  boolean home;

  int difficulty;
}
