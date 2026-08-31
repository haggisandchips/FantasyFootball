package com.haggisandchips.fantasyfootball.calculation;

// Lets a caller (e.g. the desktop UI) observe TransferSelector's search as it runs, rather than
// only seeing it in the logs.
@FunctionalInterface
public interface TransferSearchProgressListener {

  void onProgress(int transferBudget, long evaluated, long total);
}
