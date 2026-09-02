package com.haggisandchips.fantasyfootball.ui;

import com.haggisandchips.fantasyfootball.calculation.TransferSearchProgressListener;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Status;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.TransferSuggestion;
import com.haggisandchips.fantasyfootball.service.TeamAnalysisService;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiConsumer;

// The transfer search is a genuinely strategy-dependent combinatorial search (see TransferSelector -
// which candidates even get considered, and whether a combination counts as an improvement, both
// depend on the chosen stat), so this tab re-runs the whole search whenever the strategy dropdown
// changes, exactly like KillerTeamTab (including a cache, here just keyed by Strategy, and
// cancel-in-flight handling) - the one difference being there's no separate "Calculate" button here:
// the first search kicks off automatically as soon as init() has a squad to search from.
class TransfersTab extends ScrollPane {

  // A suggestion's card width is fixed per transfer count (every card in one refresh shows the
  // same number of transfer pairs) rather than measured, so cards are exactly the same size
  // without depending on JavaFX having already computed real layout bounds.
  private static final double PAIR_WIDTH = 2 * PitchPlayer.DEFAULT_CARD_WIDTH + 60;

  // .card's fx-padding (10 14 10 14) in app.css - the card's declared width has to cover this too,
  // on top of transferCount * PAIR_WIDTH, or the transfersRow inside is left PAIR_WIDTH's padding
  // short of its budget and wraps a pair onto its own line, needlessly tallening the card.
  private static final double CARD_HORIZONTAL_PADDING = 28;

  private final ComboBox<Strategy> strategyDropdown = new ComboBox<>(FXCollections.observableArrayList(Strategy.values()));

  // Rebuilt (not just its children replaced) on every strategy switch, alongside suggestionsBox.
  private final VBox transferSection = new VBox(16);

  private final FlowPane suggestionsBox = new FlowPane(16, 16);

  private final HBox header;

  // Every strategy's search result calculated so far, already ranked/grouped by transfer count (see
  // TeamAnalysisService.rankTransferSuggestions) - switching back to an already-seen strategy shows
  // it instantly instead of re-running the expensive search.
  private final Map<Strategy, Map<Integer, List<TransferSuggestion>>> cache = new HashMap<>();

  private TeamAnalysisService teamAnalysisService;

  private List<Player> allPlayers;

  // Refreshed on every init() call (unlike the rest of this tab's state, which is one-time - see
  // init()'s own comment) so a submitted transfer always shows against the squad as it currently
  // stands, even after it's changed underneath an already-displayed (cached) result.
  private Squad mySquad;

  private Runnable onTransferExecuted;

  // Reports each freshly calculated (not cache-hit) strategy's ranking, mirroring KillerTeamTab's
  // own onResult callback - wired to AnalysisReporter::reportTransferSuggestions.
  private BiConsumer<Strategy, Map<Integer, List<TransferSuggestion>>> onResult;

  private Map<Integer, List<TransferSuggestion>> suggestionsByCount;

  // The strategy the user currently has selected - lets a task whose result arrives after the user
  // has since switched to a different strategy recognise it's stale (still caches its result, just
  // doesn't clobber what's now on screen with it).
  private Strategy currentStrategy;

  // The currently in-flight search, if any - see KillerTeamTab's own runningTask for why this
  // exists (starting a second search without cancelling this first would leave both running
  // concurrently, fighting for CPU and making both look hung).
  private Task<List<TransferSuggestion>> runningTask;

  TransfersTab() {

    strategyDropdown.setValue(Strategy.POINTS);
    strategyDropdown.setConverter(FantasyFootballDesktopApp.STRATEGY_LABELS);
    strategyDropdown.setDisable(true);
    strategyDropdown.setOnAction(event -> triggerForCurrentSelection());

    suggestionsBox.setAlignment(Pos.CENTER);

    header = new HBox(12, sectionLabel("Suggested Transfers"), strategyDropdown);
    header.setAlignment(Pos.CENTER_LEFT);

    setFitToWidth(true);
    showLoading(new Label("Loading transfer suggestions..."));
  }

