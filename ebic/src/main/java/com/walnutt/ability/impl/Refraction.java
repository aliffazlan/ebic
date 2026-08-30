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
    private int maxCount;
    /** Upgrade: the share of the blow the SECOND refraction of a turn passes on. 1.0 until upgraded. */
    private double secondEfficiency = 1.0;
    private int usesRemainingThisTurn;

    public Refraction(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
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

        // The first refraction of a turn passes the whole blow on; the second passes only
        // secondEfficiency of it, and Lanaya wears the rest.
        boolean isFirst = usesRemainingThisTurn == maxCount;
        double efficiency = isFirst ? 1.0 : secondEfficiency;
        usesRemainingThisTurn--;

        Unit newTarget = redirectTarget.get();
        int amount = event.getDamage();
        int passedOn = (int) Math.round(amount * efficiency);
        if (passedOn >= amount) {
            event.cancel();
        } else {
            event.modifyDamage(-passedOn);
        }
        if (passedOn <= 0) {
            return;
        }
        DamageEvent redirected = new DamageEvent(event.getSource(), newTarget, passedOn);
        redirected.setCauseLabel("Refraction");
        newTarget.takeDamage(state, redirected);
    }

    @Override
    protected void onUpgraded() {
        this.maxCount = statInt("count", maxCount);
        this.secondEfficiency = stat("second_efficiency", 1.0);
        // Mid-turn upgrades are possible (Shawl casts on his own turn), so top the allowance up
        // rather than leaving Lanaya on last turn's count until the next turn start.
        this.usesRemainingThisTurn = Math.max(usesRemainingThisTurn, maxCount);
    }
}
