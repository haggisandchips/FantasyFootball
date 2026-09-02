package com.haggisandchips.fantasyfootball.squad;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haggisandchips.fantasyfootball.Controls;
import com.haggisandchips.fantasyfootball.auth.FplAuthClient;
import com.haggisandchips.fantasyfootball.auth.LiveFplCondition;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.PlayerLine;
import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Team;
import com.haggisandchips.fantasyfootball.domain.TransferContext;
import com.haggisandchips.fantasyfootball.fpl.PlayerDataClient;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// The genuine article, used unless FPL_MY_SQUAD_FILE is set (see LiveFplCondition) - fetches the
// logged-in user's own entry ID, then their actual picks, bank and free transfers, matching
// players by their real FPL id rather than by name. Needs a token from FplSessionAuthClient,
// populated by logging in via the desktop UI's Account menu.
@Component
@RequiredArgsConstructor
@Slf4j
@Conditional(LiveFplCondition.class)
public class AuthenticatedSquadProvider implements SquadProvider {

  private static final URI ME_URI = URI.create("https://fantasy.premierleague.com/api/me/");

  private final FplAuthClient fplAuthClient;

  private final PlayerDataClient playerDataClient;

  private final ObjectMapper objectMapper;

  @Override
  public Squad getMySquad(final List<Player> allPlayers) throws IOException, InterruptedException {

    final int entryId = fetchEntryId();
    final MyTeamResponse myTeam = fetchMyTeam(entryId);
    final EntryResponse entry = fetchEntry(entryId);

    final Map<Integer, Player> playersById =
        allPlayers.stream().collect(Collectors.toMap(Player::getFantasyId, player -> player));

    final List<Pick> picksByPosition = new ArrayList<>(myTeam.getPicks());
    picksByPosition.sort(Comparator.comparingInt(Pick::getPosition));

    final List<Player> squadPlayers = new ArrayList<>();
    final List<Player> startingEleven = new ArrayList<>();
    final List<Player> substitutes = new ArrayList<>();
    Player captain = null;
    Player viceCaptain = null;

    for (final Pick pick : picksByPosition) {
      final Player player = playersById.get(pick.getElement());
      if (player == null) {
        log.warn("Could not find player with id {} in the live player data - skipping", pick.getElement());
        continue;
      }

      player.setSellingPrice(toPounds(pick.getSellingPrice()));
      squadPlayers.add(player);

      if (pick.getPosition() <= 11) {
        startingEleven.add(player);
      } else {
        substitutes.add(player);
      }

      if (pick.isCaptain()) {
        captain = player;
      }
      if (pick.isViceCaptain()) {
        viceCaptain = player;
      }
    }

    final Map<Position, List<Player>> squadByPosition =
        squadPlayers.stream().collect(Collectors.groupingBy(Player::getPosition));

    // Always built fixture-unaware - the baseline Team a Squad carries around (see Team's own
    // considerFixtures field) never moves just because a Transfers/Killer Team toggle is flipped
    // elsewhere. TransferSelector rebuilds a fixture-aware view of this on demand when the toggle
    // asks for one (see Team.withFixtureConsideration).
    final List<PlayerLine> playerLines = new ArrayList<>();
    for (final Position position : Position.values()) {
      playerLines.add(new PlayerLine(position, squadByPosition.getOrDefault(position, List.of()), false));
    }

    final Team team = new Team(playerLines, false);

    final Transfers transfers = myTeam.getTransfers();
    final int freeTransfers = resolveFreeTransfers(transfers);
    final boolean unlimitedTransfers = "unlimited".equals(transfers.getStatus());

    return new Squad(
        toPounds(transfers.getValue()), toPounds(transfers.getBank()), freeTransfers, unlimitedTransfers, team,
        startingEleven, substitutes, captain, viceCaptain, entry.getSummaryOverallPoints(), entry.getName(),
        new TransferContext(entryId, playerDataClient.getCurrentTransferEvent()));
  }

  private int fetchEntryId() throws IOException, InterruptedException {

    final String body = fplAuthClient.authenticatedGet(ME_URI);
    final MeResponse me = objectMapper.readValue(body, MeResponse.class);

    if (me.getPlayer() == null) {
      throw new IOException(
          "The stored FPL session does not look authenticated (got HTTP 200 with no player) - log out and back "
              + "in via Account -> Log in to FPL..., making sure you're actually logged in (not browsing as a "
              + "guest)");
    }

    return me.getPlayer().getEntry();
  }

  private MyTeamResponse fetchMyTeam(final int entryId) throws IOException, InterruptedException {

    final URI uri = URI.create("https://fantasy.premierleague.com/api/my-team/" + entryId + "/");
    final String body = fplAuthClient.authenticatedGet(uri);

    return objectMapper.readValue(body, MyTeamResponse.class);
  }

  private EntryResponse fetchEntry(final int entryId) throws IOException, InterruptedException {

    final URI uri = URI.create("https://fantasy.premierleague.com/api/entry/" + entryId + "/");
    final String body = fplAuthClient.authenticatedGet(uri);

    return objectMapper.readValue(body, EntryResponse.class);
  }

  private static int resolveFreeTransfers(final Transfers transfers) {

    if (transfers.getLimit() != null) {
      return transfers.getLimit();
    }

    if ("unlimited".equals(transfers.getStatus())) {
      log.info("FPL reports unlimited transfers this gameweek (wildcard/free hit active, or the pre-deadline-1 "
          + "grace period) - capping the suggestion search at {} simultaneous swaps", Controls.UNLIMITED_TRANSFER_SUGGESTION_BUDGET);
      return Controls.UNLIMITED_TRANSFER_SUGGESTION_BUDGET;
    }

    log.warn("FPL reports transfer status '{}' with no limit - treating free transfers as 0", transfers.getStatus());
    return 0;
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

    private int position;

    @JsonProperty("selling_price")
    private int sellingPrice;

    @JsonProperty("is_captain")
    private boolean isCaptain;

    @JsonProperty("is_vice_captain")
    private boolean isViceCaptain;
  }

  @Data
  private static class EntryResponse {

    @JsonProperty("summary_overall_points")
    private int summaryOverallPoints;

    private String name;
  }

  @Data
  private static class Transfers {

    private Integer limit;

    // "cost" (normal - limit free transfers, hits beyond that cost points), "unlimited" (wildcard
    // or free hit chip active, or the one-off pre-deadline-1 grace period - no per-transfer cost
    // regardless of count, and limit is null).
    private String status;

    private int bank;

    private int value;
  }
}
