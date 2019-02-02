package com.haggisandchips.fantasyfootball.remote;

import com.haggisandchips.fantasyfootball.remote.dto.Player;

import java.io.IOException;
import java.util.List;

public interface FantasyClient {

  List<Player> getAllPlayers() throws IOException;
}
