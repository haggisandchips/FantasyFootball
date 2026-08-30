package com.haggisandchips.fantasyfootball.service.impl;

import com.haggisandchips.fantasyfootball.Controls;
import com.haggisandchips.fantasyfootball.calculation.KillerTeamFinder;
import com.haggisandchips.fantasyfootball.calculation.TeamSelector;
import com.haggisandchips.fantasyfootball.calculation.TransferSelector;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.Team;
import com.haggisandchips.fantasyfootball.domain.TransferSuggestion;
import com.haggisandchips.fantasyfootball.fpl.PlayerDataClient;
import com.haggisandchips.fantasyfootball.service.AnalysisResult;
import com.haggisandchips.fantasyfootball.service.TeamAnalysisService;
import com.haggisandchips.fantasyfootball.squad.SquadProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.haggisandchips.fantasyfootball.domain.Status.AVAILABLE;

@RequiredArgsConstructor
@Service
public class TeamAnalysisServiceImpl implements TeamAnalysisService {

  private static final Predicate<Player> AVAILABLE_PLAYERS = player -> player.getStatus() == AVAILABLE;

  private final PlayerDataClient playerDataClient;

  private final SquadProvider squadProvider;

  private static Map<Position, List<Player>> copyAvailablePlayers(
      final Map<Position, List<Player>> availablePlayers) {

    final Map<Position, List<Player>> copy = new HashMap<>();

    for (final Map.Entry<Position, List<Player>> entry : availablePlayers.entrySet()) {
      copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }

    return copy;
  }

  private static Comparator<TransferSuggestion> transferSuggestionComparator(final Strategy strategy) {

    return Comparator
        .comparingInt(TransferSuggestion::getUnavailablePlayersOut)
        .thenComparing((first, second) -> strategy.compare(first.getTeam(), second.getTeam()))
        .reversed();
  }

  @Override
  public AnalysisResult analyse() throws IOException, InterruptedException {

    final List<Player> allPlayers = playerDataClient.getAllPlayers();

    final Map<Position, List<Player>> availablePlayers =
        allPlayers.stream()
            .filter(AVAILABLE_PLAYERS)
            .collect(Collectors.groupingBy(Player::getPosition));

    final Map<Strategy, Team> killerTeams = new HashMap<>();
    for (final Strategy strategy : Controls.STRATEGIES) {
      killerTeams.put(
          strategy,
          KillerTeamFinder.find(
              strategy,
              TeamSelector.buildPermutations(strategy, copyAvailablePlayers(availablePlayers)),
              Controls.MAX_BUDGET));
    }

    final Squad mySquad = squadProvider.getMySquad(allPlayers);

    final Map<Strategy, List<TransferSuggestion>> transferSuggestionsByStrategy = new HashMap<>();
    if (mySquad.getFreeTransfers() > 0 || Controls.FREE_TRANSFERS_OVERRIDE > 0) {
      final List<TransferSuggestion> suggestions =
          TransferSelector.getTransferSuggestions(mySquad, copyAvailablePlayers(availablePlayers));

      for (final Strategy strategy : Controls.STRATEGIES) {
        final List<TransferSuggestion> sorted = new ArrayList<>(suggestions);
        sorted.sort(transferSuggestionComparator(strategy));
        transferSuggestionsByStrategy.put(
            strategy,
            sorted.stream().limit(Controls.MAX_TRANSFER_SUGGESTIONS_LOGGED).collect(Collectors.toList()));
      }
    }

    return new AnalysisResult(availablePlayers, killerTeams, mySquad, transferSuggestionsByStrategy);
  }
}
