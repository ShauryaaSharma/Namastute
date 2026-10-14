package com.namastute.trips;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// What matching needs from the groups module, in the trips module's own words (a "port").
// The groups module implements it, so the dependency points groups → trips only: no cycle (Learning Notes 43.3).
public interface OpenGroups {

    // Open groups at this terminal meeting within ±30 min of exitAt, where nobody blocked this user and this user
    // blocked nobody. Each group is returned as its members' trip ids, longest-standing member first.
    List<List<UUID>> near(UUID userId, String terminalId, Instant exitAt);
}
