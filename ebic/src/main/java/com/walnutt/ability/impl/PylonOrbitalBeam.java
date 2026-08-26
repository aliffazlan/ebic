package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.AbilityCastEvent;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.SummonedUnit;
import com.walnutt.unit.Unit;

/** Pylon's passive - mirrors its summoner's Orbital Beam onto a random nearby enemy, same damage value. */
public class PylonOrbitalBeam extends PassiveAbility {
    private final int radius;

    public PylonOrbitalBeam(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.radius = definition.getInt("radius", 1);
    }

    @Override
    public void onAbilityUsed(GameState state, AbilityCastEvent event) {
        if (event.phase() != AbilityCastEvent.Phase.POST || !(event.ability() instanceof OrbitalBeam beam)) {
            return;
        }
        Unit owner = getOwner();
        if (!(owner instanceof SummonedUnit pylon) || owner.isDead() || event.user() != pylon.getSummoner()) {
            return;
        }

        state.getMap().randomUnitInRadius(owner.getPosition(), radius,
                u -> u.getTeam() != owner.getTeam() && !u.isDead(), state.getRandom())
            .ifPresent(target -> {
                DamageEvent beamDamage = new DamageEvent(owner, target, beam.getDamage());
                beamDamage.setCauseLabel("Pylon Orbital Beam");
                target.takeDamage(state, beamDamage);
            });
    }
}
