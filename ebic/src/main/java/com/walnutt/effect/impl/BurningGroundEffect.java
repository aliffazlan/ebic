package com.walnutt.effect.impl;

import java.util.List;

import com.walnutt.effect.Effect;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.status.EffectCategory;
import com.walnutt.unit.Unit;

/**
 * Ember's Eruption: a patch of burning ground. Lives on the CASTER while remembering a
 * board position (the same shape OrbEffect uses for Sanity's Eclipse) because effects
 * only tick on units the turn loop runs, and a tile isn't one.
 *
 * A unit "ends its turn" on the fire during its own controller's turn end, so this
 * deliberately does NOT filter the event to the caster's team the way most turn hooks
 * do - it has to fire on both players' turn ends. Instead it filters the OCCUPANTS to
 * whichever team just ended, which is what makes the trigger precise and stops a unit
 * being burned twice in one round.
 */
public class BurningGroundEffect extends Effect {
    private final Unit caster;
    private final Position tile;
    private final int burnStacks;

    public BurningGroundEffect(Unit caster, Position tile, int duration, int burnStacks) {
        super("Eruption",
            "A patch of burning ground. Enemies that end their turn on it catch fire, gaining "
                + burnStacks + " stack(s) of Burn.",
            duration);
        this.caster = caster;
        this.tile = tile;
        this.burnStacks = burnStacks;
        this.category = EffectCategory.NEUTRAL;
    }

    public Position getTile() {
        return tile;
    }

    /**
     * Every patch of ground currently alight. These live on their casters rather than on
     * the tiles, so finding them means sweeping active units - one definition shared by
     * Eruption's re-cast guard and the snapshot mapper's overlay, so what the rules
     * consider burning and what the board draws as burning can never drift apart.
     *
     * A dead caster's fire goes out (see onTurnEnd), and a dead unit keeps its effects,
     * so owners that are dead are skipped.
     */
    public static List<BurningGroundEffect> activeGrounds(GameState state) {
        return Effect.activeInstances(state, BurningGroundEffect.class);
    }

    /** True if `position` is already alight - Eruption refuses to re-ignite it. */
    public static boolean isBurning(GameState state, Position position) {
        for (BurningGroundEffect ground : activeGrounds(state)) {
            if (ground.getTile().equals(position)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onTurnEnd(GameState state, TurnEndEvent event) {
        if (isExpired() || caster.isDead()) {
            return;
        }
        Tile ground = state.getMap().getTile(tile);
        if (ground == null) {
            return;
        }
        for (Unit occupant : List.copyOf(ground.getOccupants())) {
            if (occupant.getTeam() != event.team() || occupant.isDead()) {
                continue;
            }
            // Ember's own side walks through the flames unharmed.
            if (occupant.getTeam() == caster.getTeam()) {
                continue;
            }
            BurnEffect.apply(state, occupant, caster, burnStacks);
        }
    }
}
