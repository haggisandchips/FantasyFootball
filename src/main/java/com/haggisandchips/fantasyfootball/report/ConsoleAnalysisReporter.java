package com.haggisandchips.fantasyfootball.report;

import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Status;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.Team;
import com.haggisandchips.fantasyfootball.domain.TransferSuggestion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ConsoleAnalysisReporter implements AnalysisReporter {

  @Override
  public void reportAllPlayers(final List<Player> allPlayers) {

    allPlayers.stream()
        .filter(player -> player.getStatus() == Status.AVAILABLE)
        .collect(Collectors.groupingBy(Player::getPosition))
        .forEach((position, players) -> {
          log.info("{}", position);
          players.forEach(player -> log.info("{}", player));
        });
  }

  @Override
  public void reportSquad(final Squad mySquad) {

    log.info("Squad Value: {}", mySquad.getSquadValue());
    log.info("Money Available: {}", mySquad.getMoneyAvailable());
    log.info("Free Transfers: {}", mySquad.isUnlimitedTransfers() ? "∞" : mySquad.getFreeTransfers());
    log.info("Overall Points: {}", mySquad.getOverallPoints());
    log.info("My Squad: {}", mySquad.getTeam());
  }

  @Override
  public void reportTransferSuggestions(
      final Strategy strategy, final Map<Integer, List<TransferSuggestion>> byTransferCount) {

    if (byTransferCount.isEmpty()) {
      log.info("No transfer suggestions found for strategy {}", strategy.name());
      return;
    }

    byTransferCount.forEach((transferCount, suggestions) -> {
      log.info("Transfer suggestions by {} using {} transfer(s):", strategy.name(), transferCount);
      for (final TransferSuggestion suggestion : suggestions) {
        log.info("{}", suggestion);
      }
    });
  }

  @Override
  public void reportKillerTeam(final Strategy strategy, final Team team) {

    log.info("Killer Team by {}: {}", strategy.name(), team);
  }
}
