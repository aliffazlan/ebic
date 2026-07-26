package com.walnutt.ui;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.unit.Unit;

/** Bundles what InputHandler gathered from the player into one action for TurnManager to run. */
public final class ActionChoice {
    private final Unit unit;
    private final Ability ability;
    private final Target target;
    private final boolean endTurn;

    public ActionChoice(Unit unit, Ability ability, Target target) {
        this.unit = unit;
        this.ability = ability;
        this.target = target;
        this.endTurn = false;
    }

    private ActionChoice(boolean endTurn) {
        this.unit = null;
        this.ability = null;
        this.target = null;
        this.endTurn = endTurn;
    }

    public static ActionChoice endTurn() {
        return new ActionChoice(true);
    }

    public Unit getUnit() {
        return unit;
    }

    public Ability getAbility() {
        return ability;
    }

    public Target getTarget() {
        return target;
    }

    public boolean isEndTurn() {
        return endTurn;
    }
}
