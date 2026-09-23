package com.walnutt.ability.impl;

import java.util.Optional;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.CombatEngine;
import com.walnutt.combat.WeightedEncounter;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.HighNoonMarkEffect;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Flint - marks whoever he shoots, and once he's fast enough, guns them down in one draw. */
public class HighNoon extends Ability {
    private double chance;
    private int duration;
    private double critMultiplier;
    private int barrageCount;
    // True only while onUse's barrage loop is running - lets DoubleDraw tell this barrage's
    // chained shots (which may trigger it) apart from any other chained attack (which may not).
    private boolean barrageInProgress;

    public HighNoon(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), true);
        this.chance = definition.getDouble("chance", 0.4);
        this.duration = definition.getInt("duration", 3);
        this.critMultiplier = definition.getDouble("crit_damage", 2.5);
    }

    @Override
    protected void onUpgraded() {
        this.chance = stat("chance", chance);
        this.duration = statInt("duration", duration);
        this.critMultiplier = stat("crit_damage", critMultiplier);
        this.barrageCount = statInt("count", 5);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        return super.canUse(state, target) && target instanceof UnitTarget unitTarget
            && !unitTarget.getUnit().isDead() && unitTarget.getUnit().getTeam() != owner.getTeam();
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit primaryTarget = ((UnitTarget) target).getUnit();
        int remaining = barrageCount;
        int fired = 0;
        barrageInProgress = true;
        try {
            while (fired < remaining && !primaryTarget.isDead()) {
                Optional<HighNoonMarkEffect> before = markFrom(primaryTarget);
                CombatEngine.performAttack(state, new WeightedEncounter(owner, primaryTarget), true);
                fired++;
                if (before.isPresent() && before.get().isExpired()) {
                    remaining++;
                }
            }
        } finally {
            barrageInProgress = false;
        }
        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    public boolean isBarrageInProgress() {
        return barrageInProgress;
    }

    /** This unit's own currently-active High Noon mark on target, if any - not any other Flint's. */
    private Optional<HighNoonMarkEffect> markFrom(Unit target) {
        return target.getEffects().stream()
            .filter(e -> !e.isExpired() && e instanceof HighNoonMarkEffect mark && mark.getSource() == owner)
            .map(e -> (HighNoonMarkEffect) e)
            .findFirst();
    }

    @Override
    public void onPostAttack(GameState state, PostAttackEvent event) {
        if (event.attacker() != owner || event.damageEvent().getDamage() <= 0
            || markFrom(event.defender()).isPresent()) {
            return;
        }
        if (state.getRandom().nextDouble() < chance) {
            event.defender().addEffect(new HighNoonMarkEffect(owner, duration, critMultiplier));
        }
    }
}
