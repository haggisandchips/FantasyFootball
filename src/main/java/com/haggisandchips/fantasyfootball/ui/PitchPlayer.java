package com.haggisandchips.fantasyfootball.ui;

import com.haggisandchips.fantasyfootball.domain.Fixture;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Position;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

// A player rendered as their real club shirt (fetched from FPL's own CDN, keyed by team_code -
// see Player.teamCode), their name, a secondary cost/points line, and a third line for their next
// fixture - used both positioned on the pitch (where setShirtSize() lets PitchView shrink/grow it
// as the window resizes) and in ordinary flow layouts (TransfersTab's suggestion pairs and injured
// list, always at MAX_SHIRT_SIZE). The killer team list renders its own cards, not PitchPlayer.
final class PitchPlayer extends VBox {

  static final double MAX_SHIRT_SIZE = 100;

  static final double MIN_SHIRT_SIZE = 34;

  // Extra width/height beyond the shirt itself for the name + detail lines and breathing room.
  static final double CARD_PADDING = 30;

  static final double DEFAULT_CARD_WIDTH = MAX_SHIRT_SIZE + CARD_PADDING;

  // Gap between the shirt, name, detail and (where shown) fixture labels - exposed so PitchView can
  // budget the same amount of extra height it reserves for the fixture line's own font size.
  static final double LABEL_SPACING = 2;

  private final ImageView shirt;

  private final Label nameLabel;

  private final Label detailLabel;

  private final Label fixtureLabel;

  private final Fixture nextFixture;

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

    nextFixture = player.getNextFixture();
    fixtureLabel = new Label(fixtureText(nextFixture));
    fixtureLabel.getStyleClass().add("player-fixture");

    setSpacing(LABEL_SPACING);
    setAlignment(Pos.CENTER);
    getChildren().addAll(shirtPane, nameLabel, detailLabel, fixtureLabel);

    setShirtSize(MAX_SHIRT_SIZE);
  }

  // "Opponent (H/A) · FDR n" - or a placeholder for a team with no fixture in the current window (a
  // blank gameweek), which FplPlayerDataClient.attachNextFixtures leaves as null rather than omitting.
  private static String fixtureText(final Fixture fixture) {

    return fixture == null
        ? "No fixture"
        : String.format("%s (%s) · FDR %d", fixture.getOpponent(), fixture.isHome() ? "H" : "A", fixture.getDifficulty());
  }

  // FPL's own 1 (easiest, green) - 5 (hardest, red) fixture difficulty colour scale.
  private static String difficultyColor(final int difficulty) {

    switch (difficulty) {
      case 1:
        return "#2ecc71";
      case 2:
        return "#8fce00";
      case 3:
        return "#f1c40f";
      case 4:
        return "#e67e22";
      default:
        return "#e74c3c";
    }
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
    detailLabel.setStyle(String.format("-fx-font-size: %.0fpx;", detailFontSize(size)));

    final String fixtureFontSize = String.format("-fx-font-size: %.0fpx;", detailFontSize(size));
    final String fixtureFill = nextFixture == null
        ? ""
        : String.format(" -fx-text-fill: %s;", difficultyColor(nextFixture.getDifficulty()));
    fixtureLabel.setStyle(fixtureFontSize + fixtureFill);
  }

  // The cost/points line's font size at a given shirt size - exposed so PitchView can reserve
  // breathing room below it (e.g. between the forward row and the halfway line) proportional to
  // how much space that line actually takes, rather than a fixed pixel guess.
  static double detailFontSize(final double shirtSize) {

    return Math.clamp(shirtSize * 0.10, 8, 11);
  }

  private static String shirtUrl(final Player player) {

    final String goalkeeperSuffix = player.getPosition() == Position.GOALKEEPER ? "_1" : "";
    return String.format(
        "https://fantasy.premierleague.com/dist/img/shirts/standard/shirt_%d%s-66.png",
        player.getTeamCode(), goalkeeperSuffix);
  }
}
