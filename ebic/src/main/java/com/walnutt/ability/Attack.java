package com.walnutt.ability;

import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.status.Stat;
import com.walnutt.unit.ActionKind;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitType;

public class Attack extends Ability {
    public Attack() {
        super("Attack", "Basic attack against an enemy within this unit's attack range", false);
    }

    /**
     * Single source of truth for "could this unit basic-attack that one from where it
     * stands right now". Shared so nothing re-derives the rule and drifts.
     *
     * The lower bound is at least 1: tiles can legally hold more than one unit (Cloak
     * and Dagger stacks Evayne onto an occupied tile), so without a floor a ranged
     * unit could attack something standing on its own tile, which adjacency-based
     * targeting never permitted.
     */
    public static boolean canReach(GameState state, Unit attacker, Unit defender) {
        return canReachFrom(state, attacker, attacker.getPosition(), defender);
    }

    /**
     * The same rule evaluated from a hypothetical position, so a caller can ask "could
     * this unit attack that one if it stood there?" without actually moving it - what
     * the bot needs to tell an advancing step apart from a pointless one. Kept as an
     * overload rather than re-derived by the caller so the reach rule stays in one place.
     */
    public static boolean canReachFrom(GameState state, Unit attacker, Position from, Unit defender) {
        if (from == null || defender.getPosition() == null) {
            return false;
        }
        int distance = state.getMap().getDistance(from, defender.getPosition());
        int max = (int) attacker.getEffective(Stat.ATTACK_RANGE);
        int min = Math.max(1, attacker.getMinAttackRange());
        return distance >= min && distance <= max;
    }

    @Override
    public int getMoveCost(GameState state) {
        return owner != null && owner.getUnitType() == UnitType.BASIC ? 0 : 1;
    }

    /** Attack ignores the base `range` field entirely - its reach is the owner's stat. */
    @Override
    public int getRange() {
        return owner == null ? 1 : (int) owner.getEffective(Stat.ATTACK_RANGE);
    }

    @Override
    public int getMinRange() {
        return owner == null ? 0 : Math.max(1, owner.getMinAttackRange());
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
        return canReach(state, owner, defender);
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit defender = ((UnitTarget) target).getUnit();

        Attribute[] choices = state.getInputHandler().chooseAttributePair(state, owner, defender);
        Attribute attackerChoice = choices[0];
        Attribute defenderChoice = choices[1];

        CombatEngine.performAttack(state, owner, defender, attackerChoice, defenderChoice);

        owner.markAttacked();
        state.spendMoves(getMoveCost(state));
    }
}
