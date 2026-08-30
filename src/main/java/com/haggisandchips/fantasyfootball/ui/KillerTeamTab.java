package com.haggisandchips.fantasyfootball.ui;

import com.haggisandchips.fantasyfootball.domain.PlayerLine;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.Team;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Map;

// The killer-team search is an expensive combinatorial calculation, so unlike the other tabs it
// isn't run automatically - the caller supplies the calculate action via setOnCalculate() once
// player data is available, and drives showLoading()/showResult()/showError() as it progresses
// (see FantasyFootballDesktopApp).
class KillerTeamTab extends BorderPane {

  private final Button calculateButton = new Button("Calculate");

  private final StackPane contentArea = new StackPane(new Label("Loading player data..."));

  private Runnable onCalculate;

  KillerTeamTab() {

    calculateButton.setDisable(true);
    calculateButton.setOnAction(event -> {
      if (onCalculate != null) {
        onCalculate.run();
      }
    });

    final HBox toolbar = new HBox(calculateButton);
    toolbar.setPadding(new Insets(16, 16, 0, 16));

    contentArea.setAlignment(Pos.CENTER);
    contentArea.setPadding(new Insets(16));

    setTop(toolbar);
    setCenter(contentArea);
  }

  void setOnCalculate(final Runnable onCalculate) {

    this.onCalculate = onCalculate;
    calculateButton.setDisable(false);
    contentArea.getChildren().setAll(new Label("Click Calculate to build the best possible team from scratch."));
  }

  void showLoading() {

    calculateButton.setDisable(true);
    contentArea.getChildren().setAll(new ProgressIndicator());
  }

  void showResult(final Map<Strategy, Team> killerTeams) {

    calculateButton.setDisable(false);
    calculateButton.setText("Recalculate");

    final VBox root = new VBox(16);
    killerTeams.forEach((strategy, team) -> root.getChildren().add(teamSection(strategy, team)));

    final ScrollPane scrollPane = new ScrollPane(root);
    scrollPane.setFitToWidth(true);

    contentArea.getChildren().setAll(scrollPane);
  }

  void showError(final String message) {

    calculateButton.setDisable(false);
    contentArea.getChildren().setAll(new Label("Failed to calculate: " + message));
  }

  private VBox teamSection(final Strategy strategy, final Team team) {

    final Label heading = new Label(String.format(
        "%s strategy · Points: %d · Cost: £%.1fm", strategy.name(), team.getPoints(), team.getCostNow()));
    heading.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

    final VBox rows = new VBox(8);
    for (final PlayerLine line : team.getPlayerLines()) {
      if (line.getPlayers().isEmpty()) {
        continue;
      }

      final FlowPane row = new FlowPane(12, 12);
      line.getPlayers().forEach(player -> row.getChildren().add(PlayerCard.of(player)));
      rows.getChildren().add(row);
    }

    return new VBox(8, heading, rows);
  }
}
