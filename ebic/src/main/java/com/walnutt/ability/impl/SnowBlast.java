package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.BlizzardEffect;
import com.walnutt.event.DeathEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Snow Golem - on death, applies a long (damage-less) Blizzard root to all adjacent enemies. */
public class SnowBlast extends PassiveAbility {
    private final int duration;
    private final int radius;

    public SnowBlast(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.duration = definition.getInt("duration", 4);
        this.radius = definition.getInt("radius", 1);
    }

    @Override
    public void onDeath(GameState state, DeathEvent event) {
        Unit owner = getOwner();
        if (owner == null || event.unit() != owner) {
            return;
        }
        for (Unit enemy : state.getMap().getUnitsInRadius(owner.getPosition(), radius)) {
            if (enemy != owner && enemy.getTeam() != owner.getTeam() && !enemy.isDead()) {
                BlizzardEffect.applyOrExtend(enemy, owner, duration, 0);
            }
        }
    }
}
