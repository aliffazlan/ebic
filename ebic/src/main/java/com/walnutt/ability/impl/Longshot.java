package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Artemis - the further the shot, the harder it lands. */
public class Longshot extends PassiveAbility {
    private final double damageIncreasePerTile;

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
}
