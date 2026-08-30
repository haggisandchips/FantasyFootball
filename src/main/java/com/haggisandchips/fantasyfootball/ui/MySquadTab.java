package com.haggisandchips.fantasyfootball.ui;

import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.domain.Squad;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class MySquadTab extends ScrollPane {

  MySquadTab(final Squad squad) {

    final VBox root = new VBox(16,
        header(squad),
        sectionLabel("Starting XI"),
        positionRows(squad.getStartingEleven(), squad),
        new Separator(),
        sectionLabel("Substitutes"),
        playerRow(squad.getSubstitutes(), squad));
    root.setPadding(new Insets(16));

    setContent(root);
    setFitToWidth(true);
  }

  private HBox header(final Squad squad) {

    final String overallPoints =
        squad.getOverallPoints() == null ? "N/A" : String.valueOf(squad.getOverallPoints());

    return new HBox(32,
        statBox("Squad Value", String.format("£%.1fm", squad.getSquadValue())),
        statBox("Free Transfers", String.valueOf(squad.getFreeTransfers())),
        statBox("Overall Points", overallPoints));
  }

  private VBox statBox(final String caption, final String value) {

    final Label captionLabel = new Label(caption);
    captionLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");

    final Label valueLabel = new Label(value);
    valueLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

    return new VBox(2, captionLabel, valueLabel);
  }

  private Label sectionLabel(final String text) {

    final Label label = new Label(text);
    label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
    return label;
  }

  private VBox positionRows(final List<Player> players, final Squad squad) {

    final Map<Position, List<Player>> byPosition = players.stream().collect(Collectors.groupingBy(Player::getPosition));

    final VBox rows = new VBox(8);
    for (final Position position : Position.values()) {
      final List<Player> positionPlayers = byPosition.getOrDefault(position, List.of());
      if (!positionPlayers.isEmpty()) {
        rows.getChildren().add(playerRow(positionPlayers, squad));
      }
    }

    return rows;
  }

  private FlowPane playerRow(final List<Player> players, final Squad squad) {

    final FlowPane row = new FlowPane(12, 12);
    for (final Player player : players) {
      row.getChildren().add(PlayerCard.of(player, captainBadge(player, squad)));
    }

    return row;
  }

  private String captainBadge(final Player player, final Squad squad) {

    if (player == squad.getCaptain()) {
      return " (C)";
    } else if (player == squad.getViceCaptain()) {
      return " (VC)";
    } else {
      return "";
    }
  }
}
