package com.haggisandchips.fantasyfootball.fpl;

import com.haggisandchips.fantasyfootball.domain.Player;

import java.io.IOException;
import java.util.List;

// The public, unauthenticated side of the FPL API - never needs the user's own login.
public interface PlayerDataClient {

  List<Player> getAllPlayers() throws IOException, InterruptedException;

  // The gameweek a transfer submitted right now would actually apply to - the next one whose
  // deadline hasn't passed (bootstrap-static's "is_next" flag), not necessarily the last one played.
  int getCurrentTransferEvent() throws IOException, InterruptedException;
}
