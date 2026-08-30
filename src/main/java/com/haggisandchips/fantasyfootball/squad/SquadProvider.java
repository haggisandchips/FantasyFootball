package com.haggisandchips.fantasyfootball.squad;

import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Squad;

import java.io.IOException;
import java.util.List;

// The user-specific side of the FPL API - this is the part that will need real (OAuth-style)
// authentication once it fetches https://fantasy.premierleague.com/api/my-team/{teamId}/ for real.
// FileSquadProvider is a stand-in until that lands; any replacement just implements this interface.
public interface SquadProvider {

  Squad getMySquad(List<Player> allPlayers) throws IOException;
}
