package com.walnutt.ability.impl;

import java.util.Optional;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Lanaya - redirects incoming damage to a random adjacent unit, limited uses per turn. */
public class Refraction extends PassiveAbility {
    private final int maxCount;
    private int usesRemainingThisTurn;

    public Refraction(AbilityDefinition definition) {
        super(definition.name(), definition.description());
        this.maxCount = definition.getInt("count", 1);
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        if (getOwner() != null && event.team() == getOwner().getTeam()) {
            usesRemainingThisTurn = maxCount;
        }
    }

    @Override
    public void onIncomingDamage(GameState state, DamageEvent event) {
        if (event.getTarget() != getOwner() || usesRemainingThisTurn <= 0 || event.getDamage() <= 0) {
            return;
        }
        Optional<Unit> redirectTarget = state.getMap()
            .randomAdjacentUnit(getOwner().getPosition(), u -> !u.isDead(), state.getRandom());
        if (redirectTarget.isEmpty()) {
            return;
        }

        usesRemainingThisTurn--;
        Unit newTarget = redirectTarget.get();
        int amount = event.getDamage();
        event.cancel();
        DamageEvent redirected = new DamageEvent(event.getSource(), newTarget, amount);
        redirected.setCauseLabel("Refraction");
        newTarget.takeDamage(state, redirected);
    }
}
