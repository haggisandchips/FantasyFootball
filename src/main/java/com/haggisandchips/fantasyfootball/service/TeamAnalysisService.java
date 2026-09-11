package com.haggisandchips.fantasyfootball.service;

import com.haggisandchips.fantasyfootball.calculation.KillerTeamSearchProgressListener;
import com.haggisandchips.fantasyfootball.calculation.TransferSearchProgressListener;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.SquadSubstitution;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.Team;
import com.haggisandchips.fantasyfootball.domain.TransferSuggestion;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// Split into independent steps (rather than one bundled analyse()) so callers can render/trigger
// each one separately - e.g. a UI showing the squad as soon as it's fetched, computing transfer
// suggestions in the background afterwards, and only calculating killer teams on demand.
public interface TeamAnalysisService {

  List<Player> fetchAllPlayers() throws IOException, InterruptedException;

  Squad fetchMySquad(List<Player> allPlayers) throws IOException, InterruptedException;

  // The expensive search (see TransferSelector) - every combination that improves on the chosen
  // strategy's stat, unranked. Like calculateKillerTeam below, this is strategy-dependent (which
  // candidates are even considered, and whether a combination counts as an improvement, both depend
  // on the chosen stat), so unlike a plain re-rank it has to be re-run whenever the strategy changes
  // (see TransfersTab's cache, keyed by Strategy and considerFixtures together). considerFixtures
  // toggles whether fixture count/difficulty affect the ranking at all (see PlayerLine/Team's own
  // field of the same name) - off means every candidate is judged purely on season-to-date stats,
  // since a transfer is a longer-term decision than any one gameweek's fixtures; on folds in the
  // same fixture-difficulty signal Starting/OptimalElevenSelector use for picking who starts, useful
  // e.g. when planning a Free Hit into a run of favourable/double fixtures. transferBudget is the
  // largest number of simultaneous transfers to search for (every size from 1 up to it) - normally
  // mySquad.getFreeTransfers(), but TransfersTab lets it be raised or lowered so a hit (or forcing a
  // search even with 0 free transfers) can be planned deliberately.
  List<TransferSuggestion> calculateTransferSuggestions(
      Squad mySquad, List<Player> allPlayers, Strategy strategy, boolean considerFixtures, int transferBudget,
      TransferSearchProgressListener progressListener);

  // Cheap: just sorts/groups/limits an already-computed suggestion list for one strategy. Inner key
  // is the number of transfers used by suggestions in that list (e.g. 1 or 2).
  Map<Integer, List<TransferSuggestion>> rankTransferSuggestions(
      List<TransferSuggestion> suggestions, Strategy strategy);

  // Unlike transfer suggestions, the killer-team search also depends on maxBudget and
  // minimumThresholds (different per-position points/form/points-per-game minimums decide which
  // players are even considered, and maxBudget bounds affordability) - so there's no cheap shared
  // step to split a ranking pass out of; each (strategy, maxBudget, minimumThresholds) combination is
  // its own full search (see KillerTeamTab's cache). minimumThresholds is user-editable per position
  // in the UI (see TeamSelector.buildCombinations) rather than a fixed constant, since the right
  // cutoff depends on where the season's data actually is and is best judged by eye.
  Team calculateKillerTeam(
      List<Player> allPlayers, Strategy strategy, BigDecimal maxBudget, Map<Position, Double> minimumThresholds,
      boolean considerFixtures, KillerTeamSearchProgressListener progressListener);

  // Submits a suggested transfer to the live FPL account the given squad was fetched from. Only
  // possible when mySquad.getTransferContext() is non-null - callers should check that before
  // offering this at all (see TransfersTab).
  void executeTransfer(Squad mySquad, TransferSuggestion suggestion) throws IOException, InterruptedException;

  // Submits a starting-XI/bench swap and/or captaincy change to the live FPL account the given
  // squad was fetched from - see SquadSubstitution. Only possible when
  // mySquad.getTransferContext() is non-null, same as executeTransfer above.
  void executeSubstitution(Squad mySquad, SquadSubstitution substitution) throws IOException, InterruptedException;
}
