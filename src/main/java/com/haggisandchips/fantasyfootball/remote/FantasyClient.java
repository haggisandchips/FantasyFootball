package com.haggisandchips.fantasyfootball.remote;

import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Squad;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

public interface FantasyClient {

  List<Player> getAllPlayers() throws IOException, URISyntaxException, InterruptedException;

  Squad getMySquad(List<Player> allPlayers) throws IOException;
}
