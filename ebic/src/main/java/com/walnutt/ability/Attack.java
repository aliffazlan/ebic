package com.walnutt.ability;

import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.combat.Encounter;
import com.walnutt.combat.NormalEncounter;
import com.walnutt.combat.WeightedEncounter;
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
        return owner != null && (owner.getUnitType() == UnitType.BASIC || owner.hasFreeAttack()) ? 0 : 1;
    }

    /**
     * getMoveCost ignores who the target is; this doesn't - Flint's High Noon grants a free
     * attack against one specific marked target rather than unconditionally like hasFreeAttack.
     */
    private int costAgainst(GameState state, Unit defender) {
        return defender.isFreeAttackTargetFor(owner) ? 0 : getMoveCost(state);
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
        if (owner == null || owner.isBlockedFrom(ActionKind.ATTACK)) {
            return false;
        }
        if (!(target instanceof UnitTarget unitTarget)) {
            return false;
        }
        Unit defender = unitTarget.getUnit();
        // Flint's High Noon: attacking a target it marked doesn't consume the turn's one
        // attack, so hasAttackedThisTurn is skipped entirely against that specific target.
        boolean free = defender.isFreeAttackTargetFor(owner);
        if (!free && owner.hasAttackedThisTurn()) {
            return false;
        }
        if (!state.canSpendMoves(costAgainst(state, defender))) {
            return false;
        }
        // Nothing to attack with: every attribute at 0 (stripped by Cripple, Decay or
        // Shrink Ray). Refusing here also removes it from getLegalTargets, so neither the
        // client's highlighting nor the bot offers an attack that could only deal 0.
        if (!owner.hasUsableAttribute()) {
            return false;
        }
        // Repeated from Ability.canUse because Attack deliberately doesn't call super -
        // a sealed-off unit (cloaked, frozen, imprisoned) can't be swung at either.
        if (!defender.isTargetable()) {
            return false;
        }
        if (defender.isDead() || defender.getTeam() == owner.getTeam()) {
            return false;
        }
        return canReach(state, owner, defender);
    }

    /**
     * Basic units fight on instinct: an encounter with one on either side is rolled
     * automatically rather than put to the players.
     *
     * This is the single biggest pacing lever in the game - ten of a side's fourteen units
     * are Basics, so most encounters in a match now resolve with no modal for anyone.
     */
    private static boolean isAutomatic(Unit attacker, Unit defender) {
        return attacker.getUnitType() == UnitType.BASIC || defender.getUnitType() == UnitType.BASIC;
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit defender = ((UnitTarget) target).getUnit();
        boolean free = defender.isFreeAttackTargetFor(owner);
        // Captured before the attack lands: a marked target's mark (and the free attack it
        // grants) is consumed by the very damage this attack deals, so costAgainst would read
        // false - and charge a move point after all - if it were computed any later than this.
        int cost = costAgainst(state, defender);

        CombatEngine.performAttack(state, buildEncounter(state, defender));

        if (!free) {
            owner.markAttacked();
        }
        state.spendMoves(cost);
    }

    /**
     * Which of the three encounter shapes this attack is, and therefore who gets asked
     * anything. Kept in one place so the "who is prompted" rule can't drift from the
     * "how is the attribute chosen" rule.
     */
    private Encounter buildEncounter(GameState state, Unit defender) {
        if (isAutomatic(owner, defender)) {
            return new WeightedEncounter(owner, defender);
        }
        if (!defender.hasUsableAttribute()) {
            // Nothing to defend with, so there is no decision to put to the defender - but
            // the attacker still picks, since which attribute they swing decides the damage.
            Attribute attackerChoice = state.getInputHandler().chooseAttribute(state, owner, defender);
            return new NormalEncounter(owner, defender, attackerChoice, null);
        }
        Attribute[] choices = state.getInputHandler().chooseAttributePair(state, owner, defender);
        return new NormalEncounter(owner, defender, choices[0], choices[1]);
    }
}
