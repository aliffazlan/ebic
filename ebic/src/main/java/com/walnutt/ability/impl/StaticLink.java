package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.StaticLinkEffect;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Discharge - forms a draining link with an enemy; see StaticLinkEffect for the per-turn mechanics. */
public class StaticLink extends Ability {
    private int damageSteal;
    private int lingerDuration;
    /** How far the link stretches before it snaps. 1 until upgraded. */
    private int linkRange = 1;

    public StaticLink(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 7));
        setRange(definition.getInt("cast_range", 1));
        this.damageSteal = definition.getInt("dmg_steal", 5);
        this.lingerDuration = definition.getInt("buff_linger_duration", 2);
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
        return !other.isDead()
            && other.getTeam() != owner.getTeam()
            && isInRange(state, other.getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit other = ((UnitTarget) target).getUnit();
        owner.addEffect(new StaticLinkEffect(owner, other, damageSteal, lingerDuration, linkRange));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    @Override
    protected void onUpgraded() {
        this.damageSteal = statInt("dmg_steal", damageSteal);
        this.lingerDuration = statInt("buff_linger_duration", lingerDuration);
        this.linkRange = statInt("link_range", linkRange);
    }
}
