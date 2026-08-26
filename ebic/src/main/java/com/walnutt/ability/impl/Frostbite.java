package com.walnutt.ability.impl;

import java.util.Optional;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.FrostbiteEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Auroth - dealing damage frostbites the target: no healing, instant death below the HP threshold. */
public class Frostbite extends PassiveAbility {
    private final int duration;
    private final double killThreshold;

    public Frostbite(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.duration = definition.getInt("duration", 2);
        this.killThreshold = definition.getDouble("kill_threshold", 0.1);
    }

    @Override
    public void onIncomingDamage(GameState state, DamageEvent event) {
        if (event.getSource() != getOwner() || event.getTarget() == null || event.getDamage() <= 0) {
            return;
        }
        Unit target = event.getTarget();
        Optional<FrostbiteEffect> existing = target.getActiveEffect(FrostbiteEffect.class);
        if (existing.isPresent()) {
            existing.get().setRemainingTurns(duration);
        } else {
            target.addEffect(new FrostbiteEffect(getOwner(), duration, killThreshold));
        }
    }
}
