package com.haggisandchips.fantasyfootball.ui;

import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.domain.Squad;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// A custom-laid-out Region (not a fixed-size Pane) so the pitch always fills exactly the space
// its container gives it - no scrolling. layoutChildren() recomputes every marking and player
// position from the region's *current* width/height on every resize, using the same fractions of
// a fixed design ratio (ASPECT_RATIO) so the pitch scales without distorting - it grows to fill
// the available height, then width is derived from that (capped to the available width, in which
// case height is derived back down) rather than stretching width and height independently.
class PitchView extends Region {

  private static final double ASPECT_RATIO = 900.0 / 830.0;

  private static final double SIDE_MARGIN_FRACTION = 20.0 / 900.0;

  private static final double GOAL_LINE_Y_FRACTION = 10.0 / 830.0;

  private static final double HALFWAY_LINE_Y_FRACTION = 620.0 / 830.0;

  // GOALKEEPER row position (fraction of height, from the top). DEFENDER/MIDFIELDER are NOT fixed
  // fractions - they're spaced evenly between this and FORWARD's position each layout pass (see
  // layoutChildren()), otherwise a fixed CARD_HEIGHT (pixels) subtracted from a height-scaled
  // halfway line moves FORWARD independently of DEFENDER/MIDFIELDER, which can overlap or leave
  // uneven gaps depending on how tall the region actually is.
  private static final double GOALKEEPER_Y_FRACTION = 25.0 / 830.0;

  private static final double CENTER_CIRCLE_RADIUS_FRACTION = 72.0 / 900.0;

  private static final double PENALTY_BOX_WIDTH_FRACTION = 360.0 / 900.0;

  private static final double PENALTY_BOX_HEIGHT_FRACTION = 175.0 / 830.0;

  private static final double SIX_YARD_BOX_WIDTH_FRACTION = 160.0 / 900.0;

  private static final double SIX_YARD_BOX_HEIGHT_FRACTION = 75.0 / 830.0;

  private static final double GOAL_WIDTH_FRACTION = 80.0 / 900.0;

  private static final double GOAL_HEIGHT_FRACTION = 10.0 / 830.0;

  private static final double SUBS_ROW_MARGIN_FRACTION = 50.0 / 900.0;

  private static final double SUBS_LABEL_GAP_FRACTION = 10.0 / 830.0;

  // Shirt size at the reference width (900) - scaled proportionally as the pitch shrinks/grows,
  // clamped to PitchPlayer's own min/max.
  private static final double SHIRT_SIZE_FRACTION = PitchPlayer.MAX_SHIRT_SIZE / 900.0;

  private final Squad squad;

  private final Line leftSideline = line();

  private final Line rightSideline = line();

  private final Line goalLine = line();

  private final Line halfwayLine = line();

  private final Arc centerCircle = new Arc();

  private final Rectangle penaltyBox = markingRect();

  private final Rectangle sixYardBox = markingRect();

  private final Rectangle goal = markingRect();

  private final Map<Position, List<PitchPlayer>> startingNodesByPosition = new EnumMap<>(Position.class);

  private final List<PitchPlayer> substituteNodes = new ArrayList<>();

  private final Label substitutesLabel = new Label("SUBSTITUTES");

  PitchView(final Squad squad) {

    this.squad = squad;
    getStyleClass().add("pitch");
    setMinSize(320, 260);

    centerCircle.setType(ArcType.OPEN);
    centerCircle.getStyleClass().add("pitch-line");
    substitutesLabel.getStyleClass().add("dugout-label");

    getChildren().addAll(
        leftSideline, rightSideline, goalLine, halfwayLine, centerCircle, penaltyBox, sixYardBox, goal,
        substitutesLabel);

    buildPlayerNodes();
  }

  private static Line line() {

    final Line line = new Line();
    line.getStyleClass().add("pitch-line");
    return line;
  }

  private static Rectangle markingRect() {

    final Rectangle rectangle = new Rectangle();
    rectangle.getStyleClass().add("pitch-line");
    return rectangle;
  }

  private void buildPlayerNodes() {

    final Map<Position, List<Player>> byPosition =
        squad.getStartingEleven().stream().collect(Collectors.groupingBy(Player::getPosition));

    for (final Position position : Position.values()) {
      final List<PitchPlayer> nodes = new ArrayList<>();
      for (final Player player : byPosition.getOrDefault(position, List.of())) {
        final PitchPlayer node = PitchPlayer.of(player, captainBadge(player));
        nodes.add(node);
        getChildren().add(node);
      }
      startingNodesByPosition.put(position, nodes);
    }

    for (final Player player : squad.getSubstitutes()) {
      final PitchPlayer node = PitchPlayer.ofSubstitute(player);
      substituteNodes.add(node);
      getChildren().add(node);
    }
  }

  private String captainBadge(final Player player) {

    if (player == squad.getCaptain()) {
      return "C";
    } else if (player == squad.getViceCaptain()) {
      return "V";
    } else {
      return null;
    }
  }

