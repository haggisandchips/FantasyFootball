package com.haggisandchips.fantasyfootball.ui;

import com.haggisandchips.fantasyfootball.Controls;
import com.haggisandchips.fantasyfootball.calculation.KillerTeamSearchProgressListener;
import com.haggisandchips.fantasyfootball.calculation.StartingElevenSelector;
import com.haggisandchips.fantasyfootball.calculation.TeamSelector;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Status;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.Team;
import com.haggisandchips.fantasyfootball.domain.TransferSuggestion;
import com.haggisandchips.fantasyfootball.service.TeamAnalysisService;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory.DoubleSpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// The killer-team search is an expensive combinatorial calculation, so unlike the other tabs it
// isn't run automatically - init() enables the controls once player/squad data is available, and
// the user picks a strategy/budget/per-position minimums and clicks Calculate once. After that,
// changing any of those fields re-triggers a calculation only when that exact combination hasn't
// been seen before - otherwise the cached result is shown instantly (see KillerTeamCacheKey/cache
// below). Only the 4 minimum spinners for the currently selected strategy are shown at a time (each
// strategy keeps its own independently-tunable values - see thresholdsByStrategy), swapped in when
// the dropdown changes; each shows a live count of how many available players currently meet it,
// plus a shared live estimate of the resulting total combinations, both updated on every change
// (cheap - no search triggered) rather than only on commit. Each spinner's starting value is
// calculated fresh from live player data (see calculateDefaultThreshold()), not a fixed number, so
// it stays sane as the season's scores grow - the user can always override it (by typing, clicking
// the spinner's arrows, or scrolling over it), and their edit sticks until the tab is rebuilt.
// KillerTeamFinder only picks the best affordable 15-man squad, with no starting XI/bench split of
// its own, so showResult() runs StartingElevenSelector over it and renders the result exactly like
// MySquadTab - the same pitch, the same everything.
class KillerTeamTab extends BorderPane {

  // A pattern (rather than parsing on every keystroke) so the field simply refuses to accept a
  // keystroke that would make it invalid, instead of reacting after the fact - at most 3 digits of
  // millions and one decimal place, matching how costs are shown everywhere else (£%.1fm).
  private static final Pattern BUDGET_PATTERN = Pattern.compile("\\d{0,3}(\\.\\d{0,1})?");

  private static final DecimalFormat THRESHOLD_FORMAT = new DecimalFormat("0.##");

  private final ComboBox<Strategy> strategyDropdown = new ComboBox<>(FXCollections.observableArrayList(Strategy.values()));

  private final TextField budgetField = new TextField();

  // The editable minimum-threshold spinner and live "N players meet this" label for each position,
  // for whichever strategy is currently selected - see displayThresholdsForCurrentStrategy().
  private final Map<Position, Spinner<Double>> thresholdSpinners = new EnumMap<>(Position.class);

  private final Map<Position, Label> thresholdCountLabels = new EnumMap<>(Position.class);

  // The resulting total combinations across all 4 positions at the current threshold values (see
  // refreshCombinationsEstimate()) - shown to the right of the threshold spinners.
  private final Label combinationsLabel = new Label();

  private final Button calculateButton = new Button("Calculate");

  private final Label statusLabel = new Label("Loading player data...");

  // Every input combination calculated so far, keyed by exactly the inputs that affect the result -
  // see KillerTeamCacheKey. Extend that record, not this map's shape, if more inputs are ever added.
  private final Map<KillerTeamCacheKey, Team> cache = new HashMap<>();

  // Independently-tunable minimum thresholds per strategy - editing GOALKEEPER's minimum while on
  // FORM shouldn't affect what's shown/used after switching to SCORE and back.
  private final Map<Strategy, Map<Position, Double>> thresholdsByStrategy = new EnumMap<>(Strategy.class);

  private TeamAnalysisService teamAnalysisService;

  private List<Player> allPlayers;

  // Status-available players grouped by position, for the live "N players meet this" counts -
  // computed once in init() rather than per-keystroke.
  private Map<Position, List<Player>> availablePlayersByPosition;

