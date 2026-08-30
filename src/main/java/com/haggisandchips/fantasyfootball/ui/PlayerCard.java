package com.haggisandchips.fantasyfootball.ui;

import com.haggisandchips.fantasyfootball.domain.Player;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

final class PlayerCard {

  private PlayerCard() {
  }

  static VBox of(final Player player) {

    return of(player, "");
  }

  static VBox of(final Player player, final String badge) {

    final Label nameLabel = new Label(player.getName() + badge);
    nameLabel.setStyle("-fx-font-weight: bold;");

    final Label detailLabel = new Label(
        String.format("Team %s · £%.1fm · %d pts", player.getTeam(), player.getCostNow(), player.getPoints()));
    detailLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");

    final VBox card = new VBox(4, nameLabel, detailLabel);
    card.setPadding(new Insets(8, 12, 8, 12));
    card.setStyle("-fx-border-color: derive(-fx-color, -20%); -fx-border-radius: 4; "
        + "-fx-background-radius: 4; -fx-background-color: derive(-fx-color, 8%);");
    card.setPrefWidth(170);

    return card;
  }
}
