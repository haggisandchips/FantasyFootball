package com.haggisandchips.fantasyfootball.report;

import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.Team;
import com.haggisandchips.fantasyfootball.domain.TransferSuggestion;

import java.util.List;
import java.util.Map;

// Presentation of each analysis step, kept separate from TeamAnalysisService so a future UI can
// render the same data differently instead of scraping log output. Split into one method per step
// since the UI now fetches/calculates each step independently rather than all at once.
public interface AnalysisReporter {

  void reportAllPlayers(List<Player> allPlayers);

  void reportSquad(Squad mySquad);

  // Reports one strategy's ranking at a time (see TeamAnalysisService.rankTransferSuggestions) -
  // ranking the rest is now on-demand UI work, not computed eagerly for every strategy.
  void reportTransferSuggestions(Strategy strategy, Map<Integer, List<TransferSuggestion>> byTransferCount);

  // Reported once per (strategy, budget) calculation as the user triggers them (see KillerTeamTab),
  // rather than once for every strategy up front.
  void reportKillerTeam(Strategy strategy, Team team);
}
