package com.haggisandchips.fantasyfootball.service;

import com.haggisandchips.fantasyfootball.calculation.KillerTeamSearchProgressListener;
import com.haggisandchips.fantasyfootball.calculation.TransferSearchProgressListener;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.domain.Squad;
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

  // The expensive, strategy-independent search (see TransferSelector) - every improving transfer
  // combination found, unranked. Run once per squad and re-ranked per strategy via
  // rankTransferSuggestions(), rather than re-run per strategy (see TransfersTab's cache).
  List<TransferSuggestion> calculateTransferSuggestions(
      Squad mySquad, List<Player> allPlayers, TransferSearchProgressListener progressListener);

  // Cheap: just sorts/groups/limits an already-computed suggestion list for one strategy. Inner key
  // is the number of transfers used by suggestions in that list (e.g. 1 or 2).
  Map<Integer, List<TransferSuggestion>> rankTransferSuggestions(
      List<TransferSuggestion> suggestions, Strategy strategy);

  // Unlike transfer suggestions, the killer-team search itself is strategy- and budget-dependent
  // (different per-position score/form/points-per-game minimums decide which players are even
  // considered, and maxBudget bounds affordability) - so, unlike transfers, there's no cheap shared
  // step to split out; each (strategy, maxBudget, minimumThresholds) combination is its own full
  // search (see KillerTeamTab's cache). minimumThresholds is user-editable per position in the UI
  // (see TeamSelector.buildPermutations) rather than a fixed constant, since the right cutoff
  // depends on where the season's data actually is and is best judged by eye.
  Team calculateKillerTeam(
      List<Player> allPlayers, Strategy strategy, BigDecimal maxBudget, Map<Position, Double> minimumThresholds,
      KillerTeamSearchProgressListener progressListener);

  // Submits a suggested transfer to the live FPL account the given squad was fetched from. Only
  // possible when mySquad.getTransferContext() is non-null - callers should check that before
  // offering this at all (see TransfersTab).
  void executeTransfer(Squad mySquad, TransferSuggestion suggestion) throws IOException, InterruptedException;
}
