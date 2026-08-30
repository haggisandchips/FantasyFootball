package com.haggisandchips.fantasyfootball.squad;

import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Squad;

import java.io.IOException;
import java.util.List;

// The user-specific side of the FPL API. FileSquadProvider (a manual stand-in) and
// AuthenticatedSquadProvider (the real, logged-in fetch) both implement this - see
// FPL_AUTH_ENABLED for how one or the other gets wired in.
public interface SquadProvider {

  Squad getMySquad(List<Player> allPlayers) throws IOException, InterruptedException;
}
