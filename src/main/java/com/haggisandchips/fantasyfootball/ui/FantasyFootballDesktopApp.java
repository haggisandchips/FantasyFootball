package com.haggisandchips.fantasyfootball.ui;

import com.haggisandchips.fantasyfootball.FantasyFootballApplication;
import com.haggisandchips.fantasyfootball.auth.FplTokenHolder;
import com.haggisandchips.fantasyfootball.auth.TokenStore;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.report.AnalysisReporter;
import com.haggisandchips.fantasyfootball.service.TeamAnalysisService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;

// JavaFX and Spring Boot each want to own the app's lifecycle, so this class bridges them: init()
// (called by the JavaFX launcher before start()) boots a web-server-less Spring context (see
// .headless(false) below for the unrelated AWT sense of "headless") to get at the same beans the
// console runner used, and stop() closes it again on window close.
@Slf4j
public class FantasyFootballDesktopApp extends Application {

  // Every size the OS might pick from for the title bar, taskbar and alt-tab switcher.
  private static final List<String> ICON_SIZES = List.of("16", "24", "32", "48", "64", "128", "256");

  // Shared by TransfersTab and KillerTeamTab's strategy dropdowns, so both read "Points per game"
  // rather than the enum's own POINTS_PER_GAME.
  static final StringConverter<Strategy> STRATEGY_LABELS = new StringConverter<>() {
    @Override
    public String toString(final Strategy strategy) {
      return switch (strategy) {
        case POINTS -> "Points";
        case FORM -> "Form";
        case POINTS_PER_GAME -> "Points per game";
      };
    }

    @Override
    public Strategy fromString(final String string) {
      throw new UnsupportedOperationException("Strategy dropdowns are selection-only");
    }
  };

  private ConfigurableApplicationContext springContext;

  @Override
  public void init() {

    springContext = new SpringApplicationBuilder(FantasyFootballApplication.class)
        .web(WebApplicationType.NONE)
        // Spring Boot defaults java.awt.headless to true; Toolkit.getSystemClipboard() (used by
        // FplLoginDialog to pre-fill a pasted token from the clipboard) throws HeadlessException
        // under that default, so it has to be turned off.
        .headless(false)
        .run(getParameters().getRaw().toArray(new String[0]));
  }

  @Override
  public void start(final Stage stage) {

    final String squadFile = springContext.getEnvironment().getProperty("fpl.my-squad-file");
    final boolean liveMode = squadFile == null || squadFile.isBlank();
    final FplTokenHolder tokenHolder = springContext.getBean(FplTokenHolder.class);
    final TokenStore tokenStore = springContext.getBean(TokenStore.class);

    final Tab mySquadTab = new Tab("My Squad", loadingPane());
    mySquadTab.setClosable(false);

    final TransfersTab transfersTab = new TransfersTab();
    final Tab transfersTabWrapper = new Tab("Transfers", transfersTab);
    transfersTabWrapper.setClosable(false);

    final TeamAnalysisService teamAnalysisService = springContext.getBean(TeamAnalysisService.class);
    final AnalysisReporter analysisReporter = springContext.getBean(AnalysisReporter.class);

    final KillerTeamTab killerTeamTab = new KillerTeamTab();
    final Tab killerTeamTabWrapper = new Tab("Killer Team", killerTeamTab);
    killerTeamTabWrapper.setClosable(false);

    final TabPane tabPane = new TabPane(mySquadTab, transfersTabWrapper, killerTeamTabWrapper);
    VBox.setVgrow(tabPane, Priority.ALWAYS);

    final Runnable reload = () ->
        loadSquadThenTransfers(stage, teamAnalysisService, analysisReporter, mySquadTab, transfersTab, killerTeamTab);

    final VBox root = new VBox(tabPane);
    if (liveMode) {
      root.getChildren().add(0, buildMenuBar(stage, tokenHolder, tokenStore, reload));
    }

    final Scene scene = new Scene(root, 1140, 1020);
    scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());

    stage.setTitle("Fantasy Football");
    stage.setScene(scene);
    stage.getIcons().addAll(loadIcons());
    stage.show();

    if (liveMode && tokenHolder.get().isEmpty()) {
      FplLoginDialog.showAndCaptureToken(stage).ifPresent(token -> {
        tokenHolder.set(token);
        tokenStore.save(token);
      });
    }

