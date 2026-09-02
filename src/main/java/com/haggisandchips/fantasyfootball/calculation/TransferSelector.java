package com.haggisandchips.fantasyfootball.calculation;

import com.haggisandchips.fantasyfootball.Controls;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.Team;
import com.haggisandchips.fantasyfootball.domain.TransferSuggestion;
import com.haggisandchips.fantasyfootball.util.CombinationGenerator;
import com.haggisandchips.fantasyfootball.util.CombinationGeneratorImpl;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
public final class TransferSelector {

  // How often to log search progress, as a fraction of the upfront combination count.
  private static final int PROGRESS_LOG_STEPS = 10;

  private TransferSelector() {
  }

  // Thrown from deep inside buildCandidateTransfers' recursion to unwind the whole search in one go
  // once the running Task has been cancelled (see getTransferSuggestions' try/catch) - cheaper than
  // threading a "should I stop?" flag through every recursive call, and the only way to actually
  // abort mid-recursion rather than just at a loop boundary.
  private static final class SearchCancelled extends RuntimeException {
  }

  public static List<TransferSuggestion> getTransferSuggestions(
      final Squad mySquad, final Map<Position, List<Player>> availablePlayers, final Strategy strategy,
      final boolean considerFixtures, final TransferSearchProgressListener progressListener) {

    // mySquad.getTeam() is always the fixture-unaware baseline (see SquadProvider implementations) -
    // rebuilt here per the toggle so myTeam and every candidate makeSubstitutions() builds from it
    // (see below) are compared on the same footing, whichever way the toggle is set.
    final Team myTeam = mySquad.getTeam().withFixtureConsideration(considerFixtures);
    final List<Player> myPlayers = myTeam.getPlayers();

    if (myPlayers.isEmpty()) {
      log.warn("No squad data available - skipping transfer suggestions (see FantasyClient#getMySquad)");
      return List.of();
    }

    final Map<Position, List<Player>> candidatePool = filterCandidatePool(myPlayers, availablePlayers, strategy);

    final BigDecimal moneyAvailable = mySquad.getMoneyAvailable();
    final List<TransferSuggestion> suggestions = new ArrayList<>();

    // A player already in the squad can never be bought "in" as a replacement - true for the whole
    // search, so computed once rather than re-derived at every recursion step.
    final Set<Player> excludedFromSquad = new HashSet<>(myPlayers);

    // Reused across the entire search via backtracking (buildCandidateTransfers mutates an entry in,
    // recurses, then undoes it) instead of allocating a fresh HashMap/List at every recursion node.
    // The previous copy-per-node approach allocated proportionally to the number of *nodes* in the
    // search tree, which at transferBudget=3 runs into the billions - slow, and (per a JVM crash
    // under that sustained GC load) not just an inconvenience.
    final Map<Player, Player> transfers = new HashMap<>();
    final Set<Player> selectedIns = new HashSet<>();

    // Use this to plan longer term strategies making interim transfers towards a dream team.
    int transferBudget =
        Controls.FREE_TRANSFERS_OVERRIDE > 0
            ? Controls.FREE_TRANSFERS_OVERRIDE
            : mySquad.getFreeTransfers();

    // Avoid recomputing the same number of swaps more than once as the budget counts down.
    final Set<Integer> sizesConsidered = new HashSet<>();

    try {
      for (; transferBudget > 0; transferBudget--) {
        if (sizesConsidered.contains(transferBudget)) {
          continue;
        }

        final long totalCombinations = countCandidateTransfers(myPlayers, transferBudget, candidatePool);
        log.info("Calculating transfer suggestions using {} transfer(s) - {} candidate combination(s) to evaluate",
            transferBudget, totalCombinations);
        progressListener.onProgress(transferBudget, 0, totalCombinations);

        final CombinationGenerator<Player> generator =
            new CombinationGeneratorImpl<>(myPlayers, transferBudget);

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
              playersOut, 0, candidatePool, excludedFromSquad, selectedIns, transfers, candidate -> {
                if (Thread.currentThread().isInterrupted()) {
                  throw new SearchCancelled();
                }

                progress.evaluated++;
                if (progress.evaluated >= progress.nextLogAt) {
                  log.info("Progress: {}/{} candidate combinations evaluated ({}%)",
                      progress.evaluated, totalCombinations, Math.min(100, progress.evaluated * 100 / totalCombinations));
                  progressListener.onProgress(currentTransferBudget, progress.evaluated, totalCombinations);
                  progress.nextLogAt += progressStep;
                }

                if (!isAffordable(candidate, moneyAvailable)) {
                  return;
                }

                final Team candidateTeam = myTeam.makeSubstitutions(candidate);
                if (strategy.compare(candidateTeam, myTeam) > 0 && TeamSelector.isValidTeam(candidateTeam)) {
                  // Defensive copy - TransferSuggestion keeps this map, but `candidate` is the single
                  // shared map backtracking reuses (and keeps mutating) for the rest of the search.
                  suggestions.add(new TransferSuggestion(candidateTeam, new HashMap<>(candidate)));
                  sizesConsidered.add(candidate.size());
                }
              });
        }

        log.info("Finished evaluating {} transfer(s): {} candidate combination(s), {} suggestion(s) found",
            transferBudget, progress.evaluated, suggestions.size() - suggestionsBefore);
        progressListener.onProgress(transferBudget, progress.evaluated, totalCombinations);
      }
    } catch (final SearchCancelled cancelled) {
      log.info("Transfer search cancelled - {} suggestion(s) found before cancellation", suggestions.size());
    }

    return suggestions;
  }

  // Shrinks the "player in" candidate pool per position before the search runs, anchored off the
  // squad's own players rather than an absolute number - without this, every status-available
  // player in a position is a candidate (the unbounded pool that caused the JVM crash fixed earlier
  // this session).
  private static Map<Position, List<Player>> filterCandidatePool(
      final List<Player> myPlayers, final Map<Position, List<Player>> availablePlayers, final Strategy strategy) {

    final Function<Player, Double> stat = strategy.getPlayerStat();
    final Map<Position, List<Player>> filtered = new HashMap<>();

    for (final Map.Entry<Position, List<Player>> entry : availablePlayers.entrySet()) {
      final Position position = entry.getKey();

      final List<Player> squadPlayersInPosition = myPlayers.stream()
          .filter(player -> player.getPosition() == position)
          .sorted(Comparator.comparingDouble(stat::apply))
          .toList();

      if (squadPlayersInPosition.isEmpty()) {
        filtered.put(position, entry.getValue());
        continue;
      }

      // The 2nd-worst, not the worst - one outlier (an expensive dud, or a token cheap bench
      // player) shouldn't single-handedly set the floor.
      final int anchorIndex = Math.min(1, squadPlayersInPosition.size() - 1);
      final double anchorStat = stat.apply(squadPlayersInPosition.get(anchorIndex));
      final double floor = Math.max(0, anchorStat - anchorStat * Controls.TRANSFER_CANDIDATE_STAT_MARGIN_FRACTION);

      final BigDecimal cheapestOwnedCost = squadPlayersInPosition.stream()
          .map(Player::getCostNow)
          .min(BigDecimal::compareTo)
          .orElseThrow();

      // The top N by the chosen stat, regardless of the floor/cost checks below - early in the
      // season everyone's stats are small and close together, so a percentage-based margin on a
      // tiny anchor value can be tighter than intended and exclude a candidate who's genuinely one
      // of the best available. A missed transfer compounds (FPL's sub-optimal picks take gameweeks
      // to fix), so this errs generous rather than fast.
      final List<Player> topRankedByStat = entry.getValue().stream()
          .sorted(Comparator.comparingDouble(stat::apply).reversed())
          .limit(Controls.TRANSFER_CANDIDATE_POOL_SIZE)
          .toList();
      final Set<Player> topRanked = new HashSet<>(topRankedByStat);

      // A candidate passes on the chosen stat, on price, or on rank - this is what stops an
      // expensive, poorly performing owned player from raising the floor high enough to exclude
      // perfectly good cheap or simply-better alternatives.
      final List<Player> candidates = entry.getValue().stream()
          .filter(player -> stat.apply(player) >= floor
              || player.getCostNow().compareTo(cheapestOwnedCost) <= 0
              || topRanked.contains(player))
          .toList();

      log.info("Transfer {} candidate pool: kept {} of {} players ({} floor {}, cost <= {}, or top {} by {})",
          position, candidates.size(), entry.getValue().size(), strategy.name(), floor, cheapestOwnedCost,
          Controls.TRANSFER_CANDIDATE_POOL_SIZE, strategy.name());

      filtered.put(position, candidates);
    }

    return filtered;
  }

  // Upper bound on how many candidate transfer combinations buildCandidateTransfers will produce
  // for this transfer budget: for each combination of players out, the product of how many
  // available players occupy each of their positions (ignores the few exclusions
  // buildCandidateTransfers applies - e.g. a player already in the squad - so this can slightly
  // over-count, which is fine for a progress denominator).
  private static long countCandidateTransfers(
      final List<Player> myPlayers, final int transferBudget, final Map<Position, List<Player>> availablePlayers) {

    final CombinationGenerator<Player> generator = new CombinationGeneratorImpl<>(myPlayers, transferBudget);
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

  // Backtracking, not accumulate-then-branch: `transfers` and `selectedIns` are the same mutable
  // instances all the way down the recursion, with each trial undone (removed) after its subtree
  // returns - so the whole search allocates two collections total, not one per node. A player
  // already in the squad (playersOut included) is always excluded via `excludedFromSquad` without
  // needing its own per-node tracking, since squad membership never changes during the search.
  private static void buildCandidateTransfers(
      final List<Player> playersOut,
      final int index,
      final Map<Position, List<Player>> availablePlayers,
      final Set<Player> excludedFromSquad,
      final Set<Player> selectedIns,
      final Map<Player, Player> transfers,
      final Consumer<Map<Player, Player>> onCandidate) {

    if (index == playersOut.size()) {
      onCandidate.accept(transfers);
      return;
    }

    final Player playerOut = playersOut.get(index);

    for (final Player playerIn : availablePlayers.get(playerOut.getPosition())) {
      if (excludedFromSquad.contains(playerIn) || selectedIns.contains(playerIn)) {
        continue;
      }

      selectedIns.add(playerIn);
      transfers.put(playerOut, playerIn);

      buildCandidateTransfers(
          playersOut, index + 1, availablePlayers, excludedFromSquad, selectedIns, transfers, onCandidate);

      transfers.remove(playerOut);
      selectedIns.remove(playerIn);
    }
  }
}
