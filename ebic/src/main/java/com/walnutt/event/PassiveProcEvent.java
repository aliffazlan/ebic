package com.walnutt.event;

import com.walnutt.unit.Unit;

/**
 * A silent passive trigger with no gameplay effect of its own to hook (Reload's quiet
 * cooldown reset) but that still needs a one-shot vfx cue on the frontend - see
 * VfxCollector.onGameEvent, the only listener that ever looks for this. Deliberately not
 * routed through AbilityCastEvent: that event is a genuine global broadcast every unit's
 * own onAbilityUsed hook reacts to (other Reload-likes, cast counters, Overheat's
 * per-turn reset, ...), so faking one here purely for a VFX cue would risk those systems
 * treating it as a real cast. `label` becomes the resulting VfxEvent's abilityId (via
 * Identifiers.normalize), the same shape every real ability_used VfxEvent already has.
 */
public record PassiveProcEvent(Unit unit, String label) implements GameEvent {
}
