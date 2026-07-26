package com.walnutt.event;

import com.walnutt.unit.Unit;

public final class PreAttackEvent implements GameEvent, Cancellable {
    private final Unit attacker;
    private final Unit defender;
    private boolean cancelled;

    public PreAttackEvent(Unit attacker, Unit defender) {
        this.attacker = attacker;
        this.defender = defender;
    }

    public Unit getAttacker() {
        return attacker;
    }

    public Unit getDefender() {
        return defender;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void cancel() {
        this.cancelled = true;
    }
}
