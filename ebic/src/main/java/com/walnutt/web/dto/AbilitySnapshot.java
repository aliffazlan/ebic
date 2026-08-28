package com.walnutt.web.dto;

public record AbilitySnapshot(
    String id,
    String name,
    String description,
    boolean passive,
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
