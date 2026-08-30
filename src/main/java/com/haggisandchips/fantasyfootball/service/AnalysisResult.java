package com.haggisandchips.fantasyfootball.service;

import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.Team;
import com.haggisandchips.fantasyfootball.domain.TransferSuggestion;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

// The pure output of a run - no logging or presentation lives here, so it can equally be handed
// to a console reporter today or a future UI without TeamAnalysisService changing.
@Getter
@RequiredArgsConstructor
public class AnalysisResult {

  private final Map<Position, List<Player>> availablePlayers;

  private final Map<Strategy, Team> killerTeams;

  private final Squad mySquad;

  private final Map<Strategy, List<TransferSuggestion>> transferSuggestionsByStrategy;
}