  private BiConsumer<Strategy, Team> onResult;

  // Refreshed on every init() call (unlike the rest of this tab's state, which is one-time - see
  // init()'s own comment) so "Use This Team" always computes its deltas against the squad as it
  // currently stands, even after it's changed underneath an already-displayed (cached) result.
  private Squad mySquad;

  // Re-fetches and rebuilds everything from the live squad outward - shared with TransfersTab's own
  // "Make this transfer" button (see FantasyFootballDesktopApp) since both change the same live squad.
  private Runnable onTransferExecuted;

  // The budget field reverts to this if the user leaves it empty - not otherwise used once init()
  // has run once (a later squad reload must not silently change what's already on screen).
  private BigDecimal defaultBudget;

  // Only re-triggers on dropdown/field changes after the user has clicked Calculate once - before
  // that, they're just choosing their starting inputs.
  private boolean calculated;

  // True while a threshold spinner's value is being set programmatically (e.g. swapping in a
  // different strategy's stored values) rather than by the user - suppresses the "trigger a
  // recalculation" side effect of the value-change listener below, without needing to suppress the
  // live count/combinations refresh, which should still happen either way.
  private boolean suppressThresholdTriggers;

  // The input combination the user currently has selected - lets a task whose result arrives after
  // the user has since switched to a different selection recognise it's stale (still caches its
  // result, just doesn't clobber what's now on screen with it).
  private KillerTeamCacheKey currentKey;

  // The currently in-flight search, if any - starting a second one without cancelling this first
  // (e.g. a budget change while a previous search is still running) used to leave both running
  // concurrently, fighting for CPU and making both look hung. See cancelRunningTask().
  private Task<Team> runningTask;

