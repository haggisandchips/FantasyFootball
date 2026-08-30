package com.haggisandchips.fantasyfootball.ui;

import com.haggisandchips.fantasyfootball.domain.Squad;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

// No ScrollPane here deliberately - the pitch is meant to fill whatever space is left below the
// header exactly (see PitchView), not scroll.
class MySquadTab extends BorderPane {

  MySquadTab(final Squad squad) {

    setTop(header(squad));
    setCenter(new PitchView(squad));
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
}
