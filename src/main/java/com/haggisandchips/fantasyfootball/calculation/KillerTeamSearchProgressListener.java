package com.haggisandchips.fantasyfootball.calculation;

import com.haggisandchips.fantasyfootball.domain.Strategy;

// Lets a caller (e.g. the desktop UI) observe KillerTeamFinder's search as it runs, rather than
// only seeing it in the logs - mirrors TransferSearchProgressListener, with the strategy currently
// being searched standing in for transferBudget as the "which phase is this" discriminator.
@FunctionalInterface
public interface KillerTeamSearchProgressListener {

  void onProgress(Strategy strategy, long evaluated, long total);
}
