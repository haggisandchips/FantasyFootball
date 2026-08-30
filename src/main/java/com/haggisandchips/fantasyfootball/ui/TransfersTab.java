package com.haggisandchips.fantasyfootball.ui;

import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Status;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.TransferSuggestion;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

class TransfersTab extends ScrollPane {

  private final Map<Integer, List<TransferSuggestion>> suggestionsByCount;

  private final VBox suggestionsBox = new VBox(8);

  TransfersTab(final Squad mySquad, final Map<Strategy, Map<Integer, List<TransferSuggestion>>> transferSuggestionsByStrategy) {

    suggestionsByCount = new TreeMap<>(transferSuggestionsByStrategy.getOrDefault(Strategy.SCORE, Map.of()));

    refreshSuggestions(defaultTransferCount());

    final VBox root = new VBox(16,
        sectionLabel("Suggested Transfers"),
        transferCountToggle(),
        suggestionsBox,
        new Separator(),
        sectionLabel("Injured / Doubtful"),
        injuredList(mySquad));
    root.setPadding(new Insets(16));

    setContent(root);
    setFitToWidth(true);
  }

  private int defaultTransferCount() {

    if (suggestionsByCount.containsKey(2)) {
      return 2;
    }

    return suggestionsByCount.keySet().stream().findFirst().orElse(0);
  }

  private HBox transferCountToggle() {

    if (suggestionsByCount.size() < 2) {
      return new HBox();
    }

    final int defaultCount = defaultTransferCount();
    final ToggleGroup group = new ToggleGroup();
    final HBox box = new HBox(12);

    for (final Integer transferCount : suggestionsByCount.keySet()) {
      final RadioButton button = new RadioButton(transferCount + (transferCount == 1 ? " transfer" : " transfers"));
      button.setToggleGroup(group);
      button.setSelected(transferCount == defaultCount);
      button.setOnAction(event -> refreshSuggestions(transferCount));
      box.getChildren().add(button);
    }

    return box;
  }

  private void refreshSuggestions(final int transferCount) {

    final List<TransferSuggestion> suggestions = suggestionsByCount.getOrDefault(transferCount, List.of());

    suggestionsBox.getChildren().clear();
    if (suggestions.isEmpty()) {
      suggestionsBox.getChildren().add(new Label("No suggestions available."));
      return;
    }

    for (final TransferSuggestion suggestion : suggestions) {
      suggestionsBox.getChildren().add(suggestionRow(suggestion));
    }
  }

  private VBox suggestionRow(final TransferSuggestion suggestion) {

    final StringBuilder out = new StringBuilder();
    final StringBuilder in = new StringBuilder();
    for (final Map.Entry<Player, Player> transfer : suggestion.getTransfers().entrySet()) {
      if (!out.isEmpty()) {
        out.append(", ");
        in.append(", ");
      }
      out.append(transfer.getKey().getName());
      in.append(transfer.getValue().getName());
    }

    final Label transferLabel = new Label(String.format("Out: %s  →  In: %s", out, in));
    transferLabel.setStyle("-fx-font-weight: bold;");

    final Label detailLabel = new Label(String.format(
        "New team points: %d · New team cost: £%.1fm",
        suggestion.getTeam().getPoints(), suggestion.getTeam().getCostNow()));
    detailLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");

    final VBox row = new VBox(4, transferLabel, detailLabel);
    row.setPadding(new Insets(8, 12, 8, 12));
    row.setStyle("-fx-border-color: derive(-fx-color, -20%); -fx-border-radius: 4; "
        + "-fx-background-radius: 4; -fx-background-color: derive(-fx-color, 8%);");

    return row;
  }

  private VBox injuredList(final Squad squad) {

    final List<Player> squadPlayers = new ArrayList<>(squad.getStartingEleven());
    squadPlayers.addAll(squad.getSubstitutes());

    final VBox box = new VBox(8);
    for (final Player player : squadPlayers) {
      if (player.getStatus() == Status.AVAILABLE) {
        continue;
      }

      final Label nameLabel = new Label(String.format("%s (%s)", player.getName(), player.getStatus()));
      nameLabel.setStyle("-fx-font-weight: bold;");

      final String chance = player.getChanceOfPlayingNextRound() == null
          ? "unknown"
          : player.getChanceOfPlayingNextRound() + "%";
      final String news = player.getNews() == null || player.getNews().isBlank() ? "No details" : player.getNews();

      final Label detailLabel = new Label(String.format("Chance of playing: %s · %s", chance, news));
      detailLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");
      detailLabel.setWrapText(true);

      box.getChildren().add(new VBox(2, nameLabel, detailLabel));
    }

    if (box.getChildren().isEmpty()) {
      box.getChildren().add(new Label("No injury or availability concerns."));
    }

    return box;
  }

  private Label sectionLabel(final String text) {

    final Label label = new Label(text);
    label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
    return label;
  }
}
