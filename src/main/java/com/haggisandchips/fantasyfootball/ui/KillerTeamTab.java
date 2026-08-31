package com.haggisandchips.fantasyfootball.ui;

import com.haggisandchips.fantasyfootball.Controls;
import com.haggisandchips.fantasyfootball.calculation.StartingElevenSelector;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.Team;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Map;

// The killer-team search is an expensive combinatorial calculation, so unlike the other tabs it
// isn't run automatically - the caller supplies the calculate action via setOnCalculate() once
// player data is available, and drives showLoading()/showResult()/showError() as it progresses
// (see FantasyFootballDesktopApp). KillerTeamFinder only picks the best affordable 15-man squad,
// with no starting XI/bench split of its own, so showResult() runs StartingElevenSelector over it
// and renders the result exactly like MySquadTab - the same pitch, the same everything.
class KillerTeamTab extends BorderPane {

  private final Label statusLabel = new Label("Loading player data...");

  private final Button calculateButton = new Button("Calculate");

  private Runnable onCalculate;

  KillerTeamTab() {

    calculateButton.setDisable(true);
    calculateButton.setOnAction(event -> {
      if (onCalculate != null) {
        onCalculate.run();
      }
    });

    showIdle();
  }

  void setOnCalculate(final Runnable onCalculate) {

    this.onCalculate = onCalculate;
    calculateButton.setDisable(false);
    statusLabel.setText("Click Calculate to build the best possible team from scratch.");
  }

  void showLoading() {

    setTop(null);
    setCenter(new StackPane(new ProgressIndicator()));
  }

  void showResult(final Map<Strategy, Team> killerTeams) {

    final Team team = killerTeams.get(Strategy.SCORE);
    final StartingElevenSelector.Result picked = StartingElevenSelector.select(team.getPlayers());

    final Squad squad = new Squad(
        team.getCostNow(), Controls.MAX_BUDGET.subtract(team.getCostNow()), 0, team,
        picked.startingEleven(), picked.substitutes(), null, null, team.getPoints(), null);

    setTop(null);
    setCenter(new MySquadTab(squad));
  }

  void showError(final String message) {

    setTop(null);
    setCenter(new StackPane(new Label("Failed to calculate: " + message)));
  }

  private void showIdle() {

    final VBox idleContent = new VBox(12, statusLabel, calculateButton);
    idleContent.setAlignment(Pos.CENTER);

    setTop(null);
    setCenter(new StackPane(idleContent));
  }
}
