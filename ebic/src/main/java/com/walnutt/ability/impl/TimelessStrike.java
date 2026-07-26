package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.combat.CombatEngine;
import com.walnutt.combat.WeightedEncounter;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.StatusEffect;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

/**
 * Chronos - a successful attack triggers another attack, which can trigger this
 * again. The `depth` field is the depth guard: CombatEngine.performAttack's own
 * PostAttackEvent re-enters onPostAttack synchronously (not through a fresh call
 * stack we control), so depth has to live on the instance and be threaded through
 * via increment-before/decrement-after around the recursive call, capped at
 * MAX_CHAIN_DEPTH to guarantee termination.
 */
public class TimelessStrike extends PassiveAbility {
    private static final int MAX_CHAIN_DEPTH = 8;

    private final int stunDurationPerHit;
    private int depth;

    public TimelessStrike(AbilityDefinition definition) {
        super(definition.name(), definition.description());
        this.stunDurationPerHit = definition.getInt("duration", 1);
    }

    @Override
    public void onPostAttack(GameState state, PostAttackEvent event) {
        if (event.attacker() != getOwner() || depth >= MAX_CHAIN_DEPTH) {
            return;
        }
        Unit defender = event.defender();
        if (defender == null || defender.isDead() || event.damageEvent().getDamage() <= 0) {
            return;
        }

        if (depth > 0) {
            int stunDuration = stunDurationPerHit * depth;
            defender.addEffect(new StatusEffect("Timeless Strike", stunDuration, StatusFlag.STUNNED));
        }

        depth++;
        try {
            CombatEngine.performAttack(state, new WeightedEncounter(getOwner(), defender));
        } finally {
            depth--;
        }
    }
}
