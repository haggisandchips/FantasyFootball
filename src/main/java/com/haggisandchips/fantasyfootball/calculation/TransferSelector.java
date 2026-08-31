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
import java.util.function.Consumer;

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

      // The lesser of a fixed cap and 10% of the total - total/10 alone is far too coarse once the
      // search space gets into the billions (see Controls.MAX_TRANSFER_PROGRESS_LOG_STEP).
      final long progressStep = Math.max(
          1, Math.min(Controls.MAX_TRANSFER_PROGRESS_LOG_STEP, totalCombinations / PROGRESS_LOG_STEPS));
      final int suggestionsBefore = suggestions.size();
      final int currentTransferBudget = transferBudget;
      final Progress progress = new Progress();
      progress.nextLogAt = progressStep;

      while (generator.hasMore()) {
        final List<Player> playersOut = generator.getNext();

        // Candidates are handled as they're generated (rather than collected into a list first) so
        // progress can be checked mid-recursion - a single top-level combination can itself expand
        // into millions of leaves, which previously meant no progress update until it fully returned.
        buildCandidateTransfers(
            playersOut, availablePlayers, new HashMap<>(), new ArrayList<>(myPlayers), transfers -> {
              progress.evaluated++;
              if (progress.evaluated >= progress.nextLogAt) {
                log.info("Progress: {}/{} candidate combinations evaluated ({}%)",
                    progress.evaluated, totalCombinations, Math.min(100, progress.evaluated * 100 / totalCombinations));
                progressListener.onProgress(currentTransferBudget, progress.evaluated, totalCombinations);
                progress.nextLogAt += progressStep;
              }

              if (!isAffordable(transfers, moneyAvailable)) {
                return;
              }

              final Team candidateTeam = myTeam.makeSubstitutions(transfers);
              if (candidateTeam.getPoints() > myTeam.getPoints() && TeamSelector.isValidTeam(candidateTeam)) {
                suggestions.add(new TransferSuggestion(candidateTeam, transfers));
                sizesConsidered.add(transfers.size());
              }
            });
      }

      log.info("Finished evaluating {} transfer(s): {} candidate combination(s), {} suggestion(s) found",
          transferBudget, progress.evaluated, suggestions.size() - suggestionsBefore);
      progressListener.onProgress(transferBudget, progress.evaluated, totalCombinations);
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

  // Mutable per-transferBudget progress counters - a lambda can't reassign captured locals, so
  // these live in a small holder instead of two long[1] arrays or AtomicLongs (which would
  // misleadingly suggest thread-safety is the point).
  private static final class Progress {

    long evaluated;

    long nextLogAt;
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
      final Map<Player, Player> currentTransfers,
      final List<Player> excludedPlayers,
      final Consumer<Map<Player, Player>> onCandidate) {

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
        onCandidate.accept(transfers);
      } else {
        final List<Player> nextExclusions = new ArrayList<>(exclusions);
        nextExclusions.add(playerIn);
        buildCandidateTransfers(remaining, availablePlayers, transfers, nextExclusions, onCandidate);
      }
    }
  }
}
