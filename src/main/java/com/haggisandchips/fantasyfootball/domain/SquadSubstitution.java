package com.haggisandchips.fantasyfootball.domain;

import java.util.List;

// The live squad exactly as it should be saved back to FPL after a bench swap and/or captaincy
// change - startingEleven/substitutes give the new starting-XI/bench split and their on-pitch
// order (index 0 = FPL pick "position" 1, index 11 = position 12, etc, matching how
// AuthenticatedSquadProvider reads picks back), captain/viceCaptain the new armband holders (both
// must be members of startingEleven).
public record SquadSubstitution(List<Player> startingEleven, List<Player> substitutes, Player captain, Player viceCaptain) {
}
