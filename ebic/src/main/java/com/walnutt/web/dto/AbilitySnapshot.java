package com.walnutt.web.dto;

import java.util.List;
import java.util.Map;

public record AbilitySnapshot(
    String id,
    String name,
    String description,
    // The verbose half of the tooltip: one bullet per rule or interaction that was lifted
    // out of the description to keep it short, plus this ability's raw tuning numbers.
    // Both empty for Move/Attack and anything else not built from a JSON definition.
    List<String> details,
    Map<String, Double> stats,
    boolean passive,
    // True once Shawl's Hidden Potential has unlocked this ability. The client renders it
    // in gold; description/details/stats above are already the UPGRADED text by then, so
    // this flag is the only thing the wire needs. Deliberately NOT accompanied by the
    // upgrade's summary - what an ability WOULD become is shown only inside Shawl's own
    // dialogue, never on a unit anyone can hover.
    boolean upgraded,
    boolean ready,
    // True when this specific ability is locked for the rest of the turn by an effect
    // (Joker's Superior Mastery) even though its cooldown may read 0 - see
    // Unit.isAbilityRestricted. Distinct from `ready`, which is cooldown alone.
    boolean usedThisTurn,
    int currentCooldown,
    int maxCooldown,
    // Move points this cast actually spends, computed server-side by Ability.getMoveCost.
    // 0 for a passive, for a BASIC unit's Move/Attack, and for any ability a banked
    // Capacitor Bank charge will pay for. The client renders this rather than re-deriving
    // the rule, which is the only way the two can't drift.
    int moveCost,
    int range,
    int minRange
) {
}
