package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.BlizzardEffect;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Snow Golem - a successful attack applies a short (damage-less) Blizzard root to the target. */
public class BlizzardFist extends PassiveAbility {
    private final int duration;

    public BlizzardFist(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.duration = definition.getInt("duration", 1);
    }

    @Override
    public void onPostAttack(GameState state, PostAttackEvent event) {
        if (event.attacker() != getOwner() || event.damageEvent().getDamage() <= 0) {
            return;
        }
        Unit defender = event.defender();
        if (defender == null || defender.isDead()) {
            return;
        }
        BlizzardEffect.applyOrExtend(defender, getOwner(), duration, 0);
    }
}