  KillerTeamTab() {

    strategyDropdown.setValue(Strategy.SCORE);
    strategyDropdown.setConverter(FantasyFootballDesktopApp.STRATEGY_LABELS);
    strategyDropdown.setDisable(true);
    strategyDropdown.setOnAction(event -> {
      displayThresholdsForCurrentStrategy();
      triggerIfReady();
    });

    budgetField.setPrefColumnCount(5);
    budgetField.setDisable(true);
    budgetField.setTextFormatter(new TextFormatter<>(change ->
        BUDGET_PATTERN.matcher(change.getControlNewText()).matches() ? change : null));
    budgetField.setOnAction(event -> triggerIfReady());
    budgetField.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
      if (!isFocused) {
        triggerIfReady();
      }
    });

    calculateButton.setDisable(true);
    calculateButton.setOnAction(event -> {
      calculated = true;
      triggerForCurrentSelection();
    });

    combinationsLabel.getStyleClass().add("card-detail");

    // One row, vertically centred (Pos.CENTER_LEFT centres vertically, aligns left horizontally) -
    // the per-position threshold boxes are taller than the other controls (label + spinner + count
    // label stacked), so centring here is what keeps everything's midline level rather than
    // top-aligned against the tallest child.
    final HBox controls = new HBox(12);
    controls.setAlignment(Pos.CENTER_LEFT);
    controls.getChildren().addAll(new Label("Strategy:"), strategyDropdown);
    controls.getChildren().addAll(new Label("Budget: £"), budgetField, new Label("m"));
    controls.getChildren().addAll(buildThresholdSpinners());
    controls.getChildren().addAll(combinationsLabel, calculateButton);
    controls.setPadding(new Insets(16, 20, 0, 20));

    setTop(controls);
    setCenter(new StackPane(statusLabel));
  }

  private VBox[] buildThresholdSpinners() {

    final VBox[] boxes = new VBox[Position.values().length];

    for (int i = 0; i < Position.values().length; i++) {
      final Position position = Position.values()[i];

      final Spinner<Double> spinner = new Spinner<>();
      spinner.setPrefWidth(90);
      spinner.setDisable(true);
      spinner.setEditable(true);

      final DoubleSpinnerValueFactory valueFactory = new DoubleSpinnerValueFactory(0, 999, 0, 0.1);
      valueFactory.setConverter(new StringConverter<>() {
        @Override
        public String toString(final Double value) {
          return value == null ? "" : THRESHOLD_FORMAT.format(value);
        }

        @Override
        public Double fromString(final String text) {
          try {
            return Double.parseDouble(text);
          } catch (final NumberFormatException e) {
            // Reverts the display to the last valid value rather than accepting garbage.
            return valueFactory.getValue();
          }
        }
      });
      spinner.setValueFactory(valueFactory);

      // Mouse-scrollable, not just click-the-arrows or type - the same step the arrows use.
      spinner.setOnScroll(event -> {
        if (event.getDeltaY() > 0) {
          spinner.increment();
        } else if (event.getDeltaY() < 0) {
          spinner.decrement();
        }
        event.consume();
      });

      final Label countLabel = new Label();
      countLabel.getStyleClass().add("card-detail");

      // Fires for every change, however it happened (typing + commit, arrow click, or scroll), as
      // well as for programmatic updates (see displayThresholdsForCurrentStrategy()) - the live
      // per-position count refresh should happen regardless (cheap - just a count), but
      // refreshCombinationsEstimate() must not: it now runs the real buildPermutations(), and during
      // a bulk update the other 3 spinners can still be sitting at stale/default values (e.g. 0 -
      // "no filter") until their turn in the loop comes, which would run that real permutation
      // generation against a near-unfiltered pool - hundreds of millions of combinations, hanging
      // the UI thread. So, like the strategy-map write-back, it's held off until the whole bulk
      // update finishes (displayThresholdsForCurrentStrategy() does this itself, once, at the end).
      //
      // Deliberately does NOT auto-trigger a recalculation the way the strategy/budget controls do -
      // a threshold is something the user dials against the live count/combinations preview, often
      // several times in a row, and kicking off (and cancelling, and re-kicking-off) an expensive
      // background search on every single tick of that is wasteful at best. It just cancels whatever
      // search is currently running (its inputs are stale the moment a threshold changes anyway) and
      // waits for an explicit Calculate click with the values the user actually settles on.
      spinner.valueProperty().addListener((observable, oldValue, newValue) -> {
        refreshCount(position);

        if (suppressThresholdTriggers) {
          return;
        }

        thresholdsByStrategy.get(strategyDropdown.getValue()).put(position, newValue);
        refreshCombinationsEstimate();
        cancelRunningTask();
      });

      thresholdSpinners.put(position, spinner);
      thresholdCountLabels.put(position, countLabel);

      final VBox box = new VBox(2, new Label(position.getAbbreviation() + " min:"), spinner, countLabel);
      box.setAlignment(Pos.CENTER_LEFT);
      boxes[i] = box;
    }

    return boxes;
  }

  // The one-time setup below is a no-op after the first call - a submitted transfer (see
  // FantasyFootballDesktopApp) re-fetches the squad and calls this again, but that must not reset
  // the user's choices or throw away the cache just because the live squad changed elsewhere.
  // mySquad/onTransferExecuted are the exception - refreshed unconditionally on every call, so
  // "Use This Team" (see useThisTeamBar()) always has the current squad to diff against.
  void init(
      final TeamAnalysisService teamAnalysisService, final List<Player> allPlayers, final Squad mySquad,
      final BiConsumer<Strategy, Team> onResult, final Runnable onTransferExecuted) {

    this.mySquad = mySquad;
    this.onTransferExecuted = onTransferExecuted;

    if (this.teamAnalysisService != null) {
      return;
    }

    this.teamAnalysisService = teamAnalysisService;
    this.allPlayers = allPlayers;
    this.onResult = onResult;

    availablePlayersByPosition = allPlayers.stream()
        .filter(player -> player.getStatus() == Status.AVAILABLE)
        .collect(Collectors.groupingBy(Player::getPosition));

    for (final Strategy strategy : Strategy.values()) {
      thresholdsByStrategy.put(strategy, calculateDefaultThreshold(strategy));
    }

    defaultBudget = availableFunds(mySquad);
    budgetField.setText(defaultBudget.toPlainString());

    strategyDropdown.setDisable(false);
    budgetField.setDisable(false);
    calculateButton.setDisable(false);
    thresholdSpinners.values().forEach(spinner -> spinner.setDisable(false));

    displayThresholdsForCurrentStrategy();

    statusLabel.setText("Click Calculate to build the best possible team from scratch.");
  }

  // "Available funds" - what the whole squad could be rebuilt with if sold outright, matching what
  // TransferSelector.isAffordable already treats a player as being worth (their selling price, not
  // current market cost) plus whatever's left in the bank.
  private static BigDecimal availableFunds(final Squad mySquad) {

    BigDecimal total = mySquad.getMoneyAvailable();
    for (final Player player : mySquad.getTeam().getPlayers()) {
      total = total.add(player.getSellingPrice());
    }

    return total.setScale(1, RoundingMode.HALF_UP);
  }

  // Auto-calculated so the starting values stay sane through the season instead of degrading the
  // way a fixed number would (a cutoff that's about right in November excludes almost everyone in
  // gameweek 2, and is far too loose by gameweek 30) - the maximum value that still keeps at least
  // Controls.KILLER_TEAM_DEFAULT_TARGET_PLAYERS_PER_POSITION players, i.e. exactly the target-ranked
  // player's own stat. Applies to GOALKEEPER too - C(n, 2) looks cheap in isolation, but it's still
  // a straight multiplier on the combined total across all 4 positions, so leaving it unrestricted
  // inflates that total by whatever factor the full goalkeeper pool is over 10 (tens to hundreds of
  // times too many) - see the "combinations" label showing exactly that combined total.
  private Map<Position, Double> calculateDefaultThreshold(final Strategy strategy) {

    final Map<Position, Double> thresholds = new EnumMap<>(Position.class);

    for (final Position position : Position.values()) {
      final List<Double> statsDescending = availablePlayersByPosition.getOrDefault(position, List.of()).stream()
          .map(strategy.getPlayerStat())
          .sorted(Comparator.reverseOrder())
          .toList();

      if (statsDescending.isEmpty()) {
        thresholds.put(position, 0.0);
        continue;
      }

      final int targetIndex = Math.min(
          Controls.KILLER_TEAM_DEFAULT_TARGET_PLAYERS_PER_POSITION - 1, statsDescending.size() - 1);
      thresholds.put(position, statsDescending.get(targetIndex));
    }

    return thresholds;
  }

  // Swaps the 4 spinners' displayed values (and live counts/combinations) to the currently selected
  // strategy's own thresholds - called on init() and whenever the strategy dropdown changes.
  private void displayThresholdsForCurrentStrategy() {

    final Map<Position, Double> activeThresholds = thresholdsByStrategy.get(strategyDropdown.getValue());
    if (activeThresholds == null) {
      return;
    }

    suppressThresholdTriggers = true;
    try {
      for (final Position position : Position.values()) {
        thresholdSpinners.get(position).getValueFactory().setValue(activeThresholds.get(position));
      }
    } finally {
      suppressThresholdTriggers = false;
    }

    refreshCombinationsEstimate();
  }

  private long countMeetingThreshold(final Position position, final double threshold) {

    final Strategy strategy = strategyDropdown.getValue();
    return availablePlayersByPosition.getOrDefault(position, List.of()).stream()
        .filter(player -> strategy.getPlayerStat().apply(player) >= threshold)
        .count();
  }

  private void refreshCount(final Position position) {

    if (availablePlayersByPosition == null) {
      return;
    }

    final double threshold = thresholdSpinners.get(position).getValue();
    thresholdCountLabels.get(position).setText(countMeetingThreshold(position, threshold) + " players");
  }

  // The exact same number Calculate will actually evaluate, without doing any of the work Calculate
  // does - TeamSelector.countCappedCombinations gets there via a subset-sum DP over player point
  // totals rather than by generating a single candidate team, so this stays fast (and safe to run on
  // this, the FX Application Thread, on every keystroke/scroll) no matter how low a threshold is
  // pushed. An earlier version of this method built the real permutations to get this number, which
  // is what let a wide-open threshold (e.g. DEFENDER at 0, admitting the full ~150-player pool) hang
  // the whole UI thread evaluating hundreds of millions of raw combinations synchronously.
  private void refreshCombinationsEstimate() {

    if (availablePlayersByPosition == null) {
      combinationsLabel.setText("");
      return;
    }

    final Strategy strategy = strategyDropdown.getValue();

    final Map<Position, Double> currentThresholds = new EnumMap<>(Position.class);
    final Map<Position, List<Player>> poolCopy = new HashMap<>();
    for (final Position position : Position.values()) {
      currentThresholds.put(position, thresholdSpinners.get(position).getValue());
      poolCopy.put(position, new ArrayList<>(availablePlayersByPosition.getOrDefault(position, List.of())));
    }

    final long total = TeamSelector.countCappedCombinations(strategy, poolCopy, currentThresholds);
    combinationsLabel.setText(String.format("%,d combinations", total));
  }

  private void triggerIfReady() {

    if (calculated) {
      triggerForCurrentSelection();
    }
  }

  private void triggerForCurrentSelection() {

    final BigDecimal budget = parseBudget();
    if (budget == null) {
      budgetField.setText(defaultBudget.toPlainString());
      return;
    }

    final Strategy strategy = strategyDropdown.getValue();
    // An immutable snapshot - thresholdsByStrategy's maps keep being edited live, and a cache key
    // must never change after being used as one.
    final Map<Position, Double> thresholds = Map.copyOf(thresholdsByStrategy.get(strategy));

    final KillerTeamCacheKey key = new KillerTeamCacheKey(strategy, budget, thresholds);
    currentKey = key;

    // containsKey, not get() != null - KillerTeamFinder.find() can genuinely return null (no
    // affordable/valid team for this budget), which must still count as "already calculated" or an
    // unaffordable budget would trigger a fresh (identical, pointless) search every time it's re-shown.
    if (cache.containsKey(key)) {
      showResultOrNoTeamFound(cache.get(key), key.maxBudget());
    } else {
      runCalculation(key);
    }
  }

  private BigDecimal parseBudget() {

    final String text = budgetField.getText();
    if (text == null || text.isBlank()) {
      return null;
    }

    try {
      // BUDGET_PATTERN allows a few strings BigDecimal itself rejects mid-edit - e.g. a bare "."
      // (no digits either side) - so this can still throw even though the formatter let it through.
      return new BigDecimal(text).setScale(1, RoundingMode.HALF_UP);
    } catch (final NumberFormatException e) {
      return null;
    }
  }

  private void runCalculation(final KillerTeamCacheKey key) {

    // Never let two searches run at once - see runningTask's own comment.
    cancelRunningTask();

    final Task<Team> task = new Task<>() {
      @Override
      protected Team call() {

        final KillerTeamSearchProgressListener progressListener = (strategy, evaluated, total) -> {
          updateMessage(String.format(
              "Evaluating %s-based combinations: %,d / %,d", strategy.name(), evaluated, total));
          updateProgress(evaluated, Math.max(total, 1));
        };

        return teamAnalysisService.calculateKillerTeam(
            allPlayers, key.strategy(), key.maxBudget(), key.minimumThresholds(), progressListener);
      }
    };

    runningTask = task;
    setCenter(FantasyFootballDesktopApp.progressPane(task));
    setBottom(null);

    task.setOnSucceeded(event -> {
      // Only if this is still the tracked task - a narrow race exists where cancel() is called just
      // as this task was already succeeding (too late to actually cancel), in which case this must
      // not clear a *newer* task's reference that's since taken its place.
      if (runningTask == task) {
        runningTask = null;
      }

      final Team team = task.getValue();
      cache.put(key, team);
      onResult.accept(key.strategy(), team);

      if (key.equals(currentKey)) {
        showResultOrNoTeamFound(team, key.maxBudget());
      }
    });

    task.setOnFailed(event -> {
      if (runningTask == task) {
        runningTask = null;
      }

      if (key.equals(currentKey)) {
        showError(FantasyFootballDesktopApp.describe(task.getException()));
      }
    });

    Thread.ofVirtual().name("calculate-killer-team").start(task);
  }

  // KillerTeamFinder.find()'s loop checks Thread.currentThread().isInterrupted() every iteration
  // and returns early once it sees it - cancel(true) (the default) interrupts whatever thread is
  // currently running the task's call(), so this actually stops the CPU-bound work, not just the
  // Task's own bookkeeping state (which cancel() alone would update, but the search wouldn't notice).
  private void cancelRunningTask() {

    if (runningTask == null) {
      return;
    }

    runningTask.cancel();
    runningTask = null;

    // A cancelled task's progress/message properties simply stop updating - without this, whatever
    // percentage/count it had last reached would sit there looking current (or hung) indefinitely,
    // rather than reflecting that nothing is actually running any more. statusLabel's text is left
    // exactly as init() set it ("Click Calculate..."), so re-showing it is the same as resetting to
    // the tab's original starting state.
    setCenter(new StackPane(statusLabel));
    setBottom(null);
  }

  // KillerTeamFinder.find() returns null when no affordable, valid team exists for this budget -
  // routine now the budget and thresholds are user-editable, so it gets its own message rather than
  // surfacing as a crash or a generic "failed to calculate" error.
  private void showResultOrNoTeamFound(final Team team, final BigDecimal maxBudget) {

    if (team == null) {
      setCenter(new StackPane(new Label(String.format(
          "No affordable, valid team found for a £%.1fm budget.", maxBudget))));
      setBottom(null);
    } else {
      showResult(team, maxBudget);
    }
  }

  private void showResult(final Team team, final BigDecimal maxBudget) {

    final StartingElevenSelector.Result picked = StartingElevenSelector.select(team.getPlayers());

    final Squad squad = new Squad(
        team.getCostNow(), maxBudget.subtract(team.getCostNow()), 0, team,
        picked.startingEleven(), picked.substitutes(), null, null, team.getPoints(), null, null);

    // teamAnalysisService/onTransferExecuted are never touched here - this squad's own
    // TransferContext is always null (see Squad above), so MySquadTab never shows its
    // substitution button for it.
    setCenter(new MySquadTab(squad, null, null));

    // Only offered against a real, logged-in FPL account - there's nothing to submit a transfer to
    // otherwise (stub mode, or mySquad not yet loaded). Matches TransfersTab's own "Make this
    // transfer" button, which is hidden the same way.
    setBottom(mySquad != null && mySquad.getTransferContext() != null ? useThisTeamBar(team) : null);
  }

  void showError(final String message) {

    setCenter(new StackPane(new Label("Failed to calculate: " + message)));
    setBottom(null);
  }

  // The transfers needed to turn mySquad into killerTeam - every squad player killerTeam doesn't
  // keep, paired position-by-position with every killerTeam player mySquad doesn't already have.
  // Both are always full, valid squads (2 GOALKEEPER/5 DEFENDER/5 MIDFIELDER/3 FORWARD - see
  // Position), so the two lists for a given position are always the same size; which particular
  // leaving player gets paired with which particular arriving one within a position doesn't matter
  // to FPL (AuthenticatedTransferExecutor submits each pick independently), only that the pairing is
  // complete.
  private static Map<Player, Player> computeTransfers(final Squad mySquad, final Team killerTeam) {

    final Set<Integer> killerTeamIds = killerTeam.getPlayers().stream()
        .map(Player::getFantasyId).collect(Collectors.toSet());
    final Set<Integer> squadIds = mySquad.getTeam().getPlayers().stream()
        .map(Player::getFantasyId).collect(Collectors.toSet());

    final Map<Position, List<Player>> outByPosition = mySquad.getTeam().getPlayers().stream()
        .filter(player -> !killerTeamIds.contains(player.getFantasyId()))
        .collect(Collectors.groupingBy(Player::getPosition));
    final Map<Position, List<Player>> inByPosition = killerTeam.getPlayers().stream()
        .filter(player -> !squadIds.contains(player.getFantasyId()))
        .collect(Collectors.groupingBy(Player::getPosition));

    final Map<Player, Player> transfers = new LinkedHashMap<>();
    for (final Position position : Position.values()) {
      final List<Player> out = outByPosition.getOrDefault(position, List.of());
      final List<Player> in = inByPosition.getOrDefault(position, List.of());

      if (out.size() != in.size()) {
        // Should be unreachable given both sides are always valid 2/5/5/3 squads - guarding it
        // anyway rather than letting a mismatch throw a bare IndexOutOfBoundsException below, since
        // this feeds a real, irreversible FPL submission.
        throw new IllegalStateException(String.format(
            "Could not match up %s transfers (%d leaving, %d arriving) - not submitting.",
            position, out.size(), in.size()));
      }

      for (int i = 0; i < out.size(); i++) {
        transfers.put(out.get(i), in.get(i));
      }
    }

    return transfers;
  }

  // Only shown when mySquad.getTransferContext() is set - see showResult(). Mirrors TransfersTab's
  // own "Make this transfer" button/status-label pairing.
  private HBox useThisTeamBar(final Team killerTeam) {

    final Button useButton = new Button("Use This Team");
    final Label statusLabel = new Label();
    statusLabel.getStyleClass().add("card-detail");

    useButton.setOnAction(event -> {
      final Map<Player, Player> transfers;
      try {
        transfers = computeTransfers(mySquad, killerTeam);
      } catch (final IllegalStateException e) {
        new Alert(AlertType.ERROR, e.getMessage()).showAndWait();
        return;
      }

      if (transfers.isEmpty()) {
        new Alert(AlertType.INFORMATION, "Your squad already matches this team - no transfers needed.").showAndWait();
        return;
      }

      if (!confirmed(transfers)) {
        return;
      }

      final TransferSuggestion suggestion = new TransferSuggestion(killerTeam, transfers);

      useButton.setDisable(true);
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
        new Alert(AlertType.INFORMATION, "Transfer(s) submitted successfully.").showAndWait();
        onTransferExecuted.run();
      });

      submitTask.setOnFailed(e -> {
        useButton.setDisable(false);
        final String message = submitTask.getException().getMessage();
        statusLabel.setText("Failed: " + message);
        new Alert(AlertType.ERROR, "Transfer submission failed:\n\n" + message).showAndWait();
      });

      Thread.ofVirtual().name("submit-killer-team-transfer").start(submitTask);
    });

    final HBox bar = new HBox(12, useButton, statusLabel);
    bar.setAlignment(Pos.CENTER);
    bar.setPadding(new Insets(12));
    return bar;
  }

  // Mirrors TransfersTab's own confirmed(TransferSuggestion) - duplicated rather than shared since
  // that one is keyed to a single suggestion's own transfer map, and pulling a shared helper out for
  // one identical loop body isn't worth the indirection.
  private boolean confirmed(final Map<Player, Player> transfers) {

    final StringBuilder summary = new StringBuilder();
    for (final Map.Entry<Player, Player> transfer : transfers.entrySet()) {
      if (!summary.isEmpty()) {
        summary.append("\n");
      }
      summary.append(String.format(
          "OUT: %s (£%.1fm) -> IN: %s (£%.1fm)",
          transfer.getKey().getName(), transfer.getKey().getSellingPrice(),
          transfer.getValue().getName(), transfer.getValue().getCostNow()));
    }

    final Alert confirmation = new Alert(AlertType.CONFIRMATION, String.format(
        "This will submit the following %d transfer(s) directly to your live FPL team for gameweek %d "
            + "and cannot be undone through this app:\n\n%s",
        transfers.size(), mySquad.getTransferContext().currentEvent(), summary));
    confirmation.setHeaderText("Confirm transfers");

    final Optional<ButtonType> result = confirmation.showAndWait();
    return result.isPresent() && result.get() == ButtonType.OK;
  }

  // Cache key for a killer-team calculation - equal keys (via the generated equals/hashCode, which
  // for minimumThresholds compares Map content) mean an equal result, so any input that changes the
  // search must live here. Add fields for future inputs rather than caching by these three alone.
  private record KillerTeamCacheKey(Strategy strategy, BigDecimal maxBudget, Map<Position, Double> minimumThresholds) {
  }
}
