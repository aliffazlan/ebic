package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.AbilityFactory;
import com.walnutt.data.JsonDataLoader;
import com.walnutt.effect.impl.AcidPoolEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Shawl - a pool of acid that softens whoever stands in it and scalds whoever lingers. */
class AcidicBrewTest {

    private static final Map<String, AbilityDefinition> DEFINITIONS =
        new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot()).loadAllAbilities();

    private record Fixture(GameState state, AcidicBrew brew, Unit shawl, Unit enemy, Unit ally) {
    }

    private static Fixture fixture() {
        Unit shawl = new EliteUnit("Shawl", Team.PLAYER_ONE, new UnitStats(48, 32, 54, 760));
        AcidicBrew brew = (AcidicBrew) AbilityFactory.create("acidic_brew", DEFINITIONS.get("acidic_brew"));
        shawl.addAbility(brew);
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 500));
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 500));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(shawl);
        p1.addUnit(ally);
        p2.addUnit(enemy);

        GameMap map = new GameMap(4);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(shawl, map.getTile(new Position(0, 0)));
        map.moveUnit(enemy, map.getTile(new Position(0, 2)));
        map.moveUnit(ally, map.getTile(new Position(0, 1)));
        return new Fixture(state, brew, shawl, enemy, ally);
    }

    private static void spillOn(Fixture f, Position position) {
        f.brew.onUse(f.state, new TileTarget(f.state.getMap().getTile(position)));
    }

    /** Hits `target` for 10 from a neutral source, so any change is the acid's doing. */
    private static int hitFor10(Fixture f, Unit source, Unit target) {
        int before = target.getHealth();
        target.takeDamage(f.state, new DamageEvent(source, target, 10));
        return before - target.getHealth();
    }

    @Test
    void enemiesStandingInTheAcidTakeExtraDamageFromEverySource() {
        Fixture f = fixture();
        spillOn(f, new Position(0, 2)); // the tile the enemy is on

        assertEquals(20, hitFor10(f, f.shawl, f.enemy), "10 dealt plus 10 from the acid");
    }

    @Test
    void steppingOutOfTheAcidEndsTheAmplificationWithNothingToCleanse() {
        Fixture f = fixture();
        spillOn(f, new Position(0, 2));
        assertEquals(20, hitFor10(f, f.shawl, f.enemy));

        // The amplification is checked against where the victim is standing right now, rather
        // than applied as a debuff on entry - so walking out is genuinely walking out.
        f.state.getMap().moveUnit(f.enemy, f.state.getMap().getTile(new Position(2, 0)));

        assertEquals(10, hitFor10(f, f.shawl, f.enemy));
        assertTrue(f.enemy.getEffects().isEmpty(), "nothing was ever put on the unit to cleanse");
    }

    @Test
    void shawlsOwnSideWadesThroughItUnharmed() {
        Fixture f = fixture();
        spillOn(f, new Position(0, 1)); // the tile the ALLY is on

        assertEquals(10, hitFor10(f, f.enemy, f.ally), "no amplification against his own");

        AcidPoolEffect pool = AcidPoolEffect.activePools(f.state).get(0);
        int before = f.ally.getHealth();
        pool.onTurnEnd(f.state, new TurnEndEvent(Team.PLAYER_ONE));
        assertEquals(before, f.ally.getHealth(), "and no tick either");
    }

    @Test
    void anEnemyEndingItsTurnInTheAcidIsScalded() {
        Fixture f = fixture();
        spillOn(f, new Position(0, 2));
        AcidPoolEffect pool = AcidPoolEffect.activePools(f.state).get(0);
        int before = f.enemy.getHealth();

        // Only the side whose turn just ended, so nobody is scalded twice a round.
        pool.onTurnEnd(f.state, new TurnEndEvent(Team.PLAYER_ONE));
        assertEquals(before, f.enemy.getHealth());

        pool.onTurnEnd(f.state, new TurnEndEvent(Team.PLAYER_TWO));
        // 5 from the tick, amplified by 10 by the acid the victim is standing in.
        assertEquals(before - 15, f.enemy.getHealth());
    }

    @Test
    void theBaseBrewCoversExactlyOneTile() {
        Fixture f = fixture();
        spillOn(f, new Position(0, 2));
        AcidPoolEffect pool = AcidPoolEffect.activePools(f.state).get(0);

        assertEquals(0, pool.getRadius());
        assertTrue(pool.covers(f.state, new Position(0, 2)));
        assertFalse(pool.covers(f.state, new Position(0, 1)), "a neighbour is dry");
    }

    @Test
    void upgradingWidensThePoolAndLengthensTheThrow() {
        Fixture f = fixture();
        Ability brew = f.brew;
        assertEquals(2, brew.getRange());

        brew.upgrade();

        assertEquals(3, brew.getRange(), "cast_range, re-applied by the base class");
        spillOn(f, new Position(0, 2));
        AcidPoolEffect pool = AcidPoolEffect.activePools(f.state).get(0);
        assertEquals(1, pool.getRadius());
        assertTrue(pool.covers(f.state, new Position(0, 1)), "the neighbour is caught now");
        // Which means the ally's tile is splashed too - and he still walks through it dry.
        assertEquals(10, hitFor10(f, f.enemy, f.ally));
        assertEquals(20, hitFor10(f, f.shawl, f.enemy));
    }

    @Test
    void groundAlreadyCoveredCanBeDousedAgain() {
        Fixture f = fixture();
        spillOn(f, new Position(0, 2));
        f.brew.decreaseCooldown(99);

        // Unlike Eruption, re-spilling is allowed: refreshing a pool about to run out is a
        // fair use of the cooldown, and two pools genuinely stacking is a real choice.
        assertTrue(f.brew.canUse(f.state, new TileTarget(f.state.getMap().getTile(new Position(0, 2)))));
        spillOn(f, new Position(0, 2));

        assertEquals(2, AcidPoolEffect.activePools(f.state).size());
        assertEquals(30, hitFor10(f, f.shawl, f.enemy), "10 dealt, plus 10 from each pool");
    }

    @Test
    void itIsATileCastRatherThanAUnitCast() {
        Fixture f = fixture();

        assertFalse(f.brew.canUse(f.state, new UnitTarget(f.enemy)));
        assertTrue(f.brew.canUse(f.state, new TileTarget(f.state.getMap().getTile(new Position(0, 2)))),
            "including the tile an enemy is standing on - that is the point");
    }
}
