package com.haggisandchips.fantasyfootball;

import com.haggisandchips.fantasyfootball.service.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FantasyFootballApplication implements CommandLineRunner {

  private static Logger LOG = LoggerFactory.getLogger(FantasyFootballApplication.class);

  @Autowired private TeamService teamService;

  public static void main(String[] args) {

    SpringApplication.run(FantasyFootballApplication.class, args);
  }

  @Override
  public void run(String... args) throws Exception {

    teamService.process();
  }
}
