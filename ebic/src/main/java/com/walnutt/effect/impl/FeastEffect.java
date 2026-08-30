package com.walnutt.effect.impl;

import java.util.Optional;

import com.walnutt.combat.CombatEngine;
import com.walnutt.combat.WeightedEncounter;
import com.walnutt.effect.Effect;
import com.walnutt.effect.StatusEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

/** Grivath's Feast: rooted in place, but makes several automatic lifesteal attacks each of his own turns. */
public class FeastEffect extends Effect {
    private final int attacksPerTurn;
    private final double lifestealPercent;
    private final int rootDuration;
    /** True for the always-on instance upgraded Feast grants; see onPostAttack. */
    private final boolean permanent;

    public FeastEffect(int duration, int attacksPerTurn, double lifestealPercent, int rootDuration) {
        super("Feast",
            "Automatically makes " + attacksPerTurn + " free attack(s) against a random adjacent enemy "
                + "each of Grivath's turns, healing for " + Math.round(lifestealPercent * 100)
                + "% of the damage dealt and rooting each victim for " + rootDuration + " turn(s) "
                + "afterward. Grivath moves and acts freely throughout.",
            duration);
        this.attacksPerTurn = attacksPerTurn;
        this.lifestealPercent = lifestealPercent;
        this.rootDuration = rootDuration;
        this.permanent = duration == Effect.PERMANENT;
        this.category = EffectCategory.BUFF;
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        Unit owner = getOwner();
        if (owner == null || isExpired() || event.team() != owner.getTeam()) {
            return;
        }
        strike(state, attacksPerTurn);
    }

    /**
     * The frenzy's bite, on demand. Upgraded Feast makes the lifesteal and the root permanent
     * and turns the cast itself into nothing but this - so it is called from the ability as
     * well as from the turn hook, and attacksPerTurn is 0 in that permanent instance.
     */
    public void strike(GameState state, int attacks) {
        Unit owner = getOwner();
        if (owner == null || owner.isDead()) {
            return;
        }
        for (int i = 0; i < attacks; i++) {
            Optional<Unit> victim = state.getMap().randomAdjacentUnit(owner.getPosition(),
                u -> u.getTeam() != owner.getTeam() && !u.isDead(), state.getRandom());
            if (victim.isEmpty()) {
                break;
            }
            Unit target = victim.get();
            DamageEvent damage = CombatEngine.performAttack(state, new WeightedEncounter(owner, target));
            if (damage != null && damage.getDamage() > 0) {
                int healed = (int) Math.round(damage.getDamage() * lifestealPercent);
                if (healed > 0) {
                    owner.heal(state, healed);
                }
            }
            if (!target.isDead()) {
                target.addEffect(new StatusEffect("Feast Root", rootDuration, EffectCategory.DEBUFF, StatusFlag.ROOTED));
            }
        }
    }

    /**
     * Upgraded Feast: every attack Grivath makes heals and roots, not only the frenzy's own.
     * Hooked on the post-attack event so it reads real attacks and nothing else, and skipped
     * for a chained follow-up so Timeless Strike cannot multiply the lifesteal.
     */
    @Override
    public void onPostAttack(GameState state, PostAttackEvent event) {
        Unit owner = getOwner();
        if (!permanent || owner == null || isExpired() || event.attacker() != owner || event.chained()) {
            return;
        }
        Unit target = event.defender();
        int dealt = event.damageEvent().getDamage();
        if (dealt <= 0 || target == null) {
            return;
        }
        int healed = (int) Math.round(dealt * lifestealPercent);
        if (healed > 0) {
            owner.heal(state, healed);
        }
        if (!target.isDead()) {
            target.addEffect(new StatusEffect("Feast Root", rootDuration, EffectCategory.DEBUFF, StatusFlag.ROOTED));
        }
    }
}
