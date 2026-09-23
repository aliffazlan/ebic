package com.walnutt.ui;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.game.sandbox.SandboxCommand;
import com.walnutt.unit.Unit;

/** Bundles what InputHandler gathered from the player into one action for TurnManager to run. */
public final class ActionChoice {
    private final Unit unit;
    private final Ability ability;
    private final Target target;
    private final boolean endTurn;
    private final SandboxCommand sandboxCommand;

    public ActionChoice(Unit unit, Ability ability, Target target) {
        this.unit = unit;
        this.ability = ability;
        this.target = target;
        this.endTurn = false;
        this.sandboxCommand = null;
    }

    private ActionChoice(boolean endTurn, SandboxCommand sandboxCommand) {
        this.unit = null;
        this.ability = null;
        this.target = null;
        this.endTurn = endTurn;
        this.sandboxCommand = sandboxCommand;
    }

    public static ActionChoice endTurn() {
        return new ActionChoice(true, null);
    }

    /** A sandbox tool use rather than a unit acting - see SandboxController. */
    public static ActionChoice sandbox(SandboxCommand command) {
        return new ActionChoice(false, command);
    }

    public boolean isSandbox() {
        return sandboxCommand != null;
    }

    public SandboxCommand getSandboxCommand() {
        return sandboxCommand;
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
