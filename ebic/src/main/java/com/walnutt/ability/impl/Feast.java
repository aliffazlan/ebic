package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.FeastEffect;
import com.walnutt.event.PostMoveEvent;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.unit.Unit;

/**
 * Grivath - enters a feeding frenzy: no leap, no self-root, just a free automatic
 * attack each turn against an adjacent enemy, healing off the damage and rooting
 * whoever gets bitten. Upgraded, casting it on an enemy latches Grivath onto them
 * instead - see FeastEffect for the latch mechanics.
 */
public class Feast extends Ability {
    private int duration;
    private int attacks;
    private int latchAttacks;
    private double lifesteal;
    private int rootDuration;

    public Feast(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 7));
        setRange(definition.getInt("cast_range", 1));
        this.duration = definition.getInt("duration", 3);
        this.attacks = definition.getInt("attacks", 1);
        this.latchAttacks = definition.getInt("latch_attacks", 2);
        this.lifesteal = definition.getDouble("lifesteal", 0.25);
        this.rootDuration = definition.getInt("root_duration", 1);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (!isUpgraded()) {
            // Base form still opens a window anywhere - prey can walk into it over the next
            // few turns, so there is no adjacency/range gate at all.
            return target instanceof NoTarget;
        }
        // Upgraded, the cast targets a unit: itself (acts like the base form) or a living
        // enemy in range (latches on).
        if (!(target instanceof UnitTarget unitTarget)) {
            return false;
        }
        Unit picked = unitTarget.getUnit();
        if (picked == owner) {
            return true;
        }
        return !picked.isDead() && picked.getTeam() != owner.getTeam() && isInRange(state, picked.getPosition());
    }

    @Override
    protected void onUpgraded() {
        this.duration = statInt("duration", duration);
        this.attacks = statInt("attacks", attacks);
        this.latchAttacks = statInt("latch_attacks", latchAttacks);
        this.lifesteal = stat("lifesteal", lifesteal);
        this.rootDuration = statInt("root_duration", rootDuration);
    }

    @Override
    public void onUse(GameState state, Target target) {
        if (isUpgraded() && target instanceof UnitTarget unitTarget && unitTarget.getUnit() != owner) {
            Unit victim = unitTarget.getUnit();
            FeastEffect effect = new FeastEffect(duration, attacks, latchAttacks, lifesteal, rootDuration, victim);
            owner.addEffect(effect);
            Position from = owner.getPosition();
            state.getMap().moveUnit(owner, state.getMap().getTile(victim.getPosition()));
            // A forced relocation, not a chosen Move: no markMoved, no PreMoveEvent - but
            // PostMoveEvent still fires, same convention as every other forced teleport
            // (Cloak and Dagger, Translocation, ...).
            state.getEventBus().publish(state, new PostMoveEvent(owner, from, owner.getPosition()));
            // The latch's first bite lands immediately on cast, not just from Grivath's next
            // onTurnStart - only for this latch-cast branch, matching the upgrade's own text.
            effect.strike(state, latchAttacks);
        } else {
            // Unupgraded, or upgraded-but-targeting-self: acts exactly like the base ability.
            owner.addEffect(new FeastEffect(duration, attacks, lifesteal, rootDuration));
        }
        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
