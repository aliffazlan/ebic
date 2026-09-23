package com.walnutt.ability.impl;

import java.util.Arrays;
import java.util.List;

import com.walnutt.ability.Attack;
import com.walnutt.ability.PassiveAbility;
import com.walnutt.combat.Attribute;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.event.PreAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Flint - fans a second shot off the back of the first, judged by an attribute he didn't swing. */
public class DoubleDraw extends PassiveAbility {
    // Set while this passive's own second shot resolves - that shot publishes a chained
    // PostAttackEvent of its own, which must never fan out into yet another Double Draw.
    private boolean firing;

    public DoubleDraw(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
    }

    @Override
    public void onPostAttack(GameState state, PostAttackEvent event) {
        if (event.attacker() != owner || firing || (event.chained() && !inOwnHighNoonBarrage())) {
            return;
        }
        boolean succeeded = event.damageEvent().getDamage() > 0;
        if (!succeeded && !isUpgraded()) {
            return;
        }

        List<Unit> candidates = state.getAllActiveUnits().stream()
            .filter(u -> u.getTeam() != owner.getTeam() && !u.isDead() && u.isTargetable()
                && Attack.canReach(state, owner, u))
            .toList();
        if (candidates.isEmpty()) {
            return;
        }
        Unit secondaryTarget = candidates.get(state.getRandom().nextInt(candidates.size()));

        Attribute used = event.damageEvent().getAttackerAttribute();
        List<Attribute> unused = Arrays.stream(Attribute.values()).filter(a -> a != used).toList();
        Attribute chosen = unused.get(state.getRandom().nextInt(unused.size()));

        int amount = owner.getAttributeValue(chosen);
        if (!succeeded) {
            amount = (int) Math.round(amount * stat("damage", 0.4));
        }

        fireSecondShot(state, secondaryTarget, chosen, amount);
    }

    /**
     * Replicates CombatEngine.performAttack's Pre/PostAttackEvent sequence by hand rather than
     * building an Encounter, since this shot's damage is a precomputed, already-scaled amount
     * (the upgrade's on-miss reduction) - EncounterResolver would only ever re-derive the raw
     * attribute value, discarding that reduction.
     */
    private void fireSecondShot(GameState state, Unit target, Attribute attribute, int amount) {
        firing = true;
        try {
            PreAttackEvent preAttack = new PreAttackEvent(owner, target);
            state.getEventBus().publish(state, preAttack);
            if (preAttack.isCancelled()) {
                return;
            }
            DamageEvent damage = new DamageEvent(owner, target, amount);
            damage.setAttackerAttribute(attribute);
            damage.setCauseLabel("Double Draw");
            target.takeDamage(state, damage);
            state.getEventBus().publish(state, new PostAttackEvent(owner, target, damage, true));
        } finally {
            firing = false;
        }
    }

    /**
     * Upgraded High Noon's barrage fires chained shots (so Counterstrike/Energy Break don't
     * react once per shot), but each of those shots may still trigger this passive - any
     * other chained attack (Timeless Strike-style) still may not.
     */
    private boolean inOwnHighNoonBarrage() {
        return owner.getAbilities().stream()
            .anyMatch(a -> a instanceof HighNoon highNoon && highNoon.isBarrageInProgress());
    }
}
