package com.haggisandchips.fantasyfootball.ui;

import com.haggisandchips.fantasyfootball.FantasyFootballApplication;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.Team;
import com.haggisandchips.fantasyfootball.domain.TransferSuggestion;
import com.haggisandchips.fantasyfootball.report.AnalysisReporter;
import com.haggisandchips.fantasyfootball.service.TeamAnalysisService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;
import java.util.Map;

// JavaFX and Spring Boot each want to own the app's lifecycle, so this class bridges them: init()
// (called by the JavaFX launcher before start()) boots a headless Spring context to get at the
// same beans the console runner used, and stop() closes it again on window close.
@Slf4j
public class FantasyFootballDesktopApp extends Application {

  private ConfigurableApplicationContext springContext;

  @Override
  public void init() {

    springContext = new SpringApplicationBuilder(FantasyFootballApplication.class)
        .web(WebApplicationType.NONE)
        .run(getParameters().getRaw().toArray(new String[0]));
  }

  @Override
  public void start(final Stage stage) {

    final Tab mySquadTab = new Tab("My Squad", loadingPane());
    mySquadTab.setClosable(false);

    final Tab transfersTab = new Tab("Transfers", loadingPane());
    transfersTab.setClosable(false);

    final TeamAnalysisService teamAnalysisService = springContext.getBean(TeamAnalysisService.class);
    final AnalysisReporter analysisReporter = springContext.getBean(AnalysisReporter.class);

    final KillerTeamTab killerTeamTab = new KillerTeamTab();
    final Tab killerTeamTabWrapper = new Tab("Killer Team", killerTeamTab);
    killerTeamTabWrapper.setClosable(false);

    final TabPane tabPane = new TabPane(mySquadTab, transfersTab, killerTeamTabWrapper);

    final Scene scene = new Scene(tabPane, 1140, 1020);
    scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());

    stage.setTitle("Fantasy Football");
    stage.setScene(scene);
    stage.show();

    loadSquadThenTransfers(teamAnalysisService, analysisReporter, mySquadTab, transfersTab, killerTeamTab);
  }

  private void loadSquadThenTransfers(
      final TeamAnalysisService teamAnalysisService, final AnalysisReporter analysisReporter,
      final Tab mySquadTab, final Tab transfersTab, final KillerTeamTab killerTeamTab) {

    final Task<List<Player>> allPlayersTask = new Task<>() {
      @Override
      protected List<Player> call() throws Exception {

        return teamAnalysisService.fetchAllPlayers();
      }
    };

    allPlayersTask.setOnSucceeded(event -> {
      final List<Player> allPlayers = allPlayersTask.getValue();
      analysisReporter.reportAllPlayers(allPlayers);

      wireKillerTeamCalculation(teamAnalysisService, analysisReporter, killerTeamTab, allPlayers);
      loadMySquad(teamAnalysisService, analysisReporter, mySquadTab, transfersTab, allPlayers);
    });

    allPlayersTask.setOnFailed(event -> {
      final String message = describe(allPlayersTask.getException());
      mySquadTab.setContent(errorPane(message));
      transfersTab.setContent(errorPane(message));
      killerTeamTab.showError(message);
    });

    Thread.ofVirtual().name("fetch-players").start(allPlayersTask);
  }

  private void loadMySquad(
      final TeamAnalysisService teamAnalysisService, final AnalysisReporter analysisReporter,
      final Tab mySquadTab, final Tab transfersTab, final List<Player> allPlayers) {

    final Task<Squad> squadTask = new Task<>() {
      @Override
      protected Squad call() throws Exception {

        return teamAnalysisService.fetchMySquad(allPlayers);
      }
    };

    squadTask.setOnSucceeded(event -> {
      final Squad mySquad = squadTask.getValue();
      analysisReporter.reportSquad(mySquad);
      mySquadTab.setContent(new MySquadTab(mySquad));

      loadTransferSuggestions(teamAnalysisService, analysisReporter, transfersTab, mySquad, allPlayers);
    });

    squadTask.setOnFailed(event -> {
      final String message = describe(squadTask.getException());
      mySquadTab.setContent(errorPane(message));
      transfersTab.setContent(errorPane(message));
    });

    Thread.ofVirtual().name("fetch-squad").start(squadTask);
  }

  private void loadTransferSuggestions(
      final TeamAnalysisService teamAnalysisService, final AnalysisReporter analysisReporter,
      final Tab transfersTab, final Squad mySquad, final List<Player> allPlayers) {

    final Task<Map<Strategy, Map<Integer, List<TransferSuggestion>>>> transfersTask = new Task<>() {
      @Override
      protected Map<Strategy, Map<Integer, List<TransferSuggestion>>> call() {

        return teamAnalysisService.calculateTransferSuggestions(mySquad, allPlayers);
      }
    };

    transfersTask.setOnSucceeded(event -> {
      final Map<Strategy, Map<Integer, List<TransferSuggestion>>> transferSuggestions = transfersTask.getValue();
      analysisReporter.reportTransferSuggestions(transferSuggestions);
      transfersTab.setContent(new TransfersTab(mySquad, transferSuggestions));
    });

    transfersTask.setOnFailed(event ->
        transfersTab.setContent(errorPane(describe(transfersTask.getException()))));

    Thread.ofVirtual().name("calculate-transfers").start(transfersTask);
  }

  private void wireKillerTeamCalculation(
      final TeamAnalysisService teamAnalysisService, final AnalysisReporter analysisReporter,
      final KillerTeamTab killerTeamTab, final List<Player> allPlayers) {

    killerTeamTab.setOnCalculate(() -> {
      killerTeamTab.showLoading();

      final Task<Map<Strategy, Team>> killerTeamTask = new Task<>() {
        @Override
        protected Map<Strategy, Team> call() {

          return teamAnalysisService.calculateKillerTeams(allPlayers);
        }
      };

      killerTeamTask.setOnSucceeded(event -> {
        final Map<Strategy, Team> killerTeams = killerTeamTask.getValue();
        analysisReporter.reportKillerTeams(killerTeams);
        killerTeamTab.showResult(killerTeams);
      });

      killerTeamTask.setOnFailed(event -> killerTeamTab.showError(describe(killerTeamTask.getException())));

      Thread.ofVirtual().name("calculate-killer-team").start(killerTeamTask);
    });
  }

  @Override
  public void stop() {

    springContext.close();
    Platform.exit();
  }

  private static String describe(final Throwable error) {

    log.error("Analysis step failed", error);
    return error.getMessage();
  }

  private static StackPane loadingPane() {

    final StackPane pane = new StackPane(new ProgressIndicator());
    pane.setAlignment(Pos.CENTER);
    pane.setPrefSize(1140, 1020);
    return pane;
  }

  private static StackPane errorPane(final String message) {

    return new StackPane(new Label("Failed to load data: " + message));
  }
}
