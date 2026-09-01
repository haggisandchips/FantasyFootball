package com.haggisandchips.fantasyfootball.squad;

import com.haggisandchips.fantasyfootball.domain.SquadSubstitution;
import com.haggisandchips.fantasyfootball.domain.TransferContext;

import java.io.IOException;

public interface MyTeamExecutor {

  void execute(TransferContext transferContext, SquadSubstitution substitution) throws IOException, InterruptedException;
}
