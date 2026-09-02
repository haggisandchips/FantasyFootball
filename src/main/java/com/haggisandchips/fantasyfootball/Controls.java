package com.haggisandchips.fantasyfootball;

public final class Controls {

  public static final int MAX_COMBINATIONS_PER_BUCKET = 5;

  // Killer Team: hard ceiling on the raw C(poolSize, position.getNumber()) combination count
  // TeamSelector.buildCombinations will attempt to generate for a single position, before any
  // MAX_COMBINATIONS_PER_BUCKET capping kicks in (that only bounds how many of the *generated*
  // combinations the later search evaluates per bucket - generation itself is otherwise
  // unbounded). GOALKEEPER (pairs) and FORWARD (triples) stay cheap even at a pool of hundreds, but
  // DEFENDER/MIDFIELDER (five-a-side) explode fast - a threshold low enough to admit ~150 available
  // defenders yields C(150,5) ~= 591 million, which previously froze the sole JavaFX UI thread
  // (KillerTeamTab's live "combinations" label rebuilds this on every threshold spinner tick) with
  // no exception, no progress, and no way to recover short of killing the app. buildCombinations
  // throws once a position's filtered pool would exceed this, so the caller gets a clear message
  // instead of a hang.
  public static final long MAX_RAW_COMBINATIONS_PER_POSITION = 2_000_000;

  // Killer Team: target number of players to keep, per position, when auto-calculating each
  // strategy's default minimum threshold (see KillerTeamTab.calculateDefaultThreshold) - the
  // default is set to the maximum value that still keeps at least this many, i.e. the target-ranked
  // player's own stat exactly. Applies to all 4 positions, GOALKEEPER included - C(n, 2) looks cheap
  // in isolation, but it's still a straight multiplier on the combined total across all 4 positions,
  // so leaving any one position unrestricted inflates that combined total by whatever factor its
  // pool is over this target. Just the starting point shown in the tab's editable threshold fields,
  // recalculated fresh from live player data every time rather than a fixed number that would
  // perform worse as the season's points totals grow (a "13+ points" cutoff that's about right in
  // November excludes almost everyone in gameweek 2, and is far too loose by gameweek 30).
  public static final int KILLER_TEAM_DEFAULT_TARGET_PLAYERS_PER_POSITION = 10;

  // Transfers: how far below the anchor (2nd-worst, by the chosen strategy's stat, owned player in
  // that position) a candidate's stat can fall and still be considered - see
  // TransferSelector.filterCandidatePool.
  public static final double TRANSFER_CANDIDATE_STAT_MARGIN_FRACTION = 0.2;

  // Transfers: regardless of the stat-margin/cost checks above, the top N candidates by the chosen
  // strategy's stat in a position are always considered - early in the season, everyone's stats are
  // small and close together, so a percentage-based margin on a tiny anchor value can be too tight
  // and exclude a candidate who's genuinely one of the best available. This is a deliberately
  // generous safety net (slower search is an acceptable trade for not missing a good transfer,
  // especially early season when a sub-optimal pick takes longer to correct).
  public static final int TRANSFER_CANDIDATE_POOL_SIZE = 60;

  public static final int MAXIMUM_PLAYERS_FROM_TEAM = 3;

  public static final int MAX_TRANSFER_SUGGESTIONS_LOGGED = 5;

  // Plan longer term transfer strategies towards a dream team instead of using the real free
  // transfer count. 0 uses whatever Squad.getFreeTransfers() reports.
  public static final int FREE_TRANSFERS_OVERRIDE = 0;

  // FPL reports no per-transfer point cost while a wildcard/free hit chip is active (or during the
  // one-off pre-deadline-1 grace period) - real free transfers are effectively unlimited then, but
  // TransferSelector's combination search over N simultaneous swaps blows up combinatorially, so
  // this caps how many it's asked to consider at once rather than trying up to a full 15-man rebuild
  // (that's what the Killer Team tab is for).
  public static final int UNLIMITED_TRANSFER_SUGGESTION_BUDGET = 2;

  // Cap on how many candidate combinations elapse between transfer-search progress log lines, so a
  // very large search space (e.g. ~1.3B combinations at transferBudget=3) still logs/reports
  // progress frequently instead of only every totalCombinations/10.
  public static final int MAX_TRANSFER_PROGRESS_LOG_STEP = 1_000_000;

  // Same idea as MAX_TRANSFER_PROGRESS_LOG_STEP, but for KillerTeamFinder's from-scratch search.
  public static final int MAX_KILLER_TEAM_PROGRESS_LOG_STEP = 1_000_000;

  // Starting-XI-only fixture-difficulty nudge (see Player.getFixtureDifficultyMultiplier and
  // Starting/OptimalElevenSelector, which use it) - deliberately NOT used by Strategy/PlayerLine
  // (squad-building via transfers/killer team), which is a longer-term decision that shouldn't be
  // swayed by one gameweek's fixture list. How much one FDR point of difference from a player's own
  // historical average difficulty shifts their effective points, per fixture.
  public static final double FIXTURE_DIFFICULTY_ADJUSTMENT_FACTOR = 0.06;

  // Clamp on the per-fixture multiplier above - keeps the adjustment a nudge, not a dominant
  // factor, given effective points elsewhere already swings 2x for a double gameweek alone.
  public static final double FIXTURE_DIFFICULTY_MULTIPLIER_MIN = 0.7;

  public static final double FIXTURE_DIFFICULTY_MULTIPLIER_MAX = 1.3;

  private Controls() {
  }
}
