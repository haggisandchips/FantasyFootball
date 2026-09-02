package com.haggisandchips.fantasyfootball.ui;

import com.haggisandchips.fantasyfootball.Controls;
import com.haggisandchips.fantasyfootball.domain.Fixture;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Position;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;

// A player rendered as their real club shirt (fetched from FPL's own CDN, keyed by team_code -
// see Player.teamCode), their name, a cost/points line, a form/points-per-game line, and a fourth
// line for their next fixture - used both positioned on the pitch (where setShirtSize() lets
// PitchView shrink/grow it as the window resizes) and in ordinary flow layouts (TransfersTab's
// suggestion pairs and injured list, always at MAX_SHIRT_SIZE). The killer team list renders its
// own cards, not PitchPlayer.
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

  // Just enough to keep the shirt image and clamp math sane (positive, non-zero) at the smallest
  // conceivable pitch size - not a "readable" floor. Cards are meant to keep shrinking with whatever
  // room the pitch is given (see PitchView, which no longer enforces its own minimum size either),
  // and cardPadding() being proportional rather than a fixed pixel amount is what actually keeps
  // rows from overlapping at any size, not this floor.
  static final double MIN_SHIRT_SIZE = 10;

  // Extra width/height beyond the shirt itself for the name + detail lines and breathing room, as a
  // fraction of the shirt's own size (30px at MAX_SHIRT_SIZE=100, matching how this used to be a flat
  // 30px constant) rather than a fixed pixel amount - a fixed amount doesn't shrink as the shirt
  // does, so at a small enough pitch size it would eventually dominate cardWidth/cardHeight and cause
  // rows to overlap even though shirtSize itself had plenty of room left to shrink further.
  private static final double CARD_PADDING_FRACTION = 0.3;

  static double cardPadding(final double shirtSize) {

    return shirtSize * CARD_PADDING_FRACTION;
  }

  static final double DEFAULT_CARD_WIDTH = MAX_SHIRT_SIZE + cardPadding(MAX_SHIRT_SIZE);

  // Gap between the shirt, name, detail and (where shown) fixture labels, as a fraction of shirt
  // size (2px at MAX_SHIRT_SIZE=100) rather than a fixed pixel amount - for the same reason
  // cardPadding() is proportional (see above). Exposed so PitchView can budget the same amount of
  // extra height it reserves for the fixture line's own font size.
  private static final double LABEL_SPACING_FRACTION = 0.02;

  static double labelSpacing(final double shirtSize) {

    return shirtSize * LABEL_SPACING_FRACTION;
  }

  private final ImageView shirt;

  private final Label nameLabel;

  private final Label detailLabel;

  private final Label statsLabel;

  private final TextFlow fixtureLabel;

  // Parallel to fixtureLabel's own children, in order - each entry is the colour that child's text
  // was given (null for a segment that isn't colour-coded, e.g. the "; " separator between two
  // fixtures, or "No fixture") - kept separately because Text has no getter back for a colour set
  // via an inline style string, and setShirtSize needs to reapply it alongside the font size on
  // every resize without clobbering it.
  private final List<String> fixtureSegmentColors;

  private final List<Fixture> nextFixtures;

  static PitchPlayer of(final Player player) {

    return new PitchPlayer(player, List.of(), null, false, false);
  }

  // Used by PitchView for the starting XI - badges/arrow carry the OptimalElevenSelector overlay
  // (see PitchView.captaincyBadges/arrowFor); an empty list/null arrow renders neither.
  static PitchPlayer of(final Player player, final List<CaptaincyBadge> badges, final Arrow arrow) {

    return new PitchPlayer(player, badges, arrow, false, true);
  }

  // Substitutes aren't shown in a formation row, so their position isn't otherwise obvious -
  // shown as part of the detail line instead of a separate badge to keep the card compact.
  static PitchPlayer ofSubstitute(final Player player, final List<CaptaincyBadge> badges, final Arrow arrow) {

    return new PitchPlayer(player, badges, arrow, true, true);
  }

  private PitchPlayer(
      final Player player, final List<CaptaincyBadge> badges, final Arrow arrow, final boolean showPosition,
      final boolean showFixtureMultiplier) {

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

    statsLabel = new Label(String.format("Form %.1f · PPG %.1f", player.getForm(), player.getPointsPerGame()));
    statsLabel.getStyleClass().add("player-detail");

    nextFixtures = player.getNextFixtures();

    // Raw multiplier figure appended for the My Squad pitch only (see the showFixtureMultiplier
    // factory overloads above) - lets the fixture-difficulty adjustment (Player.
    // getFixtureDifficultyMultiplier, tuned via Controls.FIXTURE_DIFFICULTY_*) be eyeballed against
    // real fixtures directly on the card, not just inferred from lineup/captaincy order.
    final Double multiplier = showFixtureMultiplier && !nextFixtures.isEmpty()
        ? player.getFixtureDifficultyMultiplier()
        : null;
    final FixtureDisplay fixtureDisplay = buildFixtureDisplay(nextFixtures, multiplier);
    fixtureLabel = fixtureDisplay.flow();
    fixtureSegmentColors = fixtureDisplay.segmentColors();
    // Unlike the Labels above, a TextFlow doesn't shrink to its own content width - VBox's default
    // fillWidth stretches it to the full card width, so without this its (left-aligned by default)
    // text sits at the card's left edge instead of centred like every other line.
    fixtureLabel.setTextAlignment(TextAlignment.CENTER);

    setAlignment(Pos.CENTER);
    getChildren().addAll(shirtPane, nameLabel, detailLabel, statsLabel, fixtureLabel);

    setShirtSize(MAX_SHIRT_SIZE);
  }

  // fixtureLabel's built content, plus the colour (or null for an uncoloured segment, e.g. the
  // "; " separator between two fixtures) each of its children was given, in the same order -
  // returned together since setShirtSize needs both to reapply colour alongside font size on every
  // resize (see fixtureSegmentColors' own comment).
  private record FixtureDisplay(TextFlow flow, List<String> segmentColors) {
  }

  // "Opponent (H/A)" per fixture, space-separated for a double gameweek (a semicolon read poorly in
  // its default uncoloured black against the pitch), each individually coloured
  // by its own FDR (not a single "worst of the group" colour for the whole line - two fixtures of
  // genuinely different difficulty deserve genuinely different colours) - plus, when multiplier is
  // non-null, the raw fixture-difficulty multiplier figure (see Player.getFixtureDifficultyMultiplier)
  // appended as its own coloured segment. A placeholder covers a team with no fixture at all (a
  // blank gameweek) - see Player.nextFixtures.
  private static FixtureDisplay buildFixtureDisplay(final List<Fixture> fixtures, final Double multiplier) {

    final TextFlow flow = new TextFlow();
    final List<String> colors = new ArrayList<>();

    if (fixtures.isEmpty()) {
      addSegment(flow, colors, "No fixture", null);
      return new FixtureDisplay(flow, colors);
    }

    for (int i = 0; i < fixtures.size(); i++) {
      if (i > 0) {
        addSegment(flow, colors, " ", null);
      }

      final Fixture fixture = fixtures.get(i);
      final String text = String.format("%s (%s)", fixture.getOpponent(), fixture.isHome() ? "H" : "A");
      addSegment(flow, colors, text, difficultyColor(fixture.getDifficulty()));
    }

    if (multiplier != null) {
      addSegment(flow, colors, String.format(" ×%.2f", multiplier), multiplierColor(multiplier, fixtures.size()));
    }

    return new FixtureDisplay(flow, colors);
  }

  private static void addSegment(
      final TextFlow flow, final List<String> colors, final String text, final String color) {

    final Text node = new Text(text);
    node.getStyleClass().add("player-fixture");
    flow.getChildren().add(node);
    colors.add(color);
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

  // Same 5-colour scale as difficultyColor, but keyed to how favourable the fixture-difficulty
  // multiplier is (high = green/favourable, low = red/unfavourable) rather than to a raw FDR
  // number. Averaged per fixture first so a double gameweek's summed multiplier (which can run past
  // 2.0 - see Player.getFixtureDifficultyMultiplier) lands on the same
  // [FIXTURE_DIFFICULTY_MULTIPLIER_MIN, MAX] scale a single fixture's own multiplier does.
  private static String multiplierColor(final double multiplier, final int fixtureCount) {

    final double perFixture = multiplier / fixtureCount;
    final double min = Controls.FIXTURE_DIFFICULTY_MULTIPLIER_MIN;
    final double band = (Controls.FIXTURE_DIFFICULTY_MULTIPLIER_MAX - min) / 5;

    if (perFixture < min + band) {
      return "#e74c3c";
    } else if (perFixture < min + 2 * band) {
      return "#e67e22";
    } else if (perFixture < min + 3 * band) {
      return "#f1c40f";
    } else if (perFixture < min + 4 * band) {
      return "#8fce00";
    } else {
      return "#2ecc71";
    }
  }

  // Rescales the shirt image and card width (and, modestly, the text) to the given shirt size -
  // used by PitchView to shrink cards as the pitch itself shrinks, without reloading the image.
  void setShirtSize(final double shirtSize) {

    final double size = Math.clamp(shirtSize, MIN_SHIRT_SIZE, MAX_SHIRT_SIZE);

    shirt.setFitWidth(size);
    shirt.setFitHeight(size);

    setSpacing(labelSpacing(size));

    final double cardWidth = size + cardPadding(size);
    setPrefWidth(cardWidth);
    setMaxWidth(cardWidth);

    nameLabel.setStyle(String.format("-fx-font-size: %.0fpx;", Math.clamp(size * 0.13, 9, 13)));
    detailLabel.setStyle(String.format("-fx-font-size: %.0fpx;", detailFontSize(size)));
    statsLabel.setStyle(String.format("-fx-font-size: %.0fpx;", detailFontSize(size)));

    // Each fixture segment keeps its own colour (assigned once, in buildFixtureDisplay) - only the
    // font size changes here, reapplied per child alongside that colour since Text has no getter
    // back for a colour set via an inline style string (see fixtureSegmentColors).
    final String fixtureFontSize = String.format("-fx-font-size: %.0fpx;", detailFontSize(size));
    final List<Node> segments = fixtureLabel.getChildren();
    for (int i = 0; i < segments.size(); i++) {
      final String color = fixtureSegmentColors.get(i);
      final String fill = color == null ? "" : String.format(" -fx-fill: %s;", color);
      segments.get(i).setStyle(fixtureFontSize + fill);
    }
  }

  // The cost/points line's font size at a given shirt size - exposed so PitchView can reserve
  // breathing room below it (e.g. between the forward row and the halfway line) proportional to
  // how much space that line actually takes, rather than a fixed pixel guess. The lower bound here
  // is deliberately tiny (not a "still legible" floor, MIN_SHIRT_SIZE's own comment explains why) -
  // an 8px floor sat close enough to the unclamped value at MAX_SHIRT_SIZE (10) that it was already
  // active for most of the shrinking range, turning this back into a fixed pixel amount exactly
  // where PitchView's overlap math needs it to keep shrinking.
  static double detailFontSize(final double shirtSize) {

    return Math.clamp(shirtSize * 0.10, 2, 11);
  }

  private static String shirtUrl(final Player player) {

    final String goalkeeperSuffix = player.getPosition() == Position.GOALKEEPER ? "_1" : "";
    return String.format(
        "https://fantasy.premierleague.com/dist/img/shirts/standard/shirt_%d%s-66.png",
        player.getTeamCode(), goalkeeperSuffix);
  }
}
