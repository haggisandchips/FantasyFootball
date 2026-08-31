package com.haggisandchips.fantasyfootball.service;

import com.haggisandchips.fantasyfootball.calculation.TransferSearchProgressListener;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.Team;
import com.haggisandchips.fantasyfootball.domain.TransferSuggestion;

import java.io.IOException;
import java.util.List;
import java.util.Map;

// Split into independent steps (rather than one bundled analyse()) so callers can render/trigger
// each one separately - e.g. a UI showing the squad as soon as it's fetched, computing transfer
// suggestions in the background afterwards, and only calculating killer teams on demand.
public interface TeamAnalysisService {

  List<Player> fetchAllPlayers() throws IOException, InterruptedException;

  Squad fetchMySquad(List<Player> allPlayers) throws IOException, InterruptedException;

  // Inner key is the number of transfers used by suggestions in that list (e.g. 1 or 2).
  Map<Strategy, Map<Integer, List<TransferSuggestion>>> calculateTransferSuggestions(
      Squad mySquad, List<Player> allPlayers, TransferSearchProgressListener progressListener);

  Map<Strategy, Team> calculateKillerTeams(List<Player> allPlayers);

  // Submits a suggested transfer to the live FPL account the given squad was fetched from. Only
  // possible when mySquad.getTransferContext() is non-null - callers should check that before
  // offering this at all (see TransfersTab).
  void executeTransfer(Squad mySquad, TransferSuggestion suggestion) throws IOException, InterruptedException;
}
