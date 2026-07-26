package com.walnutt.unit;

import com.walnutt.game.GameState;
import com.walnutt.game.RemovalReason;
import com.walnutt.game.Team;

/**
 * A clone/shadow spawned by an ability rather than part of a player's original
 * draft. An auto-piloted summon (Shadow Image, driven entirely by its ability's
 * own onUse() code) should NOT be added to Player.getUnits() - it only needs
 * GameState's active-unit registry (rendering/targeting/events). A player-
 * controllable summon (Psychic Projection's clone) DOES need to go into
 * Player.getUnits() so TurnManager/InputHandler can select it for that player's
 * turn - its owning ability is responsible for calling player.removeUnit(...) when
 * it should disappear (Player.getUnits() is not touched by despawnIfNoAdjacentEnemy
 * below, which only ever does map/registry cleanup via GameState.removeUnit).
 *
 * Pass the summoner's own HealthPool to the constructor for a shared-HP clone
 * (e.g. Shadow Image); pass a fresh one for an independent-HP clone (e.g. an
 * invulnerable Psychic Projection double).
 */
public class SummonedUnit extends Unit {
    private final Unit summoner;
    private final boolean despawnIfNoAdjacentEnemy;
    private final boolean occupiesTile;

    public SummonedUnit(String name, Team team, UnitStats baseStats, HealthPool healthPool,
                         Unit summoner, boolean despawnIfNoAdjacentEnemy) {
        this(name, team, baseStats, healthPool, summoner, despawnIfNoAdjacentEnemy, true);
    }

    /** occupiesTile=false for structures like Zenith's Pylons - attackable, but never block a tile. */
    public SummonedUnit(String name, Team team, UnitStats baseStats, HealthPool healthPool,
                         Unit summoner, boolean despawnIfNoAdjacentEnemy, boolean occupiesTile) {
        super(name, team, summoner.getUnitType(), baseStats, healthPool);
        this.summoner = summoner;
        this.despawnIfNoAdjacentEnemy = despawnIfNoAdjacentEnemy;
        this.occupiesTile = occupiesTile;
    }

    public Unit getSummoner() {
        return summoner;
    }

    @Override
    public boolean occupiesTile() {
        return occupiesTile;
    }

    @Override
    public void endTurn(GameState state) {
        super.endTurn(state);
        if (despawnIfNoAdjacentEnemy && !isDead() && !state.getMap().hasAdjacentEnemy(this)) {
            state.removeUnit(this, RemovalReason.DESPAWN);
        }
    }
}
