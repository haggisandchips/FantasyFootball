package com.haggisandchips.fantasyfootball.ui;

import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Status;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.TransferSuggestion;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

class TransfersTab extends ScrollPane {

  // A suggestion's card width is fixed per transfer count (every card in one refresh shows the
  // same number of transfer pairs) rather than measured, so cards are exactly the same size
  // without depending on JavaFX having already computed real layout bounds.
  private static final double PAIR_WIDTH = 2 * PitchPlayer.DEFAULT_CARD_WIDTH + 60;

  private final Map<Integer, List<TransferSuggestion>> suggestionsByCount;

  private final FlowPane suggestionsBox = new FlowPane(16, 16);

  TransfersTab(final Squad mySquad, final Map<Strategy, Map<Integer, List<TransferSuggestion>>> transferSuggestionsByStrategy) {

    suggestionsByCount = new TreeMap<>(transferSuggestionsByStrategy.getOrDefault(Strategy.SCORE, Map.of()));

    suggestionsBox.setAlignment(Pos.CENTER);
    refreshSuggestions(defaultTransferCount());

    final VBox root = new VBox(16,
        sectionLabel("Suggested Transfers"),
        transferCountToggle(),
        suggestionsBox,
        new Separator(),
        sectionLabel("Injured / Doubtful"),
        injuredList(mySquad));
    root.setPadding(new Insets(20));

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

    // Every suggestion shown together has the same number of transfer pairs (they're all for this
    // one transferCount), so a single width fits all of them exactly - no measuring needed.
    final double cardWidth = transferCount * PAIR_WIDTH;
    for (final TransferSuggestion suggestion : suggestions) {
      suggestionsBox.getChildren().add(suggestionRow(suggestion, cardWidth));
    }
  }

  private VBox suggestionRow(final TransferSuggestion suggestion, final double cardWidth) {

    final FlowPane transfersRow = new FlowPane(12, 8);
    transfersRow.setAlignment(Pos.CENTER);

    for (final Map.Entry<Player, Player> transfer : suggestion.getTransfers().entrySet()) {
      final Label arrow = new Label("→");
      arrow.getStyleClass().add("transfer-arrow");

      final HBox pair = new HBox(8, PitchPlayer.of(transfer.getKey()), arrow, PitchPlayer.of(transfer.getValue()));
      pair.setAlignment(Pos.CENTER);
      transfersRow.getChildren().add(pair);
    }

    final Label detailLabel = new Label(String.format(
        "New team points: %d · New team cost: £%.1fm",
        suggestion.getTeam().getPoints(), suggestion.getTeam().getCostNow()));
    detailLabel.getStyleClass().add("card-detail");
    detailLabel.setWrapText(true);

    final VBox row = new VBox(8, transfersRow, detailLabel);
    row.getStyleClass().add("card");
    row.setAlignment(Pos.CENTER);
    row.setPrefWidth(cardWidth);
    row.setMinWidth(cardWidth);
    row.setMaxWidth(cardWidth);

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

      box.getChildren().add(injuredRow(player));
    }

    if (box.getChildren().isEmpty()) {
      box.getChildren().add(new Label("No injury or availability concerns."));
    }

    return box;
  }

  private HBox injuredRow(final Player player) {

    final Label nameLabel = new Label(String.format("%s (%s)", player.getName(), player.getStatus()));
    nameLabel.getStyleClass().add("card-title");

    final String chance = player.getChanceOfPlayingNextRound() == null
        ? "unknown"
        : player.getChanceOfPlayingNextRound() + "%";
    final String news = player.getNews() == null || player.getNews().isBlank() ? "No details" : player.getNews();

    final Label detailLabel = new Label(String.format("Chance of playing: %s · %s", chance, news));
    detailLabel.getStyleClass().add("card-detail");
    detailLabel.setWrapText(true);

    final VBox info = new VBox(2, nameLabel, detailLabel);
    info.setAlignment(Pos.CENTER_LEFT);

    final HBox row = new HBox(12, PitchPlayer.of(player), info);
    row.getStyleClass().add("card");
    row.setAlignment(Pos.CENTER_LEFT);

    return row;
  }

  private Label sectionLabel(final String text) {

    final Label label = new Label(text);
    label.getStyleClass().add("section-label");
    return label;
  }
}
