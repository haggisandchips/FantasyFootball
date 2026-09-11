package com.haggisandchips.fantasyfootball.service.impl;

import com.haggisandchips.fantasyfootball.Controls;
import com.haggisandchips.fantasyfootball.calculation.KillerTeamFinder;
import com.haggisandchips.fantasyfootball.calculation.KillerTeamSearchProgressListener;
import com.haggisandchips.fantasyfootball.calculation.TeamSelector;
import com.haggisandchips.fantasyfootball.calculation.TransferSearchProgressListener;
import com.haggisandchips.fantasyfootball.calculation.TransferSelector;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.SquadSubstitution;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.Team;
import com.haggisandchips.fantasyfootball.domain.TransferSuggestion;
import com.haggisandchips.fantasyfootball.fpl.PlayerDataClient;
import com.haggisandchips.fantasyfootball.service.TeamAnalysisService;
import com.haggisandchips.fantasyfootball.squad.MyTeamExecutor;
import com.haggisandchips.fantasyfootball.squad.SquadProvider;
import com.haggisandchips.fantasyfootball.squad.TransferExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.haggisandchips.fantasyfootball.domain.Status.AVAILABLE;

@RequiredArgsConstructor
@Service
public class TeamAnalysisServiceImpl implements TeamAnalysisService {

  private static final Predicate<Player> AVAILABLE_PLAYERS = player -> player.getStatus() == AVAILABLE;

  private final PlayerDataClient playerDataClient;

  private final SquadProvider squadProvider;

  // Empty in stub mode (FPL_MY_SQUAD_FILE set), where there's no live account to submit to.
  private final Optional<TransferExecutor> transferExecutor;

  // Empty in stub mode, same as transferExecutor above.
  private final Optional<MyTeamExecutor> myTeamExecutor;

  private static Map<Position, List<Player>> groupAvailablePlayers(final List<Player> allPlayers) {

    return allPlayers.stream()
        .filter(AVAILABLE_PLAYERS)
        .collect(Collectors.groupingBy(Player::getPosition));
  }

  private static Map<Position, List<Player>> copyAvailablePlayers(
      final Map<Position, List<Player>> availablePlayers) {

    final Map<Position, List<Player>> copy = new HashMap<>();

    for (final Map.Entry<Position, List<Player>> entry : availablePlayers.entrySet()) {
      copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }

    return copy;
  }

  private static Comparator<TransferSuggestion> transferSuggestionComparator(final Strategy strategy) {

    // The whole chain is flipped by the trailing reversed() (so higher-is-better unavailablePlayersOut
    // and strategy stat sort first) - the cost term is therefore built descending here too, so that
    // after the flip it reads as ascending: on a tie, the cheaper team sorts first.
    return Comparator
        .comparingInt(TransferSuggestion::getUnavailablePlayersOut)
        .thenComparing((first, second) -> strategy.compare(first.getTeam(), second.getTeam()))
        .thenComparing(suggestion -> suggestion.getTeam().getCostNow(), Comparator.reverseOrder())
        .reversed();
  }

  @Override
  public List<Player> fetchAllPlayers() throws IOException, InterruptedException {

    return playerDataClient.getAllPlayers();
  }

  @Override
  public Squad fetchMySquad(final List<Player> allPlayers) throws IOException, InterruptedException {

    return squadProvider.getMySquad(allPlayers);
  }

  @Override
  public List<TransferSuggestion> calculateTransferSuggestions(
      final Squad mySquad, final List<Player> allPlayers, final Strategy strategy, final boolean considerFixtures,
      final int transferBudget, final TransferSearchProgressListener progressListener) {

    if (transferBudget <= 0) {
      return List.of();
    }

    final Map<Position, List<Player>> availablePlayers = groupAvailablePlayers(allPlayers);
    return TransferSelector.getTransferSuggestions(
        mySquad, copyAvailablePlayers(availablePlayers), strategy, considerFixtures, transferBudget, progressListener);
  }

  @Override
  public Map<Integer, List<TransferSuggestion>> rankTransferSuggestions(
      final List<TransferSuggestion> suggestions, final Strategy strategy) {

    final List<TransferSuggestion> sorted = new ArrayList<>(suggestions);
    sorted.sort(transferSuggestionComparator(strategy));

    final Map<Integer, List<TransferSuggestion>> byTransferCount =
        sorted.stream().collect(Collectors.groupingBy(suggestion -> suggestion.getTransfers().size()));
    byTransferCount.replaceAll(
        (transferCount, suggestionsForCount) ->
            suggestionsForCount.stream()
                .limit(Controls.MAX_TRANSFER_SUGGESTIONS_LOGGED)
                .collect(Collectors.toList()));

    return byTransferCount;
  }

  @Override
  public Team calculateKillerTeam(
      final List<Player> allPlayers, final Strategy strategy, final BigDecimal maxBudget,
      final Map<Position, Double> minimumThresholds, final boolean considerFixtures,
      final KillerTeamSearchProgressListener progressListener) {

    final Map<Position, List<Player>> availablePlayers = groupAvailablePlayers(allPlayers);
    return KillerTeamFinder.find(
        strategy,
        TeamSelector.buildCombinations(
            strategy, copyAvailablePlayers(availablePlayers), minimumThresholds, considerFixtures),
        maxBudget, considerFixtures, progressListener);
  }

  @Override
  public void executeTransfer(final Squad mySquad, final TransferSuggestion suggestion)
      throws IOException, InterruptedException {

    if (mySquad.getTransferContext() == null) {
      throw new IllegalStateException(
          "This squad has no live FPL entry to submit a transfer to (unset FPL_MY_SQUAD_FILE to use a live account)");
    }

    transferExecutor
        .orElseThrow(() -> new IllegalStateException(
            "Transfer execution isn't available - unset FPL_MY_SQUAD_FILE to use a live account"))
        .execute(mySquad.getTransferContext(), suggestion);
  }

  @Override
  public void executeSubstitution(final Squad mySquad, final SquadSubstitution substitution)
      throws IOException, InterruptedException {

    if (mySquad.getTransferContext() == null) {
      throw new IllegalStateException(
          "This squad has no live FPL entry to submit to (unset FPL_MY_SQUAD_FILE to use a live account)");
    }

    myTeamExecutor
        .orElseThrow(() -> new IllegalStateException(
            "Squad updates aren't available - unset FPL_MY_SQUAD_FILE to use a live account"))
        .execute(mySquad.getTransferContext(), substitution);
  }
}
