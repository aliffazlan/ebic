package com.walnutt.ability.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.DamageTakenModifierEffect;
import com.walnutt.effect.impl.StaticLinkEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Discharge - a random adjacent enemy is struck every turn and permanently grows more vulnerable; always also hits a Static Link target. */
public class EyeOfTheStorm extends PassiveAbility {
    private int damage;
    private int bonusDamage;
    /** Bolts released each turn. 1 until upgraded. */
    private int count = 1;

    public EyeOfTheStorm(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.damage = definition.getInt("damage", 2);
        this.bonusDamage = definition.getInt("bonus_damage", 2);
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        Unit owner = getOwner();
        // A dead unit keeps its position and keeps receiving turn hooks, so without the
        // isDead check Discharge went on striking from beyond the grave.
        if (owner == null || owner.isDead() || event.team() != owner.getTeam()) {
            return;
        }

        // Shuffled and taken from, rather than rolled `count` times: a unit cannot be struck
        // more than once per turn, so several bolts need several separate enemies.
        List<Unit> candidates = new ArrayList<>(state.getMap().getAdjacentUnits(owner.getPosition(),
            u -> u.getTeam() != owner.getTeam() && !u.isDead()));
        Collections.shuffle(candidates, state.getRandom());

        Set<Unit> struck = new HashSet<>();
        for (Unit candidate : candidates) {
            if (struck.size() >= count) {
                break;
            }
            if (struck.add(candidate)) {
                strike(state, candidate);
            }
        }

        // The linked target is always struck as well - on top of the bolts, not instead of one,
        // but never twice if it was already among them.
        owner.getActiveEffect(StaticLinkEffect.class).ifPresent(link -> {
            Unit linked = link.getTarget();
            if (linked != null && !linked.isDead() && struck.add(linked)) {
                strike(state, linked);
            }
        });
    }

    private void strike(GameState state, Unit target) {
        DamageEvent strike = new DamageEvent(getOwner(), target, damage);
        strike.setCauseLabel("Eye of the Storm");
        target.takeDamage(state, strike);
        if (target.isDead()) {
            return;
        }
        DamageTakenModifierEffect vulnerability = target.getActiveEffect(DamageTakenModifierEffect.class).orElse(null);
        if (vulnerability == null) {
            vulnerability = new DamageTakenModifierEffect("Static Charge",
                "A permanently stacking vulnerability from Eye of the Storm: increases damage taken "
                    + "from all sources by " + bonusDamage + " per stack.",
                Effect.PERMANENT, 0);
            target.addEffect(vulnerability);
        }
        vulnerability.addStack(bonusDamage);
    }

    @Override
    protected void onUpgraded() {
        this.damage = statInt("damage", damage);
        this.bonusDamage = statInt("bonus_damage", bonusDamage);
        this.count = statInt("count", count);
    }
}
