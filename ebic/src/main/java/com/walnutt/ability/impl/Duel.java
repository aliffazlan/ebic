package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.DuelEffect;
import com.walnutt.game.GameState;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

/** Valor - taunts an enemy into forced mutual combat; the survivor gains permanent stats + heals. */
public class Duel extends Ability {
    private int duration;
    private double duelBonus;
    private double duelHealPercent;
    private double winMultiplier;
    /** Mutual total damage multiplier between duellists - a base-kit effect now, not an upgrade. */
    private double duelDamageMultiplier;
    /** Upgrade: the multiplier applied to the ENEMY (damage they take from Valor) only. 0 until upgraded. */
    private double duelDamageMultiplierUpgraded;

    public Duel(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 10));
        setRange(definition.getInt("cast_range", 1));
        this.duration = definition.getInt("duration", 4);
        this.duelBonus = definition.getDouble("duel_bonus", 10);
        this.duelHealPercent = definition.getDouble("duel_heal", 0.5);
        this.winMultiplier = definition.getDouble("win_multiplier", 3);
        this.duelDamageMultiplier = definition.getDouble("duel_damage_multiplier", 2);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (owner.hasStatus(StatusFlag.DUELING)) {
            return false;
        }
        if (!(target instanceof UnitTarget unitTarget)) {
            return false;
        }
        Unit other = unitTarget.getUnit();
        return !other.isDead()
            && other.getTeam() != owner.getTeam()
            && !other.hasStatus(StatusFlag.DUELING)
            && isInRange(state, other.getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit other = ((UnitTarget) target).getUnit();

        DuelEffect mine = new DuelEffect(other, duration, duelBonus, duelHealPercent, winMultiplier);
        DuelEffect theirs = new DuelEffect(owner, duration, duelBonus, duelHealPercent, winMultiplier);
        mine.linkPartner(theirs);
        theirs.linkPartner(mine);
        // "mine" governs damage Valor takes back from the enemy - always the base rate, even
        // upgraded. "theirs" governs damage Valor deals to the enemy - upgraded, that rises to
        // duelDamageMultiplierUpgraded. Only Valor's own half (mine) refreshes this ability on a win.
        mine.upgradeWith(duelDamageMultiplier, isUpgraded() ? this : null);
        theirs.upgradeWith(isUpgraded() ? duelDamageMultiplierUpgraded : duelDamageMultiplier, null);

        owner.addEffect(mine);
        other.addEffect(theirs);

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    @Override
    protected void onUpgraded() {
        this.duration = statInt("duration", duration);
        this.duelBonus = stat("duel_bonus", duelBonus);
        this.duelHealPercent = stat("duel_heal", duelHealPercent);
        this.winMultiplier = stat("win_multiplier", winMultiplier);
        this.duelDamageMultiplier = stat("duel_damage_multiplier", duelDamageMultiplier);
        this.duelDamageMultiplierUpgraded = stat("duel_damage_multiplier_upgraded", duelDamageMultiplier);
    }
}
