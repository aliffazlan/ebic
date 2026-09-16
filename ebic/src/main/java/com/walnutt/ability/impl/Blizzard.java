package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.BlizzardEffect;
import com.walnutt.game.GameState;
import com.walnutt.unit.SummonedUnit;
import com.walnutt.unit.Unit;

/** Yuki - roots and damages a unit over time; reapplying stacks the duration instead of refreshing it. */
public class Blizzard extends Ability {
    private int duration;
    private int damage;

    public Blizzard(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 4));
        setRange(definition.getInt("cast_range", 3));
        this.duration = definition.getInt("duration", 2);
        this.damage = definition.getInt("damage", 25);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (!(target instanceof UnitTarget unitTarget)) {
            return false;
        }
        Unit other = unitTarget.getUnit();
        return !other.isDead() && isInRange(state, other.getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit other = ((UnitTarget) target).getUnit();
        BlizzardEffect.applyOrExtend(other, owner, duration, damage, isUpgraded());

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    @Override
    protected void onUpgraded() {
        this.duration = statInt("duration", duration);
        this.damage = statInt("damage", damage);
    }

    /**
     * Whether this Yuki's snow disarms. Read by her golem's Blizzard Fist and Snow Blast, which
     * raise the same storm and are tagged no_upgrade precisely because they follow hers rather
     * than being unlocked in their own right.
     */
    public boolean disarms() {
        return isUpgraded();
    }

    /**
     * Upgrading Blizzard only changes what a FUTURE cast does - it never reaches into a
     * BlizzardEffect already ticking on some victim from before the upgrade. Sweep every living
     * unit and force the disarm onto any storm this Yuki laid herself, or that one of her golems
     * (Blizzard Fist / Snow Blast) laid on her behalf, so an already-buried victim is disarmed
     * the instant Shawl unlocks this rather than only on their next hit.
     */
    @Override
    protected void onRetroactiveUpgrade(GameState state) {
        Unit yuki = getOwner();
        if (yuki == null) {
            return;
        }
        for (Unit unit : state.getAllActiveUnits()) {
            unit.getActiveEffect(BlizzardEffect.class).ifPresent(effect -> {
                if (isOwnedByYukiOrHerGolems(effect.getSource(), yuki)) {
                    effect.refresh(damage, true);
                }
            });
        }
    }

    private static boolean isOwnedByYukiOrHerGolems(Unit source, Unit yuki) {
        if (source == yuki) {
            return true;
        }
        return source instanceof SummonedUnit summon && summon.getSummoner() == yuki;
    }
}