  // The one-time setup below is a no-op after the first call - a submitted transfer (see
  // FantasyFootballDesktopApp) re-fetches the squad and calls this again, but that must not reset
  // the user's chosen strategy or throw away the cache just because the live squad changed
  // elsewhere. mySquad/onTransferExecuted are the exception - refreshed unconditionally on every
  // call, same as KillerTeamTab's own init().
  void init(
      final TeamAnalysisService teamAnalysisService, final List<Player> allPlayers, final Squad mySquad,
      final BiConsumer<Strategy, Map<Integer, List<TransferSuggestion>>> onResult, final Runnable onTransferExecuted) {

    this.mySquad = mySquad;
    this.onTransferExecuted = onTransferExecuted;

    if (this.teamAnalysisService != null) {
      return;
    }

    this.teamAnalysisService = teamAnalysisService;
    this.allPlayers = allPlayers;
    this.onResult = onResult;

    strategyDropdown.setDisable(false);
    triggerForCurrentSelection();
  }

  private void triggerForCurrentSelection() {

    final Strategy strategy = strategyDropdown.getValue();
    currentStrategy = strategy;

    if (cache.containsKey(strategy)) {
      showResult(strategy);
    } else {
      runCalculation(strategy);
    }
  }

  private void runCalculation(final Strategy strategy) {

    cancelRunningTask();

    final Task<List<TransferSuggestion>> task = new Task<>() {
      @Override
      protected List<TransferSuggestion> call() {

        final TransferSearchProgressListener progressListener = (transferBudget, evaluated, total) -> {
          updateMessage(String.format(
              "Evaluating %d-transfer combinations: %,d / %,d", transferBudget, evaluated, total));
          updateProgress(evaluated, Math.max(total, 1));
        };

        return teamAnalysisService.calculateTransferSuggestions(mySquad, allPlayers, strategy, progressListener);
      }
    };

    runningTask = task;
    showLoading(FantasyFootballDesktopApp.progressPane(task));

    task.setOnSucceeded(event -> {
      // Only if this is still the tracked task - see KillerTeamTab's own onSucceeded for why.
      if (runningTask == task) {
        runningTask = null;
      }

      final Map<Integer, List<TransferSuggestion>> ranked =
          teamAnalysisService.rankTransferSuggestions(task.getValue(), strategy);
      cache.put(strategy, ranked);
      onResult.accept(strategy, ranked);

      if (strategy.equals(currentStrategy)) {
        showResult(strategy);
      }
    });

    task.setOnFailed(event -> {
      if (runningTask == task) {
        runningTask = null;
      }

      if (strategy.equals(currentStrategy)) {
        showLoading(new Label("Failed to calculate transfer suggestions: "
            + FantasyFootballDesktopApp.describe(task.getException())));
      }
    });

    Thread.ofVirtual().name("calculate-transfers").start(task);
  }

  // KillerTeamFinder's search checks Thread.currentThread().isInterrupted() every iteration to make
  // cancel(true) (the default) actually stop the CPU-bound work rather than just the Task's own
  // bookkeeping - TransferSelector does the same (see its SearchCancelled).
  private void cancelRunningTask() {

    if (runningTask == null) {
      return;
    }

    runningTask.cancel();
    runningTask = null;
  }

  // Shown while a search is in flight, or in place of it - keeps the header (and so the strategy
  // dropdown) visible throughout rather than replacing the whole tab with a bare progress bar.
  private void showLoading(final Node node) {

    final VBox box = new VBox(16, header, node);
    box.setPadding(new Insets(20));
    setContent(box);
  }

  void showError(final String message) {

    showLoading(new Label("Failed to load data: " + message));
  }

  private void showResult(final Strategy strategy) {

    suggestionsByCount = new TreeMap<>(cache.get(strategy));

    refreshSuggestions(defaultTransferCount());
    transferSection.getChildren().setAll(transferCountToggle(), suggestionsBox);

    final VBox root = new VBox(16,
        header,
        transferSection,
        new Separator(),
        sectionLabel("Injured / Doubtful"),
        injuredList(mySquad));
    root.setPadding(new Insets(20));

    setContent(root);
  }

