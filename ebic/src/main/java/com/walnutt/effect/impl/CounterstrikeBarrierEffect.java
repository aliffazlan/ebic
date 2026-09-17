package com.walnutt.effect.impl;

/**
 * Valor's Counterstrike barrier.
 *
 * A marker subclass purely so the ability can find *its own* barrier with
 * Unit.getActiveEffect(CounterstrikeBarrierEffect.class) and grow/refresh it instead of
 * stacking a second one - the same idiom EnergyShieldEffect uses for Maxwell's shield.
 * No baked-in remaining-HP number in the description since a repeat proc keeps adding to
 * the pool; getExtraInfo() (inherited from BarrierEffect) already reports the live total.
 */
public class CounterstrikeBarrierEffect extends BarrierEffect {

    public CounterstrikeBarrierEffect(int barrierHp, int duration) {
        super("Counterstrike Barrier",
            "Absorbs damage before it reaches this unit's health.",
            duration, barrierHp);
    }
}
