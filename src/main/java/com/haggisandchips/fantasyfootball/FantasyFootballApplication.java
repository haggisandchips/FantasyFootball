package com.haggisandchips.fantasyfootball;

import com.haggisandchips.fantasyfootball.report.AnalysisReporter;
import com.haggisandchips.fantasyfootball.service.TeamAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@RequiredArgsConstructor
public class FantasyFootballApplication implements CommandLineRunner {

  // TODO Log INFO (results) to file as well as console

  // Real squad fetching (AuthenticatedSquadProvider) needs FPL_AUTH_ENABLED=true plus a captured
  // FPL_API_AUTHORIZATION bearer token (see FplSessionAuthClient); otherwise FileSquadProvider's
  // my-squad.json stub is used.

  private final TeamAnalysisService teamAnalysisService;

  private final AnalysisReporter analysisReporter;

  static void main(String[] args) {

    SpringApplication.run(FantasyFootballApplication.class, args);
  }

  @Override
  public void run(String... args) throws Exception {

    analysisReporter.report(teamAnalysisService.analyse());
  }
}
