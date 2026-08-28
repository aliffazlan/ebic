package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.Move;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.UnitDefinition;
import com.walnutt.effect.impl.DroneLifespanEffect;
import com.walnutt.game.GameState;
import com.walnutt.map.Tile;
import com.walnutt.map.TileType;
import com.walnutt.unit.HealthPool;
import com.walnutt.unit.SummonedUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitFactory;
import com.walnutt.unit.UnitStats;
import com.walnutt.unit.UnitType;

/**
 * Maxwell gadget - deploys a drone that can be moved but never attacks on command.
 *
 * Built by hand rather than through UnitFactory, which attaches Move AND Attack to
 * everything it builds; a drone gets Move plus its own automatic strike, and no Attack at
 * all. Its type comes from the prototype JSON, explicitly: inheriting Maxwell's ELITE
 * would make a 100 HP body pay out like a real elite everywhere a rule checks the type
 * (Grivath's Cripple doubles its steal, Valor's Duel multiplies its win bonus).
 *
 * It joins the player's roster instead of the summon registry, which is what makes it
 * selectable and movable - and, as a side benefit, is the only way its lifespan effect
 * ticks at all, since registered summons never receive startTurn/endTurn.
 */
public class KillerDrone extends Ability {
    private static final String DRONE_DEFINITION_ID = "maxwell_drone";
    private static final int FALLBACK_MAX_HP = 100;

    private final int duration;
    private final int strikeRange;

    public KillerDrone(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 4));
        setRange(definition.getInt("cast_range", 5));
        this.duration = definition.getInt("duration", 8);
        this.strikeRange = definition.getInt("strike_range", 1);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target) || !(target instanceof TileTarget tileTarget)) {
            return false;
        }
        // Not isWalkable(): a drone occupies no tile, so an enemy standing on the target
        // one is no reason to refuse the deployment - GameMap.moveUnit stacks it happily.
        // Terrain is the only real constraint.
        Tile tile = tileTarget.getTile();
        return tile.getType() != TileType.BLOCKED && isInRange(state, tile.getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Tile destination = ((TileTarget) target).getTile();
        Unit drone = buildDrone(state);

        state.getMap().moveUnit(drone, destination);
        state.getPlayer(owner.getTeam()).addUnit(drone);
        drone.addEffect(new DroneLifespanEffect(duration));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    private Unit buildDrone(GameState state) {
        UnitDefinition definition = state.getUnitDefinitions().get(DRONE_DEFINITION_ID);
        UnitStats stats = definition == null
            ? new UnitStats(20, 20, 20, FALLBACK_MAX_HP, 1)
            : new UnitStats(definition.strength(), definition.agility(), definition.intelligence(),
                definition.maxHp(), definition.effectiveAttackRange());
        String name = definition == null ? "Drone" : definition.name();
        UnitType type = definition == null ? UnitType.BASIC : UnitFactory.parseType(definition.type());

        Unit drone = new SummonedUnit(name, owner.getTeam(), type, stats,
            new HealthPool(stats.maxHealth()), owner, false, /* occupiesTile */ false);
        drone.addAbility(new Move());
        drone.addAbility(new DroneAutoAttack(strikeRange));
        return drone;
    }
}
