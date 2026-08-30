package com.haggisandchips.fantasyfootball.squad;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haggisandchips.fantasyfootball.auth.FplAuthClient;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.PlayerLine;
import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Team;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// The genuine article, once FPL_AUTH_ENABLED=true and FPL_EMAIL / FPL_PASSWORD are set (see
// FplSessionAuthClient) - fetches the logged-in user's own entry ID, then their actual picks,
// bank and free transfers, matching players by their real FPL id rather than by name.
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "fpl.auth", name = "enabled", havingValue = "true")
public class AuthenticatedSquadProvider implements SquadProvider {

  private static final URI ME_URI = URI.create("https://fantasy.premierleague.com/api/me/");

  private final FplAuthClient fplAuthClient;

  private final ObjectMapper objectMapper;

  @Override
  public Squad getMySquad(final List<Player> allPlayers) throws IOException, InterruptedException {

    final int entryId = fetchEntryId();
    final MyTeamResponse myTeam = fetchMyTeam(entryId);

    final Map<Integer, Player> playersById =
        allPlayers.stream().collect(Collectors.toMap(Player::getFantasyId, player -> player));

    final List<Player> squadPlayers = new ArrayList<>();
    for (final Pick pick : myTeam.getPicks()) {
      final Player player = playersById.get(pick.getElement());
      if (player == null) {
        log.warn("Could not find player with id {} in the live player data - skipping", pick.getElement());
        continue;
      }

      player.setSellingPrice(toPounds(pick.getSellingPrice()));
      squadPlayers.add(player);
    }

    final Map<Position, List<Player>> squadByPosition =
        squadPlayers.stream().collect(Collectors.groupingBy(Player::getPosition));

    final List<PlayerLine> playerLines = new ArrayList<>();
    for (final Position position : Position.values()) {
      playerLines.add(new PlayerLine(position, squadByPosition.getOrDefault(position, List.of())));
    }

    final Team team = new Team(playerLines);

    final Transfers transfers = myTeam.getTransfers();
    final int freeTransfers;
    if (transfers.getLimit() == null) {
      // null typically means a wildcard/free hit is active this gameweek, i.e. no meaningful
      // transfer budget to suggest swaps within - treat that the same as having none.
      log.info("FPL reports no transfer limit (a chip is likely active) - treating free transfers as 0");
      freeTransfers = 0;
    } else {
      freeTransfers = transfers.getLimit();
    }

    return new Squad(toPounds(transfers.getValue()), toPounds(transfers.getBank()), freeTransfers, team);
  }

  private int fetchEntryId() throws IOException, InterruptedException {

    final String body = fplAuthClient.authenticatedGet(ME_URI);
    final MeResponse me = objectMapper.readValue(body, MeResponse.class);

    if (me.getPlayer() == null) {
      throw new IOException(
          "FPL_API_AUTHORIZATION does not look like an authenticated session (got HTTP 200 with no player) - "
              + "make sure you're actually logged in (not browsing as a guest) and recapture the "
              + "X-Api-Authorization header from a request to fantasy.premierleague.com/api/my-team/ or /api/me/");
    }

    return me.getPlayer().getEntry();
  }

  private MyTeamResponse fetchMyTeam(final int entryId) throws IOException, InterruptedException {

    final URI uri = URI.create("https://fantasy.premierleague.com/api/my-team/" + entryId + "/");
    final String body = fplAuthClient.authenticatedGet(uri);

    return objectMapper.readValue(body, MyTeamResponse.class);
  }

  private static BigDecimal toPounds(final int tenths) {

    return new BigDecimal(tenths).divide(BigDecimal.TEN);
  }

  @Data
  private static class MeResponse {

    private PlayerInfo player;

    @Data
    private static class PlayerInfo {

      private int entry;
    }
  }

  @Data
  private static class MyTeamResponse {

    private List<Pick> picks;

    private Transfers transfers;
  }

  @Data
  private static class Pick {

    private int element;

    @JsonProperty("selling_price")
    private int sellingPrice;
  }

  @Data
  private static class Transfers {

    private Integer limit;

    private int bank;

    private int value;
  }
}
