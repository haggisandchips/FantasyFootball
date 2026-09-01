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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FplPlayerDataClient implements PlayerDataClient {

  private static final URI BOOTSTRAP_STATIC = URI.create("https://fantasy.premierleague.com/api/bootstrap-static/");

  // future=1 restricts this to fixtures that haven't been played yet, so the earliest one left per
  // team (see attachNextFixtures) is always that team's next fixture.
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

  // Mutates each player in place, setting nextFixture from a separate fixtures fetch - allPlayers
  // is looked up by id everywhere else in the app (see squad.AuthenticatedSquadProvider), so
  // enriching these instances here is enough for the squad/transfer/killer-team screens to see it
  // too, without threading fixture data through every one of those call sites.
  private void attachNextFixtures(final List<Player> players, final List<Statistics.TeamEntry> teams)
      throws IOException, InterruptedException {

    final Map<Integer, String> shortNamesByTeamId = teams.stream()
        .collect(Collectors.toMap(Statistics.TeamEntry::getId, Statistics.TeamEntry::getShortName));

    final String body = httpGateway.get(FIXTURES);
    final List<FixtureEntry> fixtures = objectMapper.readValue(body, new TypeReference<List<FixtureEntry>>() { });

    // First (chronologically) unfinished fixture per team wins - putIfAbsent on a stream sorted by
    // kickoff time keeps that first one and ignores any later fixture for a team that already has a
    // next-fixture entry. A team with no fixture in this window (a blank gameweek) simply has no
    // entry, which callers must treat as "no fixture" rather than a lookup failure.
    final Map<Integer, Fixture> nextFixtureByTeamId = new HashMap<>();
    fixtures.stream()
        .sorted(Comparator.comparing(FixtureEntry::getKickoffTime, Comparator.nullsLast(Comparator.naturalOrder())))
        .forEach(fixture -> {
          nextFixtureByTeamId.putIfAbsent(fixture.getTeamHome(),
              new Fixture(shortNamesByTeamId.get(fixture.getTeamAway()), true, fixture.getTeamHomeDifficulty()));
          nextFixtureByTeamId.putIfAbsent(fixture.getTeamAway(),
              new Fixture(shortNamesByTeamId.get(fixture.getTeamHome()), false, fixture.getTeamAwayDifficulty()));
        });

    for (final Player player : players) {
      player.setNextFixture(nextFixtureByTeamId.get(Integer.valueOf(player.getTeam())));
    }
  }
}
