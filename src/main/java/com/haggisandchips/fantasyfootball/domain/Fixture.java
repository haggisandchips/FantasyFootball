package com.haggisandchips.fantasyfootball.domain;

import lombok.Value;

// One of a player's team's fixtures in their next upcoming gameweek - opponent, venue, and FPL's
// own 1 (easiest) - 5 (hardest) fixture difficulty rating, from this player's own team's
// perspective (see fpl.FplPlayerDataClient, which attaches these to Player after a separate
// fixtures fetch). Usually just one, but a "double gameweek" means more than one - see
// Player.nextFixtures.
@Value
public class Fixture {

  String opponent;

  boolean home;

  int difficulty;
}
