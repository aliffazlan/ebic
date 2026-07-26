package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.PoisonEffect;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Spitter - applies a stacking-duration poison on attack. */
public class PoisonSting extends PassiveAbility {
    private final int duration;
    private final int damagePerTurnRemaining;

    public PoisonSting(AbilityDefinition definition) {
        super(definition.name(), definition.description());
        this.duration = definition.getInt("duration", 3);
        this.damagePerTurnRemaining = definition.getInt("dmg_per_duration", 4);
    }

    public int getDamagePerTurnRemaining() {
        return damagePerTurnRemaining;
    }

    @Override
    public void onPostAttack(GameState state, PostAttackEvent event) {
        if (event.attacker() != getOwner()) {
            return;
        }
        Unit defender = event.defender();
        if (defender.isDead()) {
            return;
        }
        PoisonEffect.applyOrExtend(defender, getOwner(), duration, damagePerTurnRemaining);
    }
}
