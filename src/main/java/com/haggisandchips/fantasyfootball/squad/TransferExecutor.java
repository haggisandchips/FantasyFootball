package com.haggisandchips.fantasyfootball.squad;

import com.haggisandchips.fantasyfootball.domain.TransferContext;
import com.haggisandchips.fantasyfootball.domain.TransferSuggestion;

import java.io.IOException;

public interface TransferExecutor {

  void execute(TransferContext transferContext, TransferSuggestion suggestion) throws IOException, InterruptedException;
}
