package com.walnutt.event;

import com.walnutt.unit.Unit;

/**
 * Published after a DamageEvent resolves to a lethal amount, but before HP is
 * clamped/the unit is removed. A listener (e.g. Objurgation) can call
 * preventDeath(newHp) to abort the death path entirely.
 */
public final class FatalDamageEvent implements GameEvent {
    private final Unit target;
    private final DamageEvent damageEvent;
    private boolean prevented;
    private int revisedHealth;

    public FatalDamageEvent(Unit target, DamageEvent damageEvent) {
        this.target = target;
        this.damageEvent = damageEvent;
    }

    public Unit getTarget() {
        return target;
    }

    public DamageEvent getDamageEvent() {
        return damageEvent;
    }

    public void preventDeath(int setHpTo) {
        this.prevented = true;
        this.revisedHealth = Math.max(1, setHpTo);
    }

    public boolean isPrevented() {
        return prevented;
    }

    public int getRevisedHealth() {
        return revisedHealth;
    }
}
