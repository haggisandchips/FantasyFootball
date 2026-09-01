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

import java.util.List;

// A player rendered as their real club shirt (fetched from FPL's own CDN, keyed by team_code -
// see Player.teamCode), their name, a secondary cost/points line, and a third line for their next
// fixture - used both positioned on the pitch (where setShirtSize() lets PitchView shrink/grow it
// as the window resizes) and in ordinary flow layouts (TransfersTab's suggestion pairs and injured
// list, always at MAX_SHIRT_SIZE). The killer team list renders its own cards, not PitchPlayer.
final class PitchPlayer extends VBox {

  // A captaincy indicator - "outgoing" (red) marks the current C/V choice being replaced by
  // OptimalElevenSelector's suggestion, "outgoing" false (the existing accent green) marks either an
  // unchanged choice or a newly suggested one. Rendered in the shirt's top-right corner, centered and
  // stacked top-to-bottom above any arrow (see the constructor) so nothing overlays.
  record CaptaincyBadge(String text, boolean outgoing) {
  }

  // Which way a player is being suggested to move relative to the squad's actual current lineup -
  // DOWN for a current starter OptimalElevenSelector would bench, UP for a current substitute it
  // would start. Rendered top-right, centered and below any captaincy badges.
  enum Arrow {
    UP, DOWN
  }

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

    return new PitchPlayer(player, List.of(), null, false);
  }

  // Used by PitchView for the starting XI - badges/arrow carry the OptimalElevenSelector overlay
  // (see PitchView.captaincyBadges/arrowFor); an empty list/null arrow renders neither.
  static PitchPlayer of(final Player player, final List<CaptaincyBadge> badges, final Arrow arrow) {

    return new PitchPlayer(player, badges, arrow, false);
  }

  // Substitutes aren't shown in a formation row, so their position isn't otherwise obvious -
  // shown as part of the detail line instead of a separate badge to keep the card compact.
  static PitchPlayer ofSubstitute(final Player player, final List<CaptaincyBadge> badges, final Arrow arrow) {

    return new PitchPlayer(player, badges, arrow, true);
  }

  private PitchPlayer(
      final Player player, final List<CaptaincyBadge> badges, final Arrow arrow, final boolean showPosition) {

    // Loaded once at the highest size we'll ever display, then scaled down for display via
    // ImageView's fit properties (setShirtSize()) - backgroundLoading avoids blocking the JavaFX
    // Application Thread while each shirt downloads.
    final Image shirtImage =
        new Image(shirtUrl(player), MAX_SHIRT_SIZE, MAX_SHIRT_SIZE, true, true, true);
    shirt = new ImageView(shirtImage);
    shirt.setPreserveRatio(true);

    // Shirt defaults to centered (StackPane's default alignment); only the indicator stack gets a
    // per-child override - setting the StackPane's own alignment instead would shift the shirt too.
    final StackPane shirtPane = new StackPane(shirt);

    if (arrow != null || !badges.isEmpty()) {
      // Captaincy badges first, then the arrow below - all in one top-right column, centered on
      // each other (badges and the arrow glyph aren't the same width) so a player with both (e.g.
      // benched while also losing the captaincy) stacks cleanly instead of overlapping.
      final VBox indicatorStack = new VBox(2);
      indicatorStack.setAlignment(Pos.TOP_CENTER);
      // Without this, StackPane stretches the VBox (its default max width is unbounded) to the
      // shirt's full width, and TOP_CENTER then centers the icons across the whole shirt instead of
      // just relative to each other - shrinking it to its own content keeps the column narrow so
      // StackPane.setAlignment below can still pin it to the top-right corner.
      indicatorStack.setMaxWidth(VBox.USE_PREF_SIZE);

      for (final CaptaincyBadge captaincyBadge : badges) {
        final Label badgeLabel = new Label(captaincyBadge.text());
        badgeLabel.getStyleClass().add(captaincyBadge.outgoing() ? "captain-badge-outgoing" : "captain-badge");
        indicatorStack.getChildren().add(badgeLabel);
      }

      if (arrow != null) {
        // Solid black arrow (U+2B06/U+2B07), not the thin U+2191/U+2193 stroke - visually confirmed
        // against both pitch-grass shades to be the one that actually reads at card size.
        final Label arrowLabel = new Label(arrow == Arrow.UP ? "⬆" : "⬇");
        arrowLabel.getStyleClass().add(arrow == Arrow.UP ? "lineup-arrow-up" : "lineup-arrow-down");
        indicatorStack.getChildren().add(arrowLabel);
      }

      StackPane.setAlignment(indicatorStack, Pos.TOP_RIGHT);
      shirtPane.getChildren().add(indicatorStack);
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
