package com.walnutt.web;

import java.util.IdentityHashMap;
import java.util.Map;

import com.walnutt.unit.Unit;

/**
 * Unit/Ability have no stable id field of their own - this assigns and remembers
 * a stable string id per Unit *instance* (identity, not equals/hashCode) the
 * first time it's seen, for as long as this registry (one per match/GameSession)
 * lives. Also keeps the reverse mapping so incoming WS messages that reference a
 * unit by id (e.g. an attack's target) can be resolved back to the real Unit.
 */
public final class UnitIdRegistry {
    private final Map<Unit, String> idsByUnit = new IdentityHashMap<>();
    private final Map<String, Unit> unitsById = new java.util.HashMap<>();
    private int counter = 0;

    public synchronized String idFor(Unit unit) {
        return idsByUnit.computeIfAbsent(unit, u -> {
            String id = "u" + (++counter);
            unitsById.put(id, u);
            return id;
        });
    }

    public synchronized Unit resolve(String id) {
        return id == null ? null : unitsById.get(id);
    }
}
