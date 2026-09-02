package com.haggisandchips.fantasyfootball.fpl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haggisandchips.fantasyfootball.domain.Fixture;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.http.HttpGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FplPlayerDataClient implements PlayerDataClient {

  private static final URI BOOTSTRAP_STATIC = URI.create("https://fantasy.premierleague.com/api/bootstrap-static/");

  // future=1 restricts this to fixtures that haven't been played yet, so the lowest-numbered
  // gameweek left for a given team (see attachNextFixtures) is always that team's next one.
  private static final URI FIXTURES = URI.create("https://fantasy.premierleague.com/api/fixtures/?future=1");

  private final HttpGateway httpGateway;

  private final ObjectMapper objectMapper;

  @Override
  public List<Player> getAllPlayers() throws IOException, InterruptedException {

    final Statistics statistics = fetchStatistics();
    attachNextFixtures(statistics.getPlayers(), statistics.getTeams());
    return statistics.getPlayers();
  }

  @Override
  public int getCurrentTransferEvent() throws IOException, InterruptedException {

    return fetchStatistics().getEvents().stream()
        .filter(Statistics.Event::isNext)
        .findFirst()
        .map(Statistics.Event::getId)
        .orElseThrow(() -> new IOException(
            "Could not determine the gameweek currently open for transfers - no event in bootstrap-static "
                + "is marked \"is_next\""));
  }

  private Statistics fetchStatistics() throws IOException, InterruptedException {

    final String body = httpGateway.get(BOOTSTRAP_STATIC);
    return objectMapper.readValue(body, Statistics.class);
  }

  // Mutates each player in place, setting nextFixtures from a separate fixtures fetch - allPlayers
  // is looked up by id everywhere else in the app (see squad.AuthenticatedSquadProvider), so
  // enriching these instances here is enough for the squad/transfer/killer-team screens to see it
  // too, without threading fixture data through every one of those call sites.
  private void attachNextFixtures(final List<Player> players, final List<Statistics.TeamEntry> teams)
      throws IOException, InterruptedException {

    final Map<Integer, String> shortNamesByTeamId = teams.stream()
        .collect(Collectors.toMap(Statistics.TeamEntry::getId, Statistics.TeamEntry::getShortName));

    final String body = httpGateway.get(FIXTURES);
    final List<FixtureEntry> fixtures = objectMapper.readValue(body, new TypeReference<List<FixtureEntry>>() { });

    // Every still-to-play fixture each team appears in, home or away - grouped so
    // nextGameweekFixtures can work out, per team, which gameweek is next for that specific team
    // (not just fixture 1 with the earliest kickoff, which would silently drop a second fixture in
    // the same, "double", gameweek) and collect every fixture that falls in it.
    final Map<Integer, List<FixtureEntry>> fixturesByTeamId = new HashMap<>();
    for (final FixtureEntry fixture : fixtures) {
      fixturesByTeamId.computeIfAbsent(fixture.getTeamHome(), teamId -> new ArrayList<>()).add(fixture);
      fixturesByTeamId.computeIfAbsent(fixture.getTeamAway(), teamId -> new ArrayList<>()).add(fixture);
    }

    for (final Player player : players) {
      final int teamId = Integer.parseInt(player.getTeam());
      final List<FixtureEntry> teamFixtures = fixturesByTeamId.getOrDefault(teamId, List.of());
      player.setNextFixtures(nextGameweekFixtures(teamId, teamFixtures, shortNamesByTeamId));
    }
  }

  // This team's own next gameweek is whichever of its remaining fixtures' event numbers is lowest -
  // a team sitting out a blank gameweek simply has no fixture carrying that number, so its next real
  // one (however far off) wins instead. Every fixture sharing that same number is then this team's
  // full set for it (more than one only for a double gameweek), sorted by kickoff purely for a
  // stable, predictable display order.
  private static List<Fixture> nextGameweekFixtures(
      final int teamId, final List<FixtureEntry> teamFixtures, final Map<Integer, String> shortNamesByTeamId) {

    final Optional<Integer> nextGameweek = teamFixtures.stream()
        .map(FixtureEntry::getEvent)
        .filter(Objects::nonNull)
        .min(Comparator.naturalOrder());

    if (nextGameweek.isEmpty()) {
      return List.of();
    }

    return teamFixtures.stream()
        .filter(fixture -> nextGameweek.get().equals(fixture.getEvent()))
        .sorted(Comparator.comparing(FixtureEntry::getKickoffTime, Comparator.nullsLast(Comparator.naturalOrder())))
        .map(fixture -> toFixture(fixture, teamId, shortNamesByTeamId))
        .toList();
  }

  private static Fixture toFixture(
      final FixtureEntry fixture, final int teamId, final Map<Integer, String> shortNamesByTeamId) {

    final boolean home = fixture.getTeamHome() == teamId;
    final int opponentId = home ? fixture.getTeamAway() : fixture.getTeamHome();
    final int difficulty = home ? fixture.getTeamHomeDifficulty() : fixture.getTeamAwayDifficulty();
    return new Fixture(shortNamesByTeamId.get(opponentId), home, difficulty);
  }
}
