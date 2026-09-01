package com.haggisandchips.fantasyfootball.ui;

import com.haggisandchips.fantasyfootball.calculation.OptimalElevenSelector;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.SquadSubstitution;
import com.haggisandchips.fantasyfootball.service.TeamAnalysisService;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// No ScrollPane here deliberately - the pitch is meant to fill whatever space is left below the
// header exactly (see PitchView), not scroll.
class MySquadTab extends BorderPane {

  // Only used by the live "My Squad" tab, where a real submission makes sense (see
  // substitutionsBar below) - null for KillerTeamTab's own from-scratch preview, which reuses this
  // class purely to render a squad on a pitch.
  MySquadTab(final Squad squad, final TeamAnalysisService teamAnalysisService, final Runnable onSquadUpdated) {

    // Computed once and shared with PitchView (whose arrows/badges already show this same
    // suggestion) and the button below, rather than each computing its own -
    // OptimalElevenSelector.select() breaks ties with its own internal Random, so two separate
    // calls could disagree with each other over which tied player to suggest.
    final List<Player> fullSquad = new ArrayList<>(squad.getStartingEleven());
    fullSquad.addAll(squad.getSubstitutes());
    final OptimalElevenSelector.Result optimal = OptimalElevenSelector.select(fullSquad);

    setTop(header(squad));
    setCenter(new PitchView(squad, optimal));

    // Only offered against a real, logged-in FPL account - there's nothing to submit to otherwise
    // (stub mode, or KillerTeamTab's scratch squad, both of which have no TransferContext).
    if (squad.getTransferContext() != null) {
      setBottom(substitutionsBar(squad, optimal, teamAnalysisService, onSquadUpdated));
    }
  }

  private HBox header(final Squad squad) {

    final String overallPoints =
        squad.getOverallPoints() == null ? "N/A" : String.valueOf(squad.getOverallPoints());

    final HBox header = new HBox(32,
        statBox("Squad Value", String.format("£%.1fm", squad.getSquadValue())),
        statBox("Free Transfers", String.valueOf(squad.getFreeTransfers())),
        statBox("Overall Points", overallPoints));
    header.setAlignment(Pos.CENTER);
    header.setPadding(new Insets(16));
    header.getStyleClass().add("stat-bar");

    return header;
  }

  private VBox statBox(final String caption, final String value) {

    final Label captionLabel = new Label(caption);
    captionLabel.getStyleClass().add("stat-caption");

    final Label valueLabel = new Label(value);
    valueLabel.getStyleClass().add("stat-value");

    final VBox box = new VBox(2, captionLabel, valueLabel);
    box.setAlignment(Pos.CENTER);

    return box;
  }

  // The starting-XI players OptimalElevenSelector.select() would bench.
  private static List<Player> outgoingStarters(final Squad squad, final OptimalElevenSelector.Result optimal) {

    final Set<Integer> optimalIds =
        optimal.startingEleven().stream().map(Player::getFantasyId).collect(Collectors.toSet());
    return squad.getStartingEleven().stream()
        .filter(player -> !optimalIds.contains(player.getFantasyId()))
        .toList();
  }

  // The substitutes OptimalElevenSelector.select() would bring on - always the same size as
  // outgoingStarters() above, since both starting XIs are always exactly 11 players.
  private static List<Player> incomingStarters(final Squad squad, final OptimalElevenSelector.Result optimal) {

    final Set<Integer> currentIds =
        squad.getStartingEleven().stream().map(Player::getFantasyId).collect(Collectors.toSet());
    return optimal.startingEleven().stream()
        .filter(player -> !currentIds.contains(player.getFantasyId()))
        .toList();
  }

  // Automates applying the suggestion already shown on the pitch itself (the up/down arrows and
  // captain/vice-captain badges - see PitchView/OptimalElevenSelector) rather than asking the user
  // to pick anything - clicking this just submits that already-identified lineup as-is. Mirrors
  // KillerTeamTab's own "Use This Team" bar/TransfersTab's "Make this transfer" - same
  // confirm/submit/reload shape, same styling (a plain Button - see app.css's default .button).
  private HBox substitutionsBar(
      final Squad squad, final OptimalElevenSelector.Result optimal, final TeamAnalysisService teamAnalysisService,
      final Runnable onSquadUpdated) {

    final Button substituteButton = new Button("Make Substitutions");
    final Label statusLabel = new Label();
    statusLabel.getStyleClass().add("card-detail");

    substituteButton.setOnAction(event -> {
      final List<Player> outgoing = outgoingStarters(squad, optimal);
      final List<Player> incoming = incomingStarters(squad, optimal);
      final boolean captaincyChanged =
          optimal.captain() != squad.getCaptain() || optimal.viceCaptain() != squad.getViceCaptain();

      if (outgoing.isEmpty() && !captaincyChanged) {
        new Alert(AlertType.INFORMATION, "Your squad already matches the suggested lineup - no changes needed.")
            .showAndWait();
        return;
      }

      if (!confirmed(outgoing, incoming, optimal)) {
        return;
      }

      final SquadSubstitution substitution =
          new SquadSubstitution(optimal.startingEleven(), optimal.substitutes(), optimal.captain(), optimal.viceCaptain());

      substituteButton.setDisable(true);
      statusLabel.setText("Submitting...");

      final Task<Void> submitTask = new Task<>() {
        @Override
        protected Void call() throws Exception {

          teamAnalysisService.executeSubstitution(squad, substitution);
          return null;
        }
      };

      submitTask.setOnSucceeded(e -> {
        statusLabel.setText("Submitted to FPL.");
        new Alert(AlertType.INFORMATION, "Substitution(s) submitted successfully.").showAndWait();
        onSquadUpdated.run();
      });

      submitTask.setOnFailed(e -> {
        substituteButton.setDisable(false);
        final String message = submitTask.getException().getMessage();
        statusLabel.setText("Failed: " + message);
        new Alert(AlertType.ERROR, "Substitution submission failed:\n\n" + message).showAndWait();
      });

      Thread.ofVirtual().name("submit-substitutions").start(submitTask);
    });

    final HBox bar = new HBox(12, substituteButton, statusLabel);
    bar.setAlignment(Pos.CENTER);
    bar.setPadding(new Insets(12));
    return bar;
  }

  private boolean confirmed(
      final List<Player> outgoing, final List<Player> incoming, final OptimalElevenSelector.Result optimal) {

    final StringBuilder summary = new StringBuilder();
    for (int i = 0; i < outgoing.size(); i++) {
      if (!summary.isEmpty()) {
        summary.append("\n");
      }
      summary.append(String.format("OUT: %s -> IN: %s", outgoing.get(i).getName(), incoming.get(i).getName()));
    }

    if (optimal.captain() != null) {
      if (!summary.isEmpty()) {
        summary.append("\n");
      }
      summary.append("Captain: ").append(optimal.captain().getName());
    }
    if (optimal.viceCaptain() != null) {
      summary.append("\nVice-captain: ").append(optimal.viceCaptain().getName());
    }

    final Alert confirmation = new Alert(AlertType.CONFIRMATION, String.format(
        "This will submit the following change(s) directly to your live FPL team and cannot be undone through "
            + "this app:\n\n%s", summary));
    confirmation.setHeaderText("Confirm substitutions");

    final Optional<ButtonType> result = confirmation.showAndWait();
    return result.isPresent() && result.get() == ButtonType.OK;
  }
}
