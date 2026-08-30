package com.haggisandchips.fantasyfootball.ui;

import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Position;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

// A player rendered as their real club shirt (fetched from FPL's own CDN, keyed by team_code -
// see Player.teamCode), their name, and a secondary cost/points line - used both positioned on the
// pitch (where setShirtSize() lets PitchView shrink/grow it as the window resizes) and in ordinary
// flow layouts (transfer suggestions, the killer team list, etc, always at MAX_SHIRT_SIZE).
final class PitchPlayer extends VBox {

  static final double MAX_SHIRT_SIZE = 100;

  static final double MIN_SHIRT_SIZE = 34;

  // Extra width/height beyond the shirt itself for the name + detail lines and breathing room.
  static final double CARD_PADDING = 30;

  static final double DEFAULT_CARD_WIDTH = MAX_SHIRT_SIZE + CARD_PADDING;

  private final ImageView shirt;

  private final Label nameLabel;

  private final Label detailLabel;

  static PitchPlayer of(final Player player) {

    return new PitchPlayer(player, null, false);
  }

  static PitchPlayer of(final Player player, final String badge) {

    return new PitchPlayer(player, badge, false);
  }

  // Substitutes aren't shown in a formation row, so their position isn't otherwise obvious -
  // shown as part of the detail line instead of a separate badge to keep the card compact.
  static PitchPlayer ofSubstitute(final Player player) {

    return new PitchPlayer(player, null, true);
  }

  private PitchPlayer(final Player player, final String badge, final boolean showPosition) {

    // Loaded once at the highest size we'll ever display, then scaled down for display via
    // ImageView's fit properties (setShirtSize()) - backgroundLoading avoids blocking the JavaFX
    // Application Thread while each shirt downloads.
    final Image shirtImage =
        new Image(shirtUrl(player), MAX_SHIRT_SIZE, MAX_SHIRT_SIZE, true, true, true);
    shirt = new ImageView(shirtImage);
    shirt.setPreserveRatio(true);

    // Shirt defaults to centered (StackPane's default alignment); only the badge gets a per-child
    // override - setting the StackPane's own alignment instead would shift the shirt too.
    final StackPane shirtPane = new StackPane(shirt);

    if (badge != null) {
      final Label badgeLabel = new Label(badge);
      badgeLabel.getStyleClass().add("captain-badge");
      StackPane.setAlignment(badgeLabel, Pos.TOP_RIGHT);
      shirtPane.getChildren().add(badgeLabel);
    }

    nameLabel = new Label(player.getName());
    nameLabel.getStyleClass().add("player-name");

    final String detailText = showPosition
        ? String.format(
            "%s · £%.1fm · %d pts", player.getPosition().getAbbreviation(), player.getCostNow(), player.getPoints())
        : String.format("£%.1fm · %d pts", player.getCostNow(), player.getPoints());
    detailLabel = new Label(detailText);
    detailLabel.getStyleClass().add("player-detail");

    setSpacing(2);
    setAlignment(Pos.CENTER);
    getChildren().addAll(shirtPane, nameLabel, detailLabel);

    setShirtSize(MAX_SHIRT_SIZE);
  }

  // Rescales the shirt image and card width (and, modestly, the text) to the given shirt size -
  // used by PitchView to shrink cards as the pitch itself shrinks, without reloading the image.
  void setShirtSize(final double shirtSize) {

    final double size = Math.clamp(shirtSize, MIN_SHIRT_SIZE, MAX_SHIRT_SIZE);

    shirt.setFitWidth(size);
    shirt.setFitHeight(size);

    final double cardWidth = size + CARD_PADDING;
    setPrefWidth(cardWidth);
    setMaxWidth(cardWidth);

    nameLabel.setStyle(String.format("-fx-font-size: %.0fpx;", Math.clamp(size * 0.13, 9, 13)));
    detailLabel.setStyle(String.format("-fx-font-size: %.0fpx;", Math.clamp(size * 0.10, 8, 11)));
  }

  private static String shirtUrl(final Player player) {

    final String goalkeeperSuffix = player.getPosition() == Position.GOALKEEPER ? "_1" : "";
    return String.format(
        "https://fantasy.premierleague.com/dist/img/shirts/standard/shirt_%d%s-66.png",
        player.getTeamCode(), goalkeeperSuffix);
  }
}
