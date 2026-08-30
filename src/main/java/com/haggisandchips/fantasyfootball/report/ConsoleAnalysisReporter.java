package com.haggisandchips.fantasyfootball.report;

import com.haggisandchips.fantasyfootball.Controls;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.TransferSuggestion;
import com.haggisandchips.fantasyfootball.service.AnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ConsoleAnalysisReporter implements AnalysisReporter {

  @Override
  public void report(final AnalysisResult result) {

    result.getAvailablePlayers().forEach(
        (position, players) -> {
          log.info("{}", position);
          players.forEach(player -> log.info("{}", player));
        });

    result.getKillerTeams().forEach(
        (strategy, team) -> log.info("Killer Team by {}: {}", strategy.name(), team));

    final Squad mySquad = result.getMySquad();
    log.info("Squad Value: {}", mySquad.getSquadValue());
    log.info("Money Available: {}", mySquad.getMoneyAvailable());
    log.info("Free Transfers: {}", mySquad.getFreeTransfers());
    log.info("My Squad: {}", mySquad.getTeam());

    if (mySquad.getFreeTransfers() > 0 || Controls.FREE_TRANSFERS_OVERRIDE > 0) {
      for (final Strategy strategy : Controls.STRATEGIES) {
        log.info("Transfer suggestions by {}:", strategy.name());
        for (final TransferSuggestion suggestion : result.getTransferSuggestionsByStrategy().get(strategy)) {
          log.info("{}", suggestion);
        }
      }
    } else {
      log.info("No free transfers available - skipping transfer suggestions");
    }
  }
}
