package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.RemovalReason;
import com.walnutt.map.Tile;
import com.walnutt.status.EffectCategory;
import com.walnutt.unit.Unit;

/**
 * Branch's pending Sprout teleport, resolving at the START of Branch's next turn.
 *
 * Timing matters here and the ordinary duration machinery gets it wrong: effects tick
 * in Unit.endTurn, so a 1-turn delay would expire the moment Branch ended the turn they
 * cast on, teleporting before the opponent ever got to act. So tick() is a no-op and the
 * countdown happens in onTurnStart instead, which gives the opponent a full turn to hunt
 * the Branchling down. InfernalBladeEffect uses the same "opt out of tick(), drive it
 * from a turn hook" shape.
 *
 * The Branchling is removed with DESPAWN rather than killed: consuming your own summon
 * isn't a kill, and publishing a KillEvent for it would feed anything watching for kills
 * - a Doomed Branch could otherwise clear its own curse by cashing in a Branchling.
 */
public class SproutEffect extends Effect {
    private final Unit caster;
    private final Unit branchling;
    private final int barrierHp;
    private final int barrierDuration;
    private boolean resolved;

    public SproutEffect(Unit caster, Unit branchling, int delay, int barrierHp, int barrierDuration) {
        super("Sprout",
            "Teleporting to a planted Branchling at the start of this unit's next turn. On arrival, "
                + "this unit and its neighbours gain a " + barrierHp + " health barrier. Destroying "
                + "the Branchling first cancels the teleport.",
            delay);
        this.caster = caster;
        this.branchling = branchling;
        this.barrierHp = barrierHp;
        this.barrierDuration = barrierDuration;
        this.category = EffectCategory.BUFF;
    }

    @Override
    public String getExtraInfo() {
        if (branchling.isDead()) {
            return "Branchling destroyed - teleport cancelled";
        }
        return "Teleports in " + getRemainingTurns() + " turn(s)";
    }

    /** Deliberately empty - see the class comment; onTurnStart drives the countdown. */
    @Override
    public void tick() {
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        Unit owner = getOwner();
        if (resolved || owner == null || isExpired() || event.team() != owner.getTeam()) {
            return;
        }
        setRemainingTurns(getRemainingTurns() - 1);
        if (getRemainingTurns() <= 0) {
            resolve(state);
        }
    }

    /** Safety net: if this is removed some other way, the Branchling still goes with it. */
    @Override
    public void onExpire(GameState state) {
        if (!resolved) {
            consumeBranchling(state);
        }
    }

    private void resolve(GameState state) {
        resolved = true;
        if (branchling.isDead()) {
            return; // destroyed before the delay elapsed - teleport cancelled
        }
        // The Branchling is consumed either way. Clearing it up front matters when the
        // caster died mid-delay: a registered summon never ticks, so nothing else would
        // ever remove it and it would sit on the board for the rest of the match.
        Tile destination = state.getMap().getTile(branchling.getPosition());
        consumeBranchling(state);

        if (caster == null || caster.isDead() || destination == null) {
            return;
        }
        state.getMap().moveUnit(caster, destination);

        grantBarrier(caster);
        for (Unit ally : state.getMap().getAdjacentUnits(destination.getPosition(),
            u -> u.getTeam() == caster.getTeam() && u != caster && !u.isDead())) {
            grantBarrier(ally);
        }
    }

    private void consumeBranchling(GameState state) {
        if (!branchling.isDead()) {
            state.removeUnit(branchling, RemovalReason.DESPAWN);
        }
    }

    private void grantBarrier(Unit unit) {
        unit.addEffect(new BarrierEffect("Sprout Barrier",
            "A shell of bark absorbing the next " + barrierHp + " damage.", barrierDuration, barrierHp));
    }
}
