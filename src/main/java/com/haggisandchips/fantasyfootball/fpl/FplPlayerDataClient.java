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

  // Deliberately the full season (past + future), not just future=1 - attachNextFixtures still
  // needs only the unplayed ones to work out each team's next gameweek (see nextGameweekFixtures,
  // which now filters on finished itself), but the finished ones are what let it also compute each
  // team's average difficulty faced so far this season (see averageDifficultyFacedByTeamId), which
  // future=1 alone would make impossible to get without a second call.
  private static final URI FIXTURES = URI.create("https://fantasy.premierleague.com/api/fixtures/");

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

    final Map<Integer, Double> averageDifficultyFacedByTeamId = averageDifficultyFacedByTeamId(fixturesByTeamId);

    for (final Player player : players) {
      final int teamId = Integer.parseInt(player.getTeam());
      final List<FixtureEntry> teamFixtures = fixturesByTeamId.getOrDefault(teamId, List.of());
      player.setNextFixtures(nextGameweekFixtures(teamId, teamFixtures, shortNamesByTeamId));
      player.setAverageDifficultyFaced(averageDifficultyFacedByTeamId.get(teamId));
    }
  }

  // This team's own next gameweek is whichever of its still-to-play fixtures' event numbers is
  // lowest - a team sitting out a blank gameweek simply has no unplayed fixture carrying that
  // number, so its next real one (however far off) wins instead. Finished fixtures are excluded
  // here (unlike averageDifficultyFacedByTeamId, which wants exactly those) - FIXTURES now returns
  // the whole season, so without this filter the "lowest event number" would resolve to a
  // gameweek already played rather than the next one still to come. Every fixture sharing that same
  // number is then this team's full set for it (more than one only for a double gameweek), sorted by
  // kickoff purely for a stable, predictable display order.
  private static List<Fixture> nextGameweekFixtures(
      final int teamId, final List<FixtureEntry> teamFixtures, final Map<Integer, String> shortNamesByTeamId) {

    final Optional<Integer> nextGameweek = teamFixtures.stream()
        .filter(fixture -> !fixture.isFinished())
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
    return new Fixture(shortNamesByTeamId.get(opponentId), home, difficultyFor(fixture, teamId));
  }

  // The average FDR each team has actually faced across its finished fixtures so far this season -
  // see Player.averageDifficultyFaced/getFixtureDifficultyMultiplier, which use this as the personal
  // baseline a starting-XI pick's upcoming fixture difficulty is compared against. A team with no
  // finished fixtures yet (very start of season) simply has no entry, leaving that lookup null for
  // every one of its players.
  private static Map<Integer, Double> averageDifficultyFacedByTeamId(
      final Map<Integer, List<FixtureEntry>> fixturesByTeamId) {

    final Map<Integer, Double> averages = new HashMap<>();
    for (final Map.Entry<Integer, List<FixtureEntry>> entry : fixturesByTeamId.entrySet()) {
      final int teamId = entry.getKey();
      final List<Integer> finishedDifficulties = entry.getValue().stream()
          .filter(FixtureEntry::isFinished)
          .map(fixture -> difficultyFor(fixture, teamId))
          .toList();

      if (!finishedDifficulties.isEmpty()) {
        averages.put(teamId, finishedDifficulties.stream().mapToInt(Integer::intValue).average().orElseThrow());
      }
    }

    return averages;
  }

  private static int difficultyFor(final FixtureEntry fixture, final int teamId) {

    return fixture.getTeamHome() == teamId ? fixture.getTeamHomeDifficulty() : fixture.getTeamAwayDifficulty();
  }
}