  @Override
  protected void layoutChildren() {

    final double availableWidth = getWidth();
    final double availableHeight = getHeight();

    double width = availableHeight * ASPECT_RATIO;
    double height = availableHeight;
    if (width > availableWidth) {
      width = availableWidth;
      height = availableWidth / ASPECT_RATIO;
    }

    final double offsetX = (availableWidth - width) / 2;
    final double offsetY = (availableHeight - height) / 2;

    final double sideMargin = width * SIDE_MARGIN_FRACTION;
    final double left = offsetX + sideMargin;
    final double right = offsetX + width - sideMargin;
    final double goalLineY = offsetY + height * GOAL_LINE_Y_FRACTION;
    final double halfwayLineY = offsetY + height * HALFWAY_LINE_Y_FRACTION;
    final double bottomY = offsetY + height;
    final double centerX = offsetX + width / 2;

    // Sidelines run the full height, into the substitutes area below the halfway line - but no
    // other markings (goal line, boxes, halfway line itself) belong down there.
    leftSideline.setStartX(left);
    leftSideline.setStartY(goalLineY);
    leftSideline.setEndX(left);
    leftSideline.setEndY(bottomY);

    rightSideline.setStartX(right);
    rightSideline.setStartY(goalLineY);
    rightSideline.setEndX(right);
    rightSideline.setEndY(bottomY);

    goalLine.setStartX(left);
    goalLine.setStartY(goalLineY);
    goalLine.setEndX(right);
    goalLine.setEndY(goalLineY);

    halfwayLine.setStartX(left);
    halfwayLine.setStartY(halfwayLineY);
    halfwayLine.setEndX(right);
    halfwayLine.setEndY(halfwayLineY);

    final double circleRadius = width * CENTER_CIRCLE_RADIUS_FRACTION;
    centerCircle.setCenterX(centerX);
    centerCircle.setCenterY(halfwayLineY);
    centerCircle.setRadiusX(circleRadius);
    centerCircle.setRadiusY(circleRadius);
    centerCircle.setStartAngle(0);
    centerCircle.setLength(180);

    final double penaltyBoxWidth = width * PENALTY_BOX_WIDTH_FRACTION;
    final double penaltyBoxHeight = height * PENALTY_BOX_HEIGHT_FRACTION;
    penaltyBox.setX(centerX - penaltyBoxWidth / 2);
    penaltyBox.setY(goalLineY);
    penaltyBox.setWidth(penaltyBoxWidth);
    penaltyBox.setHeight(penaltyBoxHeight);

    final double sixYardWidth = width * SIX_YARD_BOX_WIDTH_FRACTION;
    final double sixYardHeight = height * SIX_YARD_BOX_HEIGHT_FRACTION;
    sixYardBox.setX(centerX - sixYardWidth / 2);
    sixYardBox.setY(goalLineY);
    sixYardBox.setWidth(sixYardWidth);
    sixYardBox.setHeight(sixYardHeight);

    final double goalWidth = width * GOAL_WIDTH_FRACTION;
    final double goalHeight = height * GOAL_HEIGHT_FRACTION;
    goal.setX(centerX - goalWidth / 2);
    goal.setY(goalLineY - goalHeight);
    goal.setWidth(goalWidth);
    goal.setHeight(goalHeight);

    // Shirt/card size scales with the pitch itself, so cards shrink along with everything else
    // when the window gets smaller instead of staying a fixed pixel size.
    final double shirtSize = Math.clamp(
        width * SHIRT_SIZE_FRACTION, PitchPlayer.MIN_SHIRT_SIZE, PitchPlayer.MAX_SHIRT_SIZE);
    final double cardWidth = shirtSize + PitchPlayer.CARD_PADDING;
    final double cardHeight = shirtSize + PitchPlayer.CARD_PADDING;

    for (final List<PitchPlayer> nodes : startingNodesByPosition.values()) {
      nodes.forEach(node -> node.setShirtSize(shirtSize));
    }
    substituteNodes.forEach(node -> node.setShirtSize(shirtSize));

    // GOALKEEPER and FORWARD are anchored (goal line and halfway line respectively); DEFENDER and
    // MIDFIELDER split the space between them into three equal gaps, so spacing stays even and
    // FORWARD can never overlap MIDFIELDER regardless of how tall the region actually is.
    final double goalkeeperY = offsetY + height * GOALKEEPER_Y_FRACTION;
    // Leaves a little breathing room between the forward row and the halfway line, so the cost/
    // points line underneath the shirt doesn't sit right on top of it - half that line's own
    // height is enough.
    final double forwardBottomGap = PitchPlayer.detailFontSize(shirtSize) / 2.0;
    final double forwardY = halfwayLineY - cardHeight - forwardBottomGap;
    final double rowGap = (forwardY - goalkeeperY) / 3.0;

    final double[] rowY = { goalkeeperY, goalkeeperY + rowGap, goalkeeperY + 2 * rowGap, forwardY };

    final Position[] positions = Position.values();
    for (int i = 0; i < positions.length; i++) {
      placeRow(startingNodesByPosition.get(positions[i]), rowY[i], offsetX, width, cardWidth);
    }

    final double subsLabelY = halfwayLineY + height * SUBS_LABEL_GAP_FRACTION;
    substitutesLabel.autosize();
    substitutesLabel.relocate(centerX - substitutesLabel.getWidth() / 2, subsLabelY);

    final double subsRowY = subsLabelY + substitutesLabel.getHeight() + height * 0.015;
    final double subsMargin = width * SUBS_ROW_MARGIN_FRACTION;
    placeRow(substituteNodes, subsRowY, offsetX + subsMargin, width - 2 * subsMargin, cardWidth);
  }

  private void placeRow(
      final List<PitchPlayer> nodes, final double y, final double rowLeft, final double rowWidth,
      final double cardWidth) {

    final int count = nodes.size();
    if (count == 0) {
      return;
    }

    final double spacing = rowWidth / (count + 1);
    for (int i = 0; i < count; i++) {
      final PitchPlayer node = nodes.get(i);
      // A custom Region (unlike Pane) doesn't auto-size children to their preferred size, so a
      // node that's only ever relocate()'d stays at its initial 0x0 layout size - which is why
      // names were rendering as an ellipsis and shirts appeared to be in the wrong place.
      node.autosize();
      node.relocate(rowLeft + spacing * (i + 1) - cardWidth / 2, y);
    }
  }
}
