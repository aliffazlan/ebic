package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.Stat;
import com.walnutt.status.StatModifier;
import com.walnutt.unit.HealthPool;
import com.walnutt.unit.Unit;

/**
 * Maxwell's Shrink Ray debuff.
 *
 * The whole design turns on storing what was TAKEN as absolute amounts rather than
 * storing a multiplier and re-dividing on expiry. A multiplier only gives the right
 * answer if nothing else touched the stat in the meantime, and plenty does - Grivath's
 * Cripple and Dirge's Decay both permanently steal attributes and max health. Tracking
 * absolute amounts means the target is handed back exactly what this effect removed:
 * shrunk 100 -> 80, robbed of 10 elsewhere -> 70, expires at 90.
 *
 * Recasting accumulates into the same instance (80 -> 64 removes a further 16, total 36)
 * and refreshes the duration, so a target shrunk twice still returns to exactly 100.
 *
 * Health needs its own handling because a unit's maximum health is really two numbers:
 * getEffective(MAX_HEALTH), which the modifier below moves, and HealthPool's own cap,
 * which is what actually clamps healing and current HP. Only addPermanentModifier keeps
 * them in step automatically, so this does it explicitly on both apply and expiry.
 */
public class ShrinkRayEffect extends Effect {
    private double statReduction;
    private double hpReduction;
    private int strengthTaken;
    private int agilityTaken;
    private int intelligenceTaken;
    private int maxHealthTaken;

    public ShrinkRayEffect(int duration, double statReduction, double hpReduction) {
        super("Shrunk",
            "Miniaturised by a shrink ray: reduced attributes and reduced maximum health. "
                + "Everything taken is returned when it wears off.",
            duration);
        this.statReduction = statReduction;
        this.hpReduction = hpReduction;
        this.category = EffectCategory.DEBUFF;
    }

    /** Applies a fresh shrink to {@code target}, stacking onto an existing one if present. */
    public static void applyOrStack(Unit target, int duration, double statReduction, double hpReduction) {
        ShrinkRayEffect existing = target.getActiveEffect(ShrinkRayEffect.class).orElse(null);
        if (existing != null) {
            existing.setRemainingTurns(duration);
            // The new cut is taken at the CURRENT percentage, not the one the first cast was made
            // at - see Effect.extendDuration. Everything already taken is still returned in full,
            // because the effect restores what it recorded rather than recomputing it.
            existing.statReduction = statReduction;
            existing.hpReduction = hpReduction;
            existing.shrink();
            return;
        }
        ShrinkRayEffect effect = new ShrinkRayEffect(duration, statReduction, hpReduction);
        target.addEffect(effect);
        effect.shrink();
    }

    /**
     * Takes another cut, measured against the target's CURRENT size - so a second cast
     * shrinks 80 to 64, not to 60.
     */
    private void shrink() {
        Unit target = getOwner();
        if (target == null) {
            return;
        }

        strengthTaken += cut(target, Stat.STRENGTH);
        agilityTaken += cut(target, Stat.AGILITY);
        intelligenceTaken += cut(target, Stat.INTELLIGENCE);

        HealthPool pool = target.getHealthPool();
        int currentBefore = pool.getCurrent();
        int oldMax = pool.getMax();
        maxHealthTaken += (int) Math.round(target.getEffective(Stat.MAX_HEALTH) * hpReduction);

        rebuildModifiers();

        int newMax = (int) target.getEffective(Stat.MAX_HEALTH);
        pool.setMax(newMax);
        if (oldMax > 0) {
            // Both halves of "reduces current and maximum health" bite: the cap drops by
            // hpReduction, and so does the fraction of it the target is sitting on. A unit
            // at 1000/1000 shrunk 10% lands on 810/900 (90% of the new cap), not 900/900.
            double shrunkFraction = ((double) currentBefore / oldMax) * (1 - hpReduction);
            pool.setCurrent((int) Math.round(newMax * shrunkFraction));
        }
    }

    private int cut(Unit target, Stat stat) {
        return (int) Math.round(target.getEffective(stat) * statReduction);
    }

    /** Modifiers are derived state - rewritten from the running totals rather than appended to. */
    private void rebuildModifiers() {
        modifiers.clear();
        addIfNonZero(Stat.STRENGTH, strengthTaken);
        addIfNonZero(Stat.AGILITY, agilityTaken);
        addIfNonZero(Stat.INTELLIGENCE, intelligenceTaken);
        addIfNonZero(Stat.MAX_HEALTH, maxHealthTaken);
    }

    private void addIfNonZero(Stat stat, int taken) {
        if (taken > 0) {
            modifiers.add(StatModifier.flat(stat, -taken, this));
        }
    }

    @Override
    public String getExtraInfo() {
        return "Shrunk by " + strengthTaken + " STR / " + agilityTaken + " AGI / "
            + intelligenceTaken + " INT / " + maxHealthTaken + " max HP";
    }

    /**
     * Gives the maximum health back and rescales current health by the same factor, so the
     * target ends on the health PERCENTAGE it was on while shrunk: 810/900 becomes
     * 900/1000, not 810/1000.
     *
     * The attribute modifiers need no undoing here - they vanish with the effect itself the
     * moment removeExpiredEffects finishes running these hooks. The health cap does, since
     * HealthPool is a separate number that nothing else is about to update.
     */
    @Override
    public void onExpire(GameState state) {
        Unit target = getOwner();
        if (target == null || maxHealthTaken <= 0) {
            return;
        }
        HealthPool pool = target.getHealthPool();
        int oldMax = pool.getMax();
        int newMax = oldMax + maxHealthTaken;
        int currentBefore = pool.getCurrent();

        pool.setMax(newMax);
        if (oldMax > 0 && !target.isDead()) {
            pool.setCurrent((int) Math.round(currentBefore * (double) newMax / oldMax));
        }
    }
}
