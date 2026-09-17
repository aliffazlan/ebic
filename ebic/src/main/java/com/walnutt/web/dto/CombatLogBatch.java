package com.walnutt.web.dto;

import java.util.List;

/**
 * One render tick's worth of combat-log-relevant data: the vfx batch from that tick (possibly
 * empty) paired with the currentTeam its "state" snapshot named. ChannelHub records one of these
 * per tick and replays the whole history to a newly-connecting spectator so their client-side
 * combat log (built the same way a live player's is, via GameStateStore.appendCombatLog/
 * commitCombatLog) reconstructs identically instead of starting empty.
 */
public record CombatLogBatch(List<VfxEvent> events, String currentTeam) {
}
