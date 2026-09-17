package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.ShadowLifespanEffect;
import com.walnutt.event.PostMoveEvent;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.unit.SummonedUnit;
import com.walnutt.unit.Unit;

/**
 * The one thing Mercurial's shadow can do: pull him back to where it stands.
 *
 * Implemented as "teleport the summoner ONTO the shadow" rather than "teleport him to the
 * position he left", which is what makes the design's own aside true for free - anything that
 * shifts the shadow shifts where the recall lands, so a translocated shadow is a translocated
 * escape route.
 *
 * Lives on the shadow rather than on Mercurial because it is the shadow the player selects and
 * spends a turn on. Killing the shadow therefore denies the recall, which is the counterplay.
 */
public class Recall extends Ability {

    public Recall(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 0));
        setRange(0); // self-cast: there is no distance to check, the shadow is the destination
    }

    /** The unit this shadow was cast off, or null if it is standing on its own somehow. */
    private Unit summoner() {
        return owner instanceof SummonedUnit shadow ? shadow.getSummoner() : null;
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target) || !(target instanceof NoTarget)) {
            return false;
        }
        Unit summoner = summoner();
        return summoner != null && !summoner.isDead() && owner.getPosition() != null;
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit summoner = summoner();
        Tile destination = state.getMap().getTile(owner.getPosition());
        if (summoner == null || summoner.isDead() || destination == null) {
            return;
        }

        // The shadow comes off the board FIRST, or its own body would be standing on the tile
        // Mercurial is about to land on. A forced relocation like Manifestation's own: no
        // markMoved and no PreMoveEvent (nothing should be able to cancel it) - but
        // PostMoveEvent still fires so anything tracking the summoner's position (a Feast
        // latch, Cloak's own onMove ambush) sees it, same as an ordinary step.
        owner.getActiveEffect(ShadowLifespanEffect.class).ifPresent(shadow -> shadow.remove(state));
        Position from = summoner.getPosition();
        state.getMap().moveUnit(summoner, destination);
        state.getEventBus().publish(state, new PostMoveEvent(summoner, from, destination.getPosition()));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
