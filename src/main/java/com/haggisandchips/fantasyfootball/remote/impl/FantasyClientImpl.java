package com.haggisandchips.fantasyfootball.remote.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haggisandchips.fantasyfootball.remote.FantasyClient;
import com.haggisandchips.fantasyfootball.remote.dto.Player;
import com.haggisandchips.fantasyfootball.remote.dto.Statistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Component
public class FantasyClientImpl implements FantasyClient {

  @Autowired private ObjectMapper objectMapper;

  @Override
  public List<Player> getAllPlayers() throws IOException {

    // TODO Temporarily parse file
    final Statistics statistics =
        objectMapper.readValue(
            new File("C:/Users/ivor/Desktop/bootstrap-static.json"), Statistics.class);

    return statistics.getPlayers();
  }
}
