package com.haggisandchips.fantasyfootball.service.impl;

import com.haggisandchips.fantasyfootball.Controls;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.PlayerLine;
import com.haggisandchips.fantasyfootball.domain.Position;
import com.haggisandchips.fantasyfootball.domain.Squad;
import com.haggisandchips.fantasyfootball.domain.Team;
import com.haggisandchips.fantasyfootball.domain.TransferSuggestion;
import com.haggisandchips.fantasyfootball.enums.Strategy;
import com.haggisandchips.fantasyfootball.helpers.TeamSelector;
import com.haggisandchips.fantasyfootball.helpers.TransferSelector;
import com.haggisandchips.fantasyfootball.remote.FantasyClient;
import com.haggisandchips.fantasyfootball.service.TeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.haggisandchips.fantasyfootball.domain.Status.AVAILABLE;

@RequiredArgsConstructor
@Slf4j
@Service
public class TeamServiceImpl implements TeamService {

  private static final Predicate<Player> AVAILABLE_PLAYERS = player -> player.getStatus() == AVAILABLE;

  private final FantasyClient fantasyClient;

  private static Team getKillerTeam(
      final Strategy strategy,
      final Map<Position, Map<Integer, Set<PlayerLine>>> permutations,
      final BigDecimal maxBudget) {

    log.debug(String.format("Calculating Killer Team by %s", strategy.name()));

    Team killerTeam = null;

    for (final Set<PlayerLine> goalkeeperLines : permutations.get(Position.GOALKEEPER).values()) {
      int gg = 0;
      for (PlayerLine goalkeeperLine : goalkeeperLines) {
        if (++gg > Controls.MAX_PERMUTATIONS_PER_SCORE) {
          break;
        }

        for (final Set<PlayerLine> defenderLines : permutations.get(Position.DEFENDER).values()) {
          int dd = 0;
          for (PlayerLine defenderLine : defenderLines) {
            if (++dd > Controls.MAX_PERMUTATIONS_PER_SCORE) {
              break;
            }

            for (final Set<PlayerLine> midfielderLines :
                permutations.get(Position.MIDFIELDER).values()) {
              int mm = 0;
              for (PlayerLine midfielderLine : midfielderLines) {
                if (++mm > Controls.MAX_PERMUTATIONS_PER_SCORE) {
                  break;
                }

                for (final Set<PlayerLine> forwardLines :
                    permutations.get(Position.FORWARD).values()) {
                  int ff = 0;
                  for (PlayerLine forwardLine : forwardLines) {
                    if (++ff > Controls.MAX_PERMUTATIONS_PER_SCORE) {
                      break;
                    }

                    List<PlayerLine> playerLines = new ArrayList<>();
                    playerLines.add(goalkeeperLine);
                    playerLines.add(defenderLine);
                    playerLines.add(midfielderLine);
                    playerLines.add(forwardLine);

                    final Team currentTeam = new Team(playerLines);
                    if (isKillerTeamAffordable(maxBudget, currentTeam)) {
                      if (killerTeam == null
                          || isBetterKillerTeam(strategy, currentTeam, killerTeam)) {
                        if (TeamSelector.isValidTeam(currentTeam)) {
                          killerTeam = currentTeam;

                          if (log.isDebugEnabled()) {
                            log.debug(String.format("New (%s based) Killer Team found: %s", strategy.name(), killerTeam));
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    return killerTeam;
  }

  private static Map<Position, List<Player>> copyAvailablePlayers(
      Map<Position, List<Player>> availablePlayers) {

    final Map<Position, List<Player>> copy = new HashMap<>();

    for (final Map.Entry<Position, List<Player>> entry : availablePlayers.entrySet()) {
      final List<Player> players = new ArrayList<>();
      copy.put(entry.getKey(), players);

      players.addAll(entry.getValue());
    }

    return copy;
  }

  private static boolean isKillerTeamAffordable(final BigDecimal maxBudget, final Team currentTeam) {

    BigDecimal moneyAvailable = maxBudget;

    final List<Player> players = currentTeam.getPlayers();

    for (final Player player : players) {
      moneyAvailable = moneyAvailable.subtract(player.getCostNow());
    }

    return moneyAvailable.compareTo(new BigDecimal("0")) >= 0;
  }

  private static boolean isBetterKillerTeam(
      final Strategy strategy, final Team currentTeam, final Team killerTeam) {

    final int comparison = strategy.compare(currentTeam, killerTeam);
    return comparison > 0
        || (comparison == 0
        && currentTeam.getCostNow().compareTo(killerTeam.getCostNow()) < 0);
  }

  private static Comparator<TransferSuggestion> transferSuggestionComparator(final Strategy strategy) {

    return Comparator
        .comparingInt(TransferSuggestion::getUnavailablePlayersOut)
        .thenComparing((first, second) -> strategy.compare(first.getTeam(), second.getTeam()))
        .reversed();
  }

  @Override
  public void process() throws IOException, URISyntaxException, InterruptedException {

    List<Player> allPlayers = fantasyClient.getAllPlayers();

    final Map<Position, List<Player>> availablePlayers =
        allPlayers.stream()
            .filter(AVAILABLE_PLAYERS)
            .collect(Collectors.groupingBy(Player::getPosition));

    availablePlayers.forEach(
        (position, players) -> {
          log.info("{}", position);
          players.forEach(player -> log.info("{}", player));
        });

    final Map<Strategy, Team> finalKillerTeams = new HashMap<>();
    for (final Strategy strategy : Controls.STRATEGIES) {
      final Team killerTeam =
          getKillerTeam(
              strategy,
              TeamSelector.buildPermutations(
                  strategy, copyAvailablePlayers(availablePlayers)),
              Controls.MAX_BUDGET);
      finalKillerTeams.put(strategy, killerTeam);
    }

    finalKillerTeams.forEach((strategy, team) -> log.info("Killer Team by {}: {}", strategy.name(), team));

    final Squad mySquad = fantasyClient.getMySquad(allPlayers);
    log.info("Squad Value: {}", mySquad.getSquadValue());
    log.info("Money Available: {}", mySquad.getMoneyAvailable());
    log.info("Free Transfers: {}", mySquad.getFreeTransfers());
    log.info("My Squad: {}", mySquad.getTeam());

    if (mySquad.getFreeTransfers() > 0 || Controls.FREE_TRANSFERS_OVERRIDE > 0) {
      final List<TransferSuggestion> suggestions =
          TransferSelector.getTransferSuggestions(mySquad, copyAvailablePlayers(availablePlayers));

      for (final Strategy strategy : Controls.STRATEGIES) {
        suggestions.sort(transferSuggestionComparator(strategy));
        log.info("Transfer suggestions by {}:", strategy.name());
        suggestions.stream()
            .limit(Controls.MAX_TRANSFER_SUGGESTIONS_LOGGED)
            .forEach(suggestion -> log.info("{}", suggestion));
      }
    } else {
      log.info("No free transfers available - skipping transfer suggestions");
    }
  }
}