  // Picks whichever transfer count's best suggestion is the best overall - highest resulting team
  // points, ties broken by lowest team cost, further ties broken by fewer transfers - rather than
  // always defaulting to a fixed count. Each count's list is already sorted best-first (see
  // TeamAnalysisServiceImpl.transferSuggestionComparator), so only its head needs comparing.
  private int defaultTransferCount() {

    Integer bestCount = null;
    TransferSuggestion best = null;

    for (final Map.Entry<Integer, List<TransferSuggestion>> entry : suggestionsByCount.entrySet()) {
      if (entry.getValue().isEmpty()) {
        continue;
      }

      final TransferSuggestion candidate = entry.getValue().get(0);
      if (best == null || isBetter(candidate, entry.getKey(), best, bestCount)) {
        best = candidate;
        bestCount = entry.getKey();
      }
    }

    return bestCount != null ? bestCount : suggestionsByCount.keySet().stream().findFirst().orElse(0);
  }

  private static boolean isBetter(
      final TransferSuggestion candidate, final int candidateCount,
      final TransferSuggestion current, final int currentCount) {

    final int pointsCompare = Integer.compare(candidate.getTeam().getPoints(), current.getTeam().getPoints());
    if (pointsCompare != 0) {
      return pointsCompare > 0;
    }

    final int costCompare = candidate.getTeam().getCostNow().compareTo(current.getTeam().getCostNow());
    if (costCompare != 0) {
      return costCompare < 0;
    }

    return candidateCount < currentCount;
  }

  private HBox transferCountToggle() {

    if (suggestionsByCount.size() < 2) {
      return new HBox();
    }

    final int defaultCount = defaultTransferCount();
    final ToggleGroup group = new ToggleGroup();
    final HBox box = new HBox(12);

    for (final Integer transferCount : suggestionsByCount.keySet()) {
      final RadioButton button = new RadioButton(transferCount + (transferCount == 1 ? " transfer" : " transfers"));
      button.setToggleGroup(group);
      button.setSelected(transferCount == defaultCount);
      button.setOnAction(event -> refreshSuggestions(transferCount));
      box.getChildren().add(button);
    }

    return box;
  }

  private void refreshSuggestions(final int transferCount) {

    final List<TransferSuggestion> suggestions = suggestionsByCount.getOrDefault(transferCount, List.of());

    suggestionsBox.getChildren().clear();
    if (suggestions.isEmpty()) {
      suggestionsBox.getChildren().add(new Label("No suggestions available."));
      return;
    }

    // Every suggestion shown together has the same number of transfer pairs (they're all for this
    // one transferCount), so a single width fits all of them exactly - no measuring needed.
    final double cardWidth = transferCount * PAIR_WIDTH + CARD_HORIZONTAL_PADDING;
    for (final TransferSuggestion suggestion : suggestions) {
      suggestionsBox.getChildren().add(suggestionRow(suggestion, cardWidth));
    }
  }

  private VBox suggestionRow(final TransferSuggestion suggestion, final double cardWidth) {

    // A plain HBox, not a wrap-capable FlowPane - cardWidth (see refreshSuggestions) already
    // guarantees every pair fits on one line, so there's no wrapping left for FlowPane to do, only
    // its looser preferred-height accounting for it to add: an HBox's height is just its tallest
    // child, so a 2-pair row is exactly as tall as a 1-pair row, matching the 1-transfer cards.
    final HBox transfersRow = new HBox(12);
    transfersRow.setAlignment(Pos.CENTER);

    for (final Map.Entry<Player, Player> transfer : suggestion.getTransfers().entrySet()) {
      final Label arrow = new Label("→");
      arrow.getStyleClass().add("transfer-arrow");

      final HBox pair = new HBox(8, PitchPlayer.of(transfer.getKey()), arrow, PitchPlayer.of(transfer.getValue()));
      pair.setAlignment(Pos.CENTER);
      transfersRow.getChildren().add(pair);
    }

    final Label detailLabel = new Label(String.format(
        "New team points: %d · New team cost: £%.1fm",
        suggestion.getTeam().getPoints(), suggestion.getTeam().getCostNow()));
    detailLabel.getStyleClass().add("card-detail");
    detailLabel.setWrapText(true);

    final VBox row = new VBox(8, transfersRow, detailLabel);
    row.getStyleClass().add("card");
    row.setAlignment(Pos.CENTER);
    row.setPrefWidth(cardWidth);
    row.setMinWidth(cardWidth);
    row.setMaxWidth(cardWidth);

    if (mySquad.getTransferContext() != null) {
      row.getChildren().add(executeControl(suggestion));
    }

    return row;
  }

