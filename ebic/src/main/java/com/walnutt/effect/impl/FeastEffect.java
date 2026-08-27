package com.walnutt.effect.impl;

import java.util.Optional;

import com.walnutt.combat.CombatEngine;
import com.walnutt.combat.WeightedEncounter;
import com.walnutt.effect.Effect;
import com.walnutt.effect.StatusEffect;
import com.walnutt.event.DamageEvent;
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
        this.category = EffectCategory.BUFF;
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        Unit owner = getOwner();
        if (owner == null || isExpired() || event.team() != owner.getTeam()) {
            return;
        }
        for (int i = 0; i < attacksPerTurn; i++) {
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
}
