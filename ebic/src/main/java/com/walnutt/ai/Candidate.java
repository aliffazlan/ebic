package com.walnutt.ai;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.game.GameState;
import com.walnutt.ui.ActionChoice;
import com.walnutt.unit.Unit;

/** One legal (unit, ability, target) triple the bot could play this instant. */
public record Candidate(Unit unit, Ability ability, Target target) {

    public int moveCost(GameState state) {
        return ability.getMoveCost(state);
    }

    public ActionChoice toActionChoice() {
        return new ActionChoice(unit, ability, target);
    }
}