    reload.run();
  }

  // Only added in live mode (FPL_MY_SQUAD_FILE unset) - in stub mode there's no live account to
  // log in to.
  private MenuBar buildMenuBar(
      final Stage stage, final FplTokenHolder tokenHolder, final TokenStore tokenStore, final Runnable reload) {

    final MenuItem loginItem = new MenuItem("Log in to FPL...");
    final MenuItem logoutItem = new MenuItem("Log out");
    final MenuItem reloadItem = new MenuItem("Reload");

    final Runnable refreshMenuState = () -> {
      final boolean loggedIn = tokenHolder.get().isPresent();
      loginItem.setDisable(loggedIn);
      logoutItem.setDisable(!loggedIn);
    };
    refreshMenuState.run();

    loginItem.setOnAction(event -> FplLoginDialog.showAndCaptureToken(stage).ifPresentOrElse(
        token -> {
          tokenHolder.set(token);
          tokenStore.save(token);
          refreshMenuState.run();
          reload.run();
        },
        refreshMenuState));

    logoutItem.setOnAction(event -> {
      tokenHolder.clear();
      tokenStore.clear();
      stage.setTitle("Fantasy Football");
      refreshMenuState.run();
      reload.run();
    });

    // Re-fetches the player pool and live squad and refreshes My Squad and Transfers against them -
    // exactly what a submitted transfer/substitution already triggers (see loadMySquad), just
    // invokable on demand. Killer Team is deliberately left alone here too: its own init() only
    // ever refreshes its internal mySquad reference on a re-call, never resetting the strategy/
    // budget/cache the user has already chosen there.
    reloadItem.setOnAction(event -> reload.run());

    return new MenuBar(new Menu("Account", null, loginItem, logoutItem, new SeparatorMenuItem(), reloadItem));
  }

  private void loadSquadThenTransfers(
      final Stage stage, final TeamAnalysisService teamAnalysisService, final AnalysisReporter analysisReporter,
      final Tab mySquadTab, final TransfersTab transfersTab, final KillerTeamTab killerTeamTab) {

    final Task<List<Player>> allPlayersTask = new Task<>() {
      @Override
      protected List<Player> call() throws Exception {

        return teamAnalysisService.fetchAllPlayers();
      }
    };

    allPlayersTask.setOnSucceeded(event -> {
      final List<Player> allPlayers = allPlayersTask.getValue();
      analysisReporter.reportAllPlayers(allPlayers);

      loadMySquad(stage, teamAnalysisService, analysisReporter, mySquadTab, transfersTab, killerTeamTab, allPlayers);
    });

    allPlayersTask.setOnFailed(event -> {
      final String message = describe(allPlayersTask.getException());
      mySquadTab.setContent(errorPane(message));
      transfersTab.showError(message);
      killerTeamTab.showError(message);
    });

    Thread.ofVirtual().name("fetch-players").start(allPlayersTask);
  }

  private void loadMySquad(
      final Stage stage, final TeamAnalysisService teamAnalysisService, final AnalysisReporter analysisReporter,
      final Tab mySquadTab, final TransfersTab transfersTab, final KillerTeamTab killerTeamTab, final List<Player> allPlayers) {

    final Task<Squad> squadTask = new Task<>() {
      @Override
      protected Squad call() throws Exception {

        return teamAnalysisService.fetchMySquad(allPlayers);
      }
    };

    squadTask.setOnSucceeded(event -> {
      final Squad mySquad = squadTask.getValue();
      analysisReporter.reportSquad(mySquad);

      if (mySquad.getTeamName() != null) {
        stage.setTitle("Fantasy Football - " + mySquad.getTeamName());
      }

      // A submitted transfer or substitution - from the My Squad tab's own "Make Substitution", the
      // Transfers tab, or the Killer Team tab's "Use This Team" - changes the live squad (picks,
      // bank, free transfers) underneath every tab, so they all share this one
      // re-fetch-and-recalculate-from-scratch callback rather than each patching in-memory state
      // themselves.
      final Runnable onTransferExecuted = () -> loadMySquad(
          stage, teamAnalysisService, analysisReporter, mySquadTab, transfersTab, killerTeamTab, allPlayers);

      mySquadTab.setContent(new MySquadTab(mySquad, teamAnalysisService, onTransferExecuted));

      // Needs mySquad (for the default budget - see KillerTeamTab.availableFunds()), so wired here
      // rather than as soon as allPlayers is available. A no-op after the first call (a submitted
      // transfer re-runs loadMySquad, but the killer team's own strategy/budget selection and cache
      // shouldn't be reset just because the live squad changed elsewhere) - though it does still
      // refresh its own mySquad reference every call, so "Use This Team" always computes deltas
      // against the current squad even when the rest of its state is left alone.
      killerTeamTab.init(teamAnalysisService, allPlayers, mySquad, analysisReporter::reportKillerTeam, onTransferExecuted);

      // Needs mySquad for the same reason killerTeamTab.init() does above - and is likewise a no-op
      // after the first call, refreshing mySquad/onTransferExecuted but leaving the tab's own
      // strategy selection and per-strategy cache alone.
      transfersTab.init(teamAnalysisService, allPlayers, mySquad, analysisReporter::reportTransferSuggestions, onTransferExecuted);
    });

    squadTask.setOnFailed(event -> {
      final String message = describe(squadTask.getException());
      mySquadTab.setContent(errorPane(message));
      transfersTab.showError(message);
    });

    Thread.ofVirtual().name("fetch-squad").start(squadTask);
  }

  @Override
  public void stop() {

    springContext.close();
    Platform.exit();
  }

  static String describe(final Throwable error) {

    log.error("Analysis step failed", error);
    return error.getMessage();
  }

  private static List<Image> loadIcons() {

    return ICON_SIZES.stream()
        .map(size -> new Image(FantasyFootballDesktopApp.class.getResourceAsStream(
            "/icons/icon_" + size + "x" + size + ".png")))
        .toList();
  }

  private static StackPane loadingPane() {

    final StackPane pane = new StackPane(new ProgressIndicator());
    pane.setAlignment(Pos.CENTER);
    pane.setPrefSize(1140, 1020);
    return pane;
  }

  // Bound directly to the task's own progress/message properties (rather than polling or manually
  // marshalling updates onto the FX thread) - Task already does that marshalling for us. Shared by
  // TransfersTab and KillerTeamTab (package-private, not private - KillerTeamTab uses it too).
  static StackPane progressPane(final Task<?> task) {

    final ProgressBar progressBar = new ProgressBar();
    progressBar.progressProperty().bind(task.progressProperty());
    progressBar.setPrefWidth(360);

    final Label messageLabel = new Label();
    messageLabel.textProperty().bind(task.messageProperty());

    final VBox content = new VBox(12, progressBar, messageLabel);
    content.setAlignment(Pos.CENTER);

    final StackPane pane = new StackPane(content);
    pane.setAlignment(Pos.CENTER);
    pane.setPrefSize(1140, 1020);
    return pane;
  }

  private static StackPane errorPane(final String message) {

    return new StackPane(new Label("Failed to load data: " + message));
  }
}
