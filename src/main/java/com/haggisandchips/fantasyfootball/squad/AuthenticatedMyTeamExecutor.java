package com.haggisandchips.fantasyfootball.squad;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haggisandchips.fantasyfootball.auth.FplAuthClient;
import com.haggisandchips.fantasyfootball.auth.LiveFplCondition;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.SquadSubstitution;
import com.haggisandchips.fantasyfootball.domain.TransferContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

// Submits a starting-XI/bench swap and/or captaincy change to FPL's own (undocumented) "my team"
// endpoint - the same request fantasy.premierleague.com/my-team/... makes when you click "Save
// Team" there, and the same URI AuthenticatedSquadProvider already GETs picks from. Reverse
// engineered, not a public API (see AuthenticatedTransferExecutor's own comment for the same
// caveat) - the caller sees the raw response body on failure (see FplSessionAuthClient) to help
// diagnose that if it happens.
@Component
@RequiredArgsConstructor
@Slf4j
@Conditional(LiveFplCondition.class)
public class AuthenticatedMyTeamExecutor implements MyTeamExecutor {

  private final FplAuthClient fplAuthClient;

  private final ObjectMapper objectMapper;

  @Override
  public void execute(final TransferContext transferContext, final SquadSubstitution substitution)
      throws IOException, InterruptedException {

    final URI uri = URI.create("https://fantasy.premierleague.com/api/my-team/" + transferContext.entryId() + "/");
    final String requestBody = objectMapper.writeValueAsString(toRequest(substitution));

    log.info("Submitting my-team update: {}", requestBody);
    final String responseBody = fplAuthClient.authenticatedPost(uri, requestBody);
    log.info("My-team update succeeded: {}", responseBody);
  }

  private static MyTeamRequest toRequest(final SquadSubstitution substitution) {

    final List<Pick> picks = new ArrayList<>();

    int position = 1;
    for (final Player player : substitution.startingEleven()) {
      picks.add(toPick(player, position++, substitution));
    }
    for (final Player player : substitution.substitutes()) {
      picks.add(toPick(player, position++, substitution));
    }

    return new MyTeamRequest(picks);
  }

  private static Pick toPick(final Player player, final int position, final SquadSubstitution substitution) {

    return new Pick(
        player.getFantasyId(), position,
        player == substitution.captain(), player == substitution.viceCaptain());
  }

  @Data
  private static class MyTeamRequest {

    private final List<Pick> picks;

    private final Object chip = null;
  }

  @Data
  private static class Pick {

    private final int element;

    private final int position;

    @JsonProperty("is_captain")
    private final boolean isCaptain;

    @JsonProperty("is_vice_captain")
    private final boolean isViceCaptain;
  }
}
