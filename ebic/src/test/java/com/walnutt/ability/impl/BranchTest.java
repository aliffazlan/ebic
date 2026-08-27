package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.JsonDataLoader;
import com.walnutt.effect.impl.BarrierEffect;
import com.walnutt.effect.impl.OvergrowthEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitType;
import com.walnutt.unit.UnitStats;

class BranchTest {

    private Unit branch;
    private GameState state;
    private GameMap map;
    private Player p1;
    private Player p2;

    private void setUpBoard() {
        branch = new EliteUnit("Branch", Team.PLAYER_ONE, new UnitStats(44, 16, 36, 620, 2));
        p1 = new Player("P1", Team.PLAYER_ONE);
        p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(branch);
        map = new GameMap(5);
        state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        // Real definitions so Branchlings get their aura wired the way a match would.
        JsonDataLoader loader = new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot());
        state.setUnitDefinitions(loader.loadAllUnits());
        state.setAbilityDefinitions(loader.loadAllAbilities());
        map.moveUnit(branch, map.getTile(new Position(0, 0)));
    }

    private Overgrowth newOvergrowth() {
        return new Overgrowth(new AbilityDefinition("Overgrowth", "active", "desc",
            Map.of("cooldown", 4.0, "duration", 3.0, "cast_range", 3.0)));
    }

    private Sprout newSprout() {
        return new Sprout(new AbilityDefinition("Sprout", "active", "desc",
            Map.of("cooldown", 6.0, "delay", 1.0, "barrier", 20.0, "barrier_duration", 2.0)));
    }

    private long branchlingCount() {
        return state.getAllActiveUnits().stream().filter(u -> u.getName().equals("Branchling")).count();
    }

    @Test
    void overgrowthRingsTheTargetTileWithSixBranchlings() {
        setUpBoard();
        Overgrowth overgrowth = newOvergrowth();
        branch.addAbility(overgrowth);

        overgrowth.onUse(state, new TileTarget(map.getTile(new Position(0, 2))));

        assertEquals(6, branchlingCount(), "a hex tile has exactly six neighbours");
        // The centre itself is never planted on - that's what lets you ring an enemy.
        assertTrue(map.getTile(new Position(0, 2)).getOccupants().isEmpty());
    }

    @Test
    void overgrowthSkipsTilesThatAreAlreadyOccupied() {
        setUpBoard();
        Unit blocker = new BasicUnit("Blocker", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 100));
        p2.addUnit(blocker);
        map.moveUnit(blocker, map.getTile(new Position(0, 1)));  // one of the six ring tiles

        Overgrowth overgrowth = newOvergrowth();
        branch.addAbility(overgrowth);
        overgrowth.onUse(state, new TileTarget(map.getTile(new Position(0, 2))));

        assertEquals(5, branchlingCount(), "the occupied ring tile is skipped");
    }

    @Test
    void branchlingsWitherWhenTheOvergrowthExpires() {
        setUpBoard();
        Overgrowth overgrowth = newOvergrowth();
        branch.addAbility(overgrowth);
        overgrowth.onUse(state, new TileTarget(map.getTile(new Position(0, 2))));
        assertEquals(6, branchlingCount());

        OvergrowthEffect effect = branch.getActiveEffect(OvergrowthEffect.class).orElseThrow();
        while (!effect.isExpired()) {
            effect.tick();
        }
        branch.removeExpiredEffects(state);

        assertEquals(0, branchlingCount(), "every Branchling should be despawned");
    }

    /** Branchlings can't move or attack at all - they're a body and an aura, nothing else. */
    @Test
    void branchlingsHaveNoMoveOrAttack() {
        setUpBoard();
        Overgrowth overgrowth = newOvergrowth();
        branch.addAbility(overgrowth);
        overgrowth.onUse(state, new TileTarget(map.getTile(new Position(0, 2))));

        Unit branchling = state.getAllActiveUnits().stream()
            .filter(u -> u.getName().equals("Branchling")).findFirst().orElseThrow();

        assertTrue(branchling.getActiveAbilities().isEmpty(), "no Move, no Attack, nothing usable");
        assertTrue(branchling.occupiesTile(), "unlike Pylons, Branchlings body-block");
    }

    @Test
    void branchlingAurasStackOnASharedNeighbour() {
        setUpBoard();
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 500));
        p1.addUnit(ally);
        map.moveUnit(ally, map.getTile(new Position(0, 2)));
        ally.getHealthPool().setCurrent(100);

        Overgrowth overgrowth = newOvergrowth();
        branch.addAbility(overgrowth);
        overgrowth.onUse(state, new TileTarget(map.getTile(new Position(0, 2))));
        assertEquals(6, branchlingCount());

        state.getEventBus().publish(state, new TurnEndEvent(Team.PLAYER_ONE));

        // 6 Branchlings x 8 healing each, all landing on the one ally in the middle.
        assertEquals(100 + (6 * 8), ally.getHealth(), "overlapping auras stack");
    }

    @Test
    void sproutTeleportsBranchToItsBranchlingAndShieldsTheLanding() {
        setUpBoard();
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 500));
        p1.addUnit(ally);
        map.moveUnit(ally, map.getTile(new Position(3, 1)));

        Sprout sprout = newSprout();
        branch.addAbility(sprout);
        sprout.onUse(state, new TileTarget(map.getTile(new Position(3, 0))));
        assertEquals(1, branchlingCount());

        // Resolves at the start of Branch's NEXT turn, not when this one ends.
        branch.endTurn(state);
        assertEquals(1, branchlingCount(), "still pending after the casting turn ends");
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));

        assertEquals(new Position(3, 0), branch.getPosition(), "Branch teleports to the Branchling");
        assertEquals(0, branchlingCount(), "the Branchling is consumed");
        assertTrue(branch.getActiveEffect(BarrierEffect.class).isPresent(), "self barrier");
        assertTrue(ally.getActiveEffect(BarrierEffect.class).isPresent(), "adjacent ally barrier");
    }

    @Test
    void killingTheBranchlingCancelsTheTeleportButNotTheCooldown() {
        setUpBoard();
        Sprout sprout = newSprout();
        branch.addAbility(sprout);
        sprout.onUse(state, new TileTarget(map.getTile(new Position(3, 0))));
        assertFalse(sprout.isReady(), "the cooldown is spent at cast time");

        Unit branchling = state.getAllActiveUnits().stream()
            .filter(u -> u.getName().equals("Branchling")).findFirst().orElseThrow();
        Unit killer = new BasicUnit("Killer", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 100));
        p2.addUnit(killer);
        branchling.takeDamage(state, new DamageEvent(killer, branchling, 9999));
        assertTrue(branchling.isDead());

        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));

        assertNotEquals(new Position(3, 0), branch.getPosition(), "no teleport");
        assertTrue(branch.getActiveEffect(BarrierEffect.class).isEmpty(), "and no barrier");
        assertFalse(sprout.isReady(), "the cooldown is still spent");
    }

    /**
     * A Branchling is a disposable 50 HP body. Typing it off its summoner would make it
     * count as an ELITE, and rules that pay out more against non-basics (Grivath's
     * Cripple doubling its steal, Duel's win multiplier) would turn it into free food.
     */
    @Test
    void branchlingsAreBasicUnitsNotElitesLikeTheirSummoner() {
        setUpBoard();
        Overgrowth overgrowth = newOvergrowth();
        branch.addAbility(overgrowth);
        overgrowth.onUse(state, new TileTarget(map.getTile(new Position(0, 2))));

        Unit branchling = state.getAllActiveUnits().stream()
            .filter(u -> u.getName().equals("Branchling")).findFirst().orElseThrow();

        assertEquals(UnitType.ELITE, branch.getUnitType());
        assertEquals(UnitType.BASIC, branchling.getUnitType(), "taken from branchling.json, not from Branch");
    }

    /**
     * Registered summons never tick, so if the caster dies mid-delay nothing else would
     * ever clean the Branchling up - it would sit on the board for the rest of the match.
     */
    @Test
    void sproutStillConsumesItsBranchlingIfBranchDiesBeforeTheTeleport() {
        setUpBoard();
        Sprout sprout = newSprout();
        branch.addAbility(sprout);
        sprout.onUse(state, new TileTarget(map.getTile(new Position(3, 0))));
        assertEquals(1, branchlingCount());

        Unit killer = new BasicUnit("Killer", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 100));
        p2.addUnit(killer);
        branch.takeDamage(state, new DamageEvent(killer, branch, 99999));
        assertTrue(branch.isDead());

        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));

        assertEquals(0, branchlingCount(), "the Branchling must not be orphaned on the board");
    }

    /**
     * The reported bug: with the delay counted down in Unit.endTurn, casting Sprout and
     * ending your turn resolved the teleport immediately, so the opponent never got a
     * window to hunt the Branchling down. It must survive a full opposing turn.
     */
    @Test
    void sproutDoesNotResolveUntilBranchsNextTurnStarts() {
        setUpBoard();
        Sprout sprout = newSprout();
        branch.addAbility(sprout);
        sprout.onUse(state, new TileTarget(map.getTile(new Position(3, 0))));
        Position castPosition = branch.getPosition();

        // Branch ends the turn they cast on - nothing should happen yet.
        branch.endTurn(state);
        state.getEventBus().publish(state, new TurnEndEvent(Team.PLAYER_ONE));
        assertEquals(castPosition, branch.getPosition(), "no teleport when the casting turn ends");
        assertEquals(1, branchlingCount());

        // A whole opposing turn passes - still pending, so it can be interrupted.
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_TWO));
        state.getEventBus().publish(state, new TurnEndEvent(Team.PLAYER_TWO));
        assertEquals(castPosition, branch.getPosition(), "still pending through the opponent's turn");
        assertEquals(1, branchlingCount());

        // Branch's next turn begins - now it lands.
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));
        assertEquals(new Position(3, 0), branch.getPosition(), "resolves at the start of Branch's next turn");
        assertEquals(0, branchlingCount());
    }
}
