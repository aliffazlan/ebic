package com.walnutt.event;

import com.walnutt.unit.Unit;

/**
 * Fired once damage has actually been applied to the target's HealthPool (or a
 * FatalDamageEvent listener revised it instead of letting the unit die). This is
 * the reactive hook (Counterstrike, etc.) - separate from DamageEvent, which is the
 * earlier, mutable, pre-application hook mitigation/redirection listeners use.
 */
public record PostDamageEvent(Unit target, DamageEvent damageEvent) implements GameEvent {
}
