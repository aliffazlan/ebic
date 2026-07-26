package com.walnutt.ability;

import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.game.GameState;
import com.walnutt.unit.ActionKind;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitType;

public class Attack extends Ability {
    public Attack() {
        super("Attack", "Basic attack against an adjacent enemy", false);
    }

    @Override
    public int getMoveCost(GameState state) {
        return owner != null && owner.getUnitType() == UnitType.BASIC ? 0 : 1;
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (owner == null || owner.hasAttackedThisTurn() || owner.isBlockedFrom(ActionKind.ATTACK)) {
            return false;
        }
        if (!state.canSpendMoves(getMoveCost(state))) {
            return false;
        }
        if (!(target instanceof UnitTarget unitTarget)) {
            return false;
        }
        Unit defender = unitTarget.getUnit();
        if (defender.isDead() || defender.getTeam() == owner.getTeam()) {
            return false;
        }
        return state.getMap().areAdjacent(owner.getPosition(), defender.getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit defender = ((UnitTarget) target).getUnit();

        Attribute attackerChoice = state.getInputHandler().chooseAttribute(state, owner);
        Attribute defenderChoice = state.getInputHandler().chooseAttribute(state, defender);

        CombatEngine.performAttack(state, owner, defender, attackerChoice, defenderChoice);

        owner.markAttacked();
        state.spendMoves(getMoveCost(state));
    }
}
