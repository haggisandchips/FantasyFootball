package com.haggisandchips.fantasyfootball.domain;

// Identifies the live FPL entry a squad was fetched from and the gameweek transfers would apply
// to - only present when the squad came from AuthenticatedSquadProvider (a real, logged-in
// account), since that's the only case where submitting a transfer against it makes sense.
public record TransferContext(int entryId, int currentEvent) {
}
