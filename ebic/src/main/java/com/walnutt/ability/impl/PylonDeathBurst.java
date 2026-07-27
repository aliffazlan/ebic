package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.effect.StatusEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.DeathEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

/**
 * Intrinsic to every Zenith Pylon - not JSON-registered (not player-selectable,
 * doesn't appear in zenith_pylon.json's ability list), constructed directly by
 * PylonAbility.onUse() with the numbers read from Zenith's own Pylon ability.
 */
public class PylonDeathBurst extends PassiveAbility {
    private final int deathDamage;
    private final int deathDuration;

    public PylonDeathBurst(int deathDamage, int deathDuration) {
        super("Pylon Collapse");
        this.deathDamage = deathDamage;
        this.deathDuration = deathDuration;
    }

    @Override
    public void onDeath(GameState state, DeathEvent event) {
        Unit owner = getOwner();
        if (owner == null || event.unit() != owner) {
            return;
        }
        for (Unit enemy : state.getMap().getAdjacentUnits(owner.getPosition(),
                u -> u.getTeam() != owner.getTeam() && !u.isDead())) {
            DamageEvent burstDamage = new DamageEvent(owner, enemy, deathDamage);
            burstDamage.setCauseLabel("Pylon Collapse");
            enemy.takeDamage(state, burstDamage);
            enemy.addEffect(new StatusEffect("Pylon Collapse Stun", deathDuration, StatusFlag.STUNNED));
        }
    }
}
