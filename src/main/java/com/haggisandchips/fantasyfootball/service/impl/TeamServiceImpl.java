package com.haggisandchips.fantasyfootball.service.impl;

import com.haggisandchips.fantasyfootball.remote.FantasyClient;
import com.haggisandchips.fantasyfootball.remote.dto.Player;
import com.haggisandchips.fantasyfootball.service.TeamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class TeamServiceImpl implements TeamService {

  @Autowired private FantasyClient fantasyClient;

  @Override
  public void process() throws IOException {

    List<Player> allPlayers = fantasyClient.getAllPlayers();

    allPlayers.forEach(System.out::println);
  }
}
