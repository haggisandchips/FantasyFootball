package com.haggisandchips.fantasyfootball.calculation;

import com.haggisandchips.fantasyfootball.Controls;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Team;
import com.haggisandchips.fantasyfootball.domain.TransferSuggestion;
import com.haggisandchips.fantasyfootball.util.PermutationGenerator;
import com.haggisandchips.fantasyfootball.util.PermutationGeneratorImpl;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public final class TransferSelector {

  // How often to log search progress, as a fraction of the upfront combination count.
  private static final int PROGRESS_LOG_STEPS = 10;

  private TransferSelector() {
  }

  public static List<TransferSuggestion> getTransferSuggestions(
      final Squad mySquad, final Map<Position, List<Player>> availablePlayers,
      final TransferSearchProgressListener progressListener) {

    final Team myTeam = mySquad.getTeam();
    final List<Player> myPlayers = myTeam.getPlayers();

    if (myPlayers.isEmpty()) {
      log.warn("No squad data available - skipping transfer suggestions (see FantasyClient#getMySquad)");
      return List.of();
    }

    final BigDecimal moneyAvailable = mySquad.getMoneyAvailable();
    final List<TransferSuggestion> suggestions = new ArrayList<>();

    // Use this to plan longer term strategies making interim transfers towards a dream team.
    int transferBudget =
        Controls.FREE_TRANSFERS_OVERRIDE > 0
            ? Controls.FREE_TRANSFERS_OVERRIDE
            : mySquad.getFreeTransfers();

    // Avoid recomputing the same number of swaps more than once as the budget counts down.
    final Set<Integer> sizesConsidered = new HashSet<>();

    for (; transferBudget > 0; transferBudget--) {
      if (sizesConsidered.contains(transferBudget)) {
        continue;
      }

      final long totalCombinations = countCandidateTransfers(myPlayers, transferBudget, availablePlayers);
      log.info("Calculating transfer suggestions using {} transfer(s) - {} candidate combination(s) to evaluate",
          transferBudget, totalCombinations);
      progressListener.onProgress(transferBudget, 0, totalCombinations);

      final PermutationGenerator<Player> generator =
          new PermutationGeneratorImpl<>(myPlayers, transferBudget);

      final long progressStep = Math.max(1, totalCombinations / PROGRESS_LOG_STEPS);
      final int suggestionsBefore = suggestions.size();
      long evaluated = 0;
      long nextProgressLogAt = progressStep;

      while (generator.hasMore()) {
        final List<Player> playersOut = generator.getNext();

        final List<Map<Player, Player>> candidateTransfers = new ArrayList<>();
        buildCandidateTransfers(
            playersOut, availablePlayers, candidateTransfers, new HashMap<>(), new ArrayList<>(myPlayers));

        evaluated += candidateTransfers.size();
        if (evaluated >= nextProgressLogAt) {
          log.info("Progress: {}/{} candidate combinations evaluated ({}%)",
              evaluated, totalCombinations, Math.min(100, evaluated * 100 / totalCombinations));
          progressListener.onProgress(transferBudget, evaluated, totalCombinations);
          nextProgressLogAt += progressStep;
        }

        for (final Map<Player, Player> transfers : candidateTransfers) {
          if (!isAffordable(transfers, moneyAvailable)) {
            continue;
          }

          final Team candidateTeam = myTeam.makeSubstitutions(transfers);
          if (candidateTeam.getPoints() > myTeam.getPoints() && TeamSelector.isValidTeam(candidateTeam)) {
            suggestions.add(new TransferSuggestion(candidateTeam, transfers));
            sizesConsidered.add(transfers.size());
          }
        }
      }

      log.info("Finished evaluating {} transfer(s): {} candidate combination(s), {} suggestion(s) found",
          transferBudget, evaluated, suggestions.size() - suggestionsBefore);
      progressListener.onProgress(transferBudget, evaluated, totalCombinations);
    }

    return suggestions;
  }

  // Upper bound on how many candidate transfer combinations buildCandidateTransfers will produce
  // for this transfer budget: for each combination of players out, the product of how many
  // available players occupy each of their positions (ignores the few exclusions
  // buildCandidateTransfers applies - e.g. a player already in the squad - so this can slightly
  // over-count, which is fine for a progress denominator).
  private static long countCandidateTransfers(
      final List<Player> myPlayers, final int transferBudget, final Map<Position, List<Player>> availablePlayers) {

    final PermutationGenerator<Player> generator = new PermutationGeneratorImpl<>(myPlayers, transferBudget);
    long total = 0;

    while (generator.hasMore()) {
      long comboCount = 1;
      for (final Player playerOut : generator.getNext()) {
        comboCount *= availablePlayers.getOrDefault(playerOut.getPosition(), List.of()).size();
      }
      total += comboCount;
    }

    return total;
  }

  private static boolean isAffordable(final Map<Player, Player> transfers, final BigDecimal moneyAvailable) {
    BigDecimal coffers = moneyAvailable;

    for (final Map.Entry<Player, Player> transfer : transfers.entrySet()) {
      coffers = coffers.add(transfer.getKey().getSellingPrice()).subtract(transfer.getValue().getCostNow());
    }

    return coffers.compareTo(BigDecimal.ZERO) >= 0;
  }

  private static void buildCandidateTransfers(
      final List<Player> playersOut,
      final Map<Position, List<Player>> availablePlayers,
      final List<Map<Player, Player>> candidateTransfers,
      final Map<Player, Player> currentTransfers,
      final List<Player> excludedPlayers) {

    final List<Player> remaining = new ArrayList<>(playersOut);
    final Player playerOut = remaining.remove(0);

    final List<Player> exclusions = new ArrayList<>(excludedPlayers);
    exclusions.add(playerOut);

    for (final Player playerIn : availablePlayers.get(playerOut.getPosition())) {
      if (exclusions.contains(playerIn)) {
        continue;
      }

      final Map<Player, Player> transfers = new HashMap<>(currentTransfers);
      transfers.put(playerOut, playerIn);

      if (remaining.isEmpty()) {
        candidateTransfers.add(transfers);
      } else {
        final List<Player> nextExclusions = new ArrayList<>(exclusions);
        nextExclusions.add(playerIn);
        buildCandidateTransfers(remaining, availablePlayers, candidateTransfers, transfers, nextExclusions);
      }
    }
  }
}
