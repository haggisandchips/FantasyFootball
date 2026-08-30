package com.haggisandchips.fantasyfootball;

import com.haggisandchips.fantasyfootball.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@RequiredArgsConstructor
public class FantasyFootballApplication implements CommandLineRunner {

  // TODO Modernise -> Lombok - @Data, @Value, constructors etc
  // TODO Modernise -> Java 20 (19?)
  // TODO Modernise -> goJF-ify (subject to above)
  // TODO Modernise -> latest gradle
  // TODO Modernise -> streams
  // TODO Modernise -> lambdas
  // TODO Log INFO (results) to file as well as console

  // TODO Remove all warnings
  // TODO Restructure to split out into more focused components

  // TODO Wire up real squad fetching (currently stubbed in FantasyClientImpl#getMySquad)
  // TODO ... via OAUTH(?!) authentication (https://www.oliverlooney.com/blogs/FPL-APIs-Explained)

  private final TeamService teamService;

  static void main(String[] args) {

    SpringApplication.run(FantasyFootballApplication.class, args);
  }

  @Override
  public void run(String... args) throws Exception {

    teamService.process();
  }
}