  // Only shown when mySquad.getTransferContext() is set - i.e. this squad came from a real,
  // logged-in FPL account, so there's an actual entry to submit the transfer to.
  private VBox executeControl(final TransferSuggestion suggestion) {

    final Button executeButton = new Button("Make this transfer");
    final Label statusLabel = new Label();
    statusLabel.getStyleClass().add("card-detail");
    statusLabel.setWrapText(true);

    executeButton.setOnAction(event -> {
      if (!confirmed(suggestion)) {
        return;
      }

      executeButton.setDisable(true);
      statusLabel.setText("Submitting...");

      final Task<Void> submitTask = new Task<>() {
        @Override
        protected Void call() throws Exception {

          teamAnalysisService.executeTransfer(mySquad, suggestion);
          return null;
        }
      };

      submitTask.setOnSucceeded(e -> {
        statusLabel.setText("Submitted to FPL.");
        new Alert(AlertType.INFORMATION, "Transfer submitted successfully.").showAndWait();
        onTransferExecuted.run();
      });

      submitTask.setOnFailed(e -> {
        executeButton.setDisable(false);
        final String message = submitTask.getException().getMessage();
        statusLabel.setText("Failed: " + message);
        new Alert(AlertType.ERROR, "Transfer submission failed:\n\n" + message).showAndWait();
      });

      Thread.ofVirtual().name("submit-transfer").start(submitTask);
    });

    final VBox box = new VBox(6, executeButton, statusLabel);
    box.setAlignment(Pos.CENTER);
    return box;
  }

  private boolean confirmed(final TransferSuggestion suggestion) {

    final StringBuilder summary = new StringBuilder();
    for (final Map.Entry<Player, Player> transfer : suggestion.getTransfers().entrySet()) {
      if (!summary.isEmpty()) {
        summary.append("\n");
      }
      summary.append(String.format(
          "OUT: %s (£%.1fm) -> IN: %s (£%.1fm)",
          transfer.getKey().getName(), transfer.getKey().getSellingPrice(),
          transfer.getValue().getName(), transfer.getValue().getCostNow()));
    }

    final Alert confirmation = new Alert(AlertType.CONFIRMATION, String.format(
        "This will submit the following transfer(s) directly to your live FPL team for gameweek %d "
            + "and cannot be undone through this app:\n\n%s",
        mySquad.getTransferContext().currentEvent(), summary));
    confirmation.setHeaderText("Confirm transfer");

    final Optional<ButtonType> result = confirmation.showAndWait();
    return result.isPresent() && result.get() == ButtonType.OK;
  }

  private VBox injuredList(final Squad squad) {

    final List<Player> squadPlayers = new ArrayList<>(squad.getStartingEleven());
    squadPlayers.addAll(squad.getSubstitutes());

    final VBox box = new VBox(8);
    for (final Player player : squadPlayers) {
      if (player.getStatus() == Status.AVAILABLE) {
        continue;
      }

      box.getChildren().add(injuredRow(player));
    }

    if (box.getChildren().isEmpty()) {
      box.getChildren().add(new Label("No injury or availability concerns."));
    }

    return box;
  }

  private HBox injuredRow(final Player player) {

    final Label nameLabel = new Label(String.format("%s (%s)", player.getName(), player.getStatus()));
    nameLabel.getStyleClass().add("card-title");

    final String chance = player.getChanceOfPlayingNextRound() == null
        ? "unknown"
        : player.getChanceOfPlayingNextRound() + "%";
    final String news = player.getNews() == null || player.getNews().isBlank() ? "No details" : player.getNews();

    final Label detailLabel = new Label(String.format("Chance of playing: %s · %s", chance, news));
    detailLabel.getStyleClass().add("card-detail");
    detailLabel.setWrapText(true);

    final VBox info = new VBox(2, nameLabel, detailLabel);
    info.setAlignment(Pos.CENTER_LEFT);

    final HBox row = new HBox(12, PitchPlayer.of(player), info);
    row.getStyleClass().add("card");
    row.setAlignment(Pos.CENTER_LEFT);

    return row;
  }

  private Label sectionLabel(final String text) {

    final Label label = new Label(text);
    label.getStyleClass().add("section-label");
    return label;
  }
}
