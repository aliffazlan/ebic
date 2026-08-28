package com.walnutt.ability.impl;

import java.util.ArrayList;
import java.util.List;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.combat.EncounterResolver;
import com.walnutt.combat.WeightedEncounter;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * The strike carried by each of Maxwell's Killer Drones. Internal to the ability that
 * builds them, so it is deliberately not registered in AbilityFactory - the same
 * arrangement PylonDeathBurst uses.
 *
 * Drones have no Attack of their own, so this is their only offence. It resolves a
 * WeightedEncounter directly through EncounterResolver rather than CombatEngine, which
 * keeps PreAttackEvent/PostAttackEvent unpublished - the established pattern for an
 * automatic attack nobody chose an attribute for (Counterstrike, Cloak and Dagger's
 * ambush, Duel's forced hit).
 */
public class DroneAutoAttack extends PassiveAbility {
    private final int strikeRange;

    public DroneAutoAttack(int strikeRange) {
        super("Drone Strike",
            "This drone strikes a random enemy within " + strikeRange
                + " tile at the end of every turn, counting an enemy sharing its tile.");
        this.strikeRange = strikeRange;
    }

    @Override
    public void onTurnEnd(GameState state, TurnEndEvent event) {
        Unit drone = getOwner();
        if (drone == null || drone.isDead() || drone.getPosition() == null) {
            return;
        }
        // Its controller's turn only, so it fires exactly once a round and after that side
        // has finished moving it around.
        if (event.team() != drone.getTeam()) {
            return;
        }

        // getUnitsInRadius rather than getAdjacentUnits: tiles can hold more than one unit,
        // and an enemy standing ON the drone (distance 0) is very much in reach.
        List<Unit> candidates = new ArrayList<>();
        for (Unit unit : state.getMap().getUnitsInRadius(drone.getPosition(), strikeRange)) {
            if (!unit.isDead() && unit.getTeam() != drone.getTeam()) {
                candidates.add(unit);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }

        Unit victim = candidates.get(state.getRandom().nextInt(candidates.size()));
        DamageEvent strike = new EncounterResolver().resolve(state, new WeightedEncounter(drone, victim));
        strike.setCauseLabel("Killer Drone");
        victim.takeDamage(state, strike);
    }
}
