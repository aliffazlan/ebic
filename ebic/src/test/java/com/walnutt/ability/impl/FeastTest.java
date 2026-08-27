package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class FeastTest {

    private static Feast newFeast() {
        return new Feast(new AbilityDefinition("Feast", "active", "desc", Map.of(
            "cooldown", 7.0, "duration", 3.0, "attacks", 1.0, "lifesteal", 0.25, "root_duration", 1.0)));
    }

    @Test
    void automaticallyAttacksAndLifestealsAndRootsVictims_withoutRootingGrivath() {
        Unit grivath = new BasicUnit("Grivath", Team.PLAYER_ONE, new UnitStats(60, 0, 0, 750));
        grivath.getHealthPool().setCurrent(500);
        Feast feast = newFeast();
        grivath.addAbility(feast);
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 500));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(grivath);
        p2.addUnit(victim);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(grivath, map.getTile(new Position(0, 0)));
        map.moveUnit(victim, map.getTile(new Position(1, 0)));

        feast.onUse(state, new NoTarget());

        // The rework drops the self-root: Grivath keeps acting normally while feasting.
        assertFalse(grivath.hasStatus(StatusFlag.ROOTED), "Feast no longer roots Grivath himself");

        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));

        // STRENGTH (grivath, only nonzero stat) beats INTELLIGENCE (victim, only nonzero stat) deterministically.
        assertTrue(victim.getHealth() < 500, "the automatic attack should have dealt damage");
        assertTrue(grivath.getHealth() > 500, "lifesteal should have healed Grivath");
        assertTrue(victim.hasStatus(StatusFlag.ROOTED), "the attacked victim should still be rooted");
    }

    @Test
    void isANoTargetSelfCastRatherThanALeap() {
        Unit grivath = new BasicUnit("Grivath", Team.PLAYER_ONE, new UnitStats(60, 0, 0, 750));
        Feast feast = newFeast();
        grivath.addAbility(feast);

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(grivath);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(grivath, map.getTile(new Position(0, 0)));

        assertTrue(feast.canUse(state, new NoTarget()));
        assertFalse(feast.canUse(state, new TileTarget(map.getTile(new Position(1, 0)))),
            "Feast is no longer tile-targeted");

        feast.onUse(state, new NoTarget());
        assertTrue(grivath.getPosition().equals(new Position(0, 0)), "Feast no longer moves Grivath");
    }
}
