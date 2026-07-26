package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.InfernalBladeEffect;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Lucifer - a successful attack curses the target with a stacking, pausable disarm. */
public class InfernalBlade extends PassiveAbility {
    private final int duration;

    public InfernalBlade(AbilityDefinition definition) {
        super(definition.name(), definition.description());
        this.duration = definition.getInt("duration", 1);
    }

    @Override
    public void onPostAttack(GameState state, PostAttackEvent event) {
        if (event.attacker() != getOwner() || event.damageEvent().getDamage() <= 0) {
            return;
        }
        Unit defender = event.defender();
        if (defender.isDead()) {
            return;
        }
        InfernalBladeEffect.applyOrExtend(defender, getOwner(), duration);
    }
}
