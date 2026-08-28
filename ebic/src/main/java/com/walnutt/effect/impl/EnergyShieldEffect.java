package com.walnutt.effect.impl;

/**
 * Maxwell's Energy Shield barrier.
 *
 * A marker subclass purely so the ability can find *its own* shield with
 * Unit.getActiveEffect(EnergyShieldEffect.class) and refresh it instead of stacking a
 * second one. Looking up the base BarrierEffect would also match Thaddeus's Holy Shield
 * and Zenith's Dislocation barrier, which are separate shields and should keep stacking
 * alongside this one - a matching-by-name check would work too, but the typed lookup is
 * the idiom used everywhere else in the codebase.
 */
public class EnergyShieldEffect extends BarrierEffect {

    public EnergyShieldEffect(String name, int barrierHp, int duration) {
        super(name,
            "Absorbs up to " + barrierHp + " damage before it reaches this unit's health.",
            duration, barrierHp);
    }
}
