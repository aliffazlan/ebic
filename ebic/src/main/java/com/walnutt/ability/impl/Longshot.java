package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.status.Stat;
import com.walnutt.status.StatModifier;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Artemis - the further the shot, the harder it lands. */
public class Longshot extends PassiveAbility {
    private double damageIncreasePerTile;
    /** Applied once, on upgrade - see onUpgraded. */
    private int bonusRange;

    public Longshot(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.damageIncreasePerTile = definition.getDouble("dmg_increase", 0.2);
    }

    @Override
    public void onIncomingDamage(GameState state, DamageEvent event) {
        Unit archer = getOwner();
        if (archer == null || event.getSource() != archer || event.getDamage() <= 0) {
            return;
        }
        Unit target = event.getTarget();
        if (target == null || archer.getPosition() == null || target.getPosition() == null) {
            return;
        }
        int distance = state.getMap().getDistance(archer.getPosition(), target.getPosition());
        if (distance <= 0) {
            return;
        }
        event.multiplyDamage(1 + (damageIncreasePerTile * distance));
    }

    /**
     * Upgrade: a permanent bonus to the owner's attack range, which the damage bonus above
     * then has that much further to climb.
     *
     * Granted as a modifier on the OWNER rather than handled inside this ability, the same
     * shape Gyroscope uses - Attack reads Stat.ATTACK_RANGE directly, so nothing else has to
     * learn that Artemis reaches further.
     */
    @Override
    protected void onUpgraded() {
        this.damageIncreasePerTile = stat("dmg_increase", damageIncreasePerTile);
        int upgraded = statInt("bonus_range", 0);
        if (owner != null && upgraded != bonusRange) {
            owner.addPermanentModifier(StatModifier.flat(Stat.ATTACK_RANGE, upgraded - bonusRange, this));
        }
        this.bonusRange = upgraded;
    }
}
