package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.combat.CombatEngine;
import com.walnutt.combat.WeightedEncounter;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.TimelessStrikeStunEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Chronos - a successful attack triggers another attack, which can trigger this
 * again. The `depth` field is the depth guard: CombatEngine.performAttack's own
 * PostAttackEvent re-enters onPostAttack synchronously (not through a fresh call
 * stack we control), so depth has to live on the instance and be threaded through
 * via increment-before/decrement-after around the recursive call, capped at
 * MAX_CHAIN_DEPTH to guarantee termination.
 *
 * Chained attacks land at reduced damage (damage_multiplier), applied per hit rather
 * than compounding - a 50-damage opener that chains twice deals 50 + 25 + 25, not
 * 50 + 25 + 12. The reduction is done in onIncomingDamage rather than by pre-scaling
 * through EncounterResolver (Counterstrike's approach) because the chain depends on
 * CombatEngine.performAttack republishing PostAttackEvent to re-enter this hook.
 */
public class TimelessStrike extends PassiveAbility {
    private static final int MAX_CHAIN_DEPTH = 8;

    private final int stunDurationPerHit;
    private final double chainedDamageMultiplier;
    private int depth;

    public TimelessStrike(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.stunDurationPerHit = definition.getInt("duration", 1);
        this.chainedDamageMultiplier = definition.getDouble("damage_multiplier", 0.5);
    }

    /** Halves chained hits only - depth 0 is the player's own opening attack, left at full damage. */
    @Override
    public void onIncomingDamage(GameState state, DamageEvent event) {
        if (depth <= 0 || event.getSource() != getOwner() || event.getDamage() <= 0) {
            return;
        }
        event.multiplyDamage(chainedDamageMultiplier);
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
            // Extend one shared stun rather than stacking a fresh instance per proc:
            // two procs must read as a single 2-turn stun, not two 1-turn stuns
            // overlapping (which expire independently and look wrong in the sidebar).
            defender.getActiveEffect(TimelessStrikeStunEffect.class).ifPresentOrElse(
                existing -> existing.extendDuration(stunDurationPerHit),
                () -> defender.addEffect(new TimelessStrikeStunEffect(stunDurationPerHit)));
        }

        depth++;
        try {
            // chained=true: this is a follow-up within one cast, not a fresh attack, so
            // once-per-cast passives (Energy Break, Counterstrike) skip it.
            CombatEngine.performAttack(state, new WeightedEncounter(getOwner(), defender), true);
        } finally {
            depth--;
        }
    }
}
