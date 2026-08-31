package com.haggisandchips.fantasyfootball.ui;

import com.haggisandchips.fantasyfootball.calculation.KillerTeamSearchProgressListener;
import com.haggisandchips.fantasyfootball.calculation.StartingElevenSelector;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Strategy;
import com.haggisandchips.fantasyfootball.domain.Team;
import com.haggisandchips.fantasyfootball.service.TeamAnalysisService;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

// The killer-team search is an expensive combinatorial calculation, so unlike the other tabs it
// isn't run automatically - init() enables the controls once player/squad data is available, and
// the user picks a strategy/budget and clicks Calculate once. After that, changing the strategy
// dropdown or the budget field re-triggers a calculation only when that exact (strategy, budget)
// combination hasn't been seen before - otherwise the cached result is shown instantly (see
// KillerTeamCacheKey/cache below). KillerTeamFinder only picks the best affordable 15-man squad,
// with no starting XI/bench split of its own, so showResult() runs StartingElevenSelector over it
// and renders the result exactly like MySquadTab - the same pitch, the same everything.
class KillerTeamTab extends BorderPane {

  // A pattern (rather than parsing on every keystroke) so the field simply refuses to accept a
  // keystroke that would make it invalid, instead of reacting after the fact - at most 3 digits of
  // millions and one decimal place, matching how costs are shown everywhere else (£%.1fm).
  private static final Pattern BUDGET_PATTERN = Pattern.compile("\\d{0,3}(\\.\\d{0,1})?");

  private final ComboBox<Strategy> strategyDropdown = new ComboBox<>(FXCollections.observableArrayList(Strategy.values()));

  private final TextField budgetField = new TextField();

  private final Button calculateButton = new Button("Calculate");

  private final Label statusLabel = new Label("Loading player data...");

  // Every (strategy, maxBudget) combination calculated so far, keyed by exactly the inputs that
  // affect the result - see KillerTeamCacheKey. Extend that record, not this map's shape, if more
  // inputs are ever added (e.g. a minimum-points threshold).
  private final Map<KillerTeamCacheKey, Team> cache = new HashMap<>();

  private TeamAnalysisService teamAnalysisService;

  private List<Player> allPlayers;

  private BiConsumer<Strategy, Team> onResult;

  // The budget field reverts to this if the user leaves it empty - not otherwise used once init()
  // has run once (a later squad reload must not silently change what's already on screen).
  private BigDecimal defaultBudget;

  // Only re-triggers on dropdown/budget changes after the user has clicked Calculate once - before
  // that, they're just choosing their starting inputs.
  private boolean calculated;

  // The (strategy, maxBudget) the user currently has selected - lets a task whose result arrives
  // after the user has since switched to a different selection recognise it's stale (still caches
  // its result, just doesn't clobber what's now on screen with it).
  private KillerTeamCacheKey currentKey;

  KillerTeamTab() {

    strategyDropdown.setValue(Strategy.SCORE);
    strategyDropdown.setConverter(FantasyFootballDesktopApp.STRATEGY_LABELS);
    strategyDropdown.setDisable(true);
    strategyDropdown.setOnAction(event -> triggerIfReady());

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

    final HBox controls = new HBox(10,
        new Label("Strategy:"), strategyDropdown,
        new Label("Budget: £"), budgetField, new Label("m"),
        calculateButton);
    controls.setAlignment(Pos.CENTER_LEFT);
    controls.setPadding(new Insets(16, 20, 0, 20));

    setTop(controls);
    setCenter(new StackPane(statusLabel));
  }

  // A no-op after the first call - a submitted transfer (see FantasyFootballDesktopApp) re-fetches
  // the squad and calls this again, but that must not reset the user's strategy/budget choice or
  // throw away the cache just because the live squad changed elsewhere.
  void init(
      final TeamAnalysisService teamAnalysisService, final List<Player> allPlayers, final Squad mySquad,
      final BiConsumer<Strategy, Team> onResult) {

    if (this.teamAnalysisService != null) {
      return;
    }

    this.teamAnalysisService = teamAnalysisService;
    this.allPlayers = allPlayers;
    this.onResult = onResult;

    defaultBudget = availableFunds(mySquad);
    budgetField.setText(defaultBudget.toPlainString());

    strategyDropdown.setDisable(false);
    budgetField.setDisable(false);
    calculateButton.setDisable(false);
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

    final KillerTeamCacheKey key = new KillerTeamCacheKey(strategyDropdown.getValue(), budget);
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

    final Task<Team> task = new Task<>() {
      @Override
      protected Team call() {

        final KillerTeamSearchProgressListener progressListener = (strategy, evaluated, total) -> {
          updateMessage(String.format(
              "Evaluating %s-based combinations: %,d / %,d", strategy.name(), evaluated, total));
          updateProgress(evaluated, Math.max(total, 1));
        };

        return teamAnalysisService.calculateKillerTeam(allPlayers, key.strategy(), key.maxBudget(), progressListener);
      }
    };

    setCenter(FantasyFootballDesktopApp.progressPane(task));

    task.setOnSucceeded(event -> {
      final Team team = task.getValue();
      cache.put(key, team);
      onResult.accept(key.strategy(), team);

      if (key.equals(currentKey)) {
        showResultOrNoTeamFound(team, key.maxBudget());
      }
    });

    task.setOnFailed(event -> {
      if (key.equals(currentKey)) {
        showError(FantasyFootballDesktopApp.describe(task.getException()));
      }
    });

    Thread.ofVirtual().name("calculate-killer-team").start(task);
  }

  // KillerTeamFinder.find() returns null when no affordable, valid team exists for this budget -
  // routine now the budget is user-editable (rather than the old fixed, generous constant), so it
  // gets its own message rather than surfacing as a crash or a generic "failed to calculate" error.
  private void showResultOrNoTeamFound(final Team team, final BigDecimal maxBudget) {

    if (team == null) {
      setCenter(new StackPane(new Label(String.format(
          "No affordable, valid team found for a £%.1fm budget.", maxBudget))));
    } else {
      showResult(team, maxBudget);
    }
  }

  private void showResult(final Team team, final BigDecimal maxBudget) {

    final StartingElevenSelector.Result picked = StartingElevenSelector.select(team.getPlayers());

    final Squad squad = new Squad(
        team.getCostNow(), maxBudget.subtract(team.getCostNow()), 0, team,
        picked.startingEleven(), picked.substitutes(), null, null, team.getPoints(), null, null);

    setCenter(new MySquadTab(squad));
  }

  void showError(final String message) {

    setCenter(new StackPane(new Label("Failed to calculate: " + message)));
  }

  // Cache key for a killer-team calculation - equal keys (via the generated equals/hashCode) mean
  // an equal result, so any input that changes the search must live here. Add fields for future
  // inputs (e.g. a minimum-points threshold) rather than caching by strategy/budget alone.
  private record KillerTeamCacheKey(Strategy strategy, BigDecimal maxBudget) {
  }
}
