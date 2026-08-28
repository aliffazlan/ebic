package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.AbilityFactory;
import com.walnutt.effect.impl.MimicEffect;
import com.walnutt.event.AbilityCastEvent;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.ChampionUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class MimicTest {

    private static final String FIREBLAST = "fireblast";
    private static final String SOUL_RIP = "soul_rip";
    private static final String PSYCHIC = "psychic_projection";

    private record Fixture(GameState state, GameMap map, Unit joker, Mimic mimic, Unit enemy) {

        /** Publishes a cast by `caster` exactly as TurnManager would. */
        void enemyCasts(Unit caster, Ability ability) {
            state.getEventBus().publish(state,
                new AbilityCastEvent(caster, ability, new UnitTarget(caster), AbilityCastEvent.Phase.PRE));
            state.getEventBus().publish(state,
                new AbilityCastEvent(caster, ability, new UnitTarget(caster), AbilityCastEvent.Phase.POST));
        }

        void endJokersTurn() {
            state.getEventBus().publish(state, new TurnEndEvent(Team.PLAYER_ONE));
            joker.endTurn(state);
        }

        void startJokersTurn() {
            joker.startTurn(state);
            state.setRemainingMoves(3);
        }

        Ability jokersCopyOf(String name) {
            return joker.getAbilities().stream().filter(a -> a.getName().equals(name)).findFirst().orElse(null);
        }
    }

    /** Real definitions, so the copy is built through the real AbilityFactory path. */
    private static Map<String, AbilityDefinition> definitions() {
        Map<String, AbilityDefinition> defs = new HashMap<>();
        defs.put(FIREBLAST, new AbilityDefinition("Fireblast", "active", "desc",
            Map.of("cooldown", 2.0, "cast_range", 3.0, "damage", 24.0, "burn_stacks", 3.0)));
        defs.put(SOUL_RIP, new AbilityDefinition("Soul Rip", "active", "desc",
            Map.of("cooldown", 4.0, "cast_range", 3.0)));
        // The escape hatch: real content, tagged so it can never be copied.
        defs.put(PSYCHIC, new AbilityDefinition("Psychic Projection", "active", "desc",
            Map.of("cooldown", 6.0, "cast_range", 4.0, "duration", 3.0), List.of(AbilityDefinition.NO_COPY)));
        return defs;
    }

    private static Mimic mimic() {
        return new Mimic(new AbilityDefinition("Mimic", "active", "desc", Map.of(
            "cooldown", 2.0, "cast_range", 3.0, "duration", 10.0, "steal_window", 3.0, "max_abilities", 1.0)));
    }

    private static Fixture fixture() {
        Unit joker = new ChampionUnit("Joker", Team.PLAYER_ONE, new UnitStats(54, 68, 85, 990, 2));
        Mimic mimic = mimic();
        joker.addAbility(mimic);

        Unit enemy = new EliteUnit("Enemy", Team.PLAYER_TWO, new UnitStats(40, 40, 40, 500, 1));
        Player one = new Player("P1", Team.PLAYER_ONE);
        Player two = new Player("P2", Team.PLAYER_TWO);
        one.addUnit(joker);
        two.addUnit(enemy);
        GameMap map = new GameMap(8);
        GameState state = new GameState(map, List.of(one, two), new Random(1));
        state.setAbilityDefinitions(definitions());
        state.setRemainingMoves(3);
        map.moveUnit(joker, map.getTile(new Position(0, 0)));
        map.moveUnit(enemy, map.getTile(new Position(0, 2)));
        return new Fixture(state, map, joker, mimic, enemy);
    }

    private static Ability build(GameState state, String id) {
        return AbilityFactory.create(id, state.getAbilityDefinitions().get(id));
    }

    @Test
    void copiesTheAbilityAnEnemyLastCastNearby() {
        Fixture f = fixture();
        f.enemyCasts(f.enemy, build(f.state, FIREBLAST));

        assertTrue(f.mimic.canUse(f.state, new UnitTarget(f.enemy)));
        f.mimic.onUse(f.state, new UnitTarget(f.enemy));

        assertNotNull(f.jokersCopyOf("Fireblast"), "the stolen ability is now part of Joker's kit");
        assertSame(f.jokersCopyOf("Fireblast"), f.mimic.getEquippedCopy());
    }

    /** A fresh steal arrives ready, which is what makes it a play rather than a setup move. */
    @Test
    void aFirstCopyArrivesOffCooldown() {
        Fixture f = fixture();
        f.enemyCasts(f.enemy, build(f.state, FIREBLAST));

        f.mimic.onUse(f.state, new UnitTarget(f.enemy));

        assertTrue(f.jokersCopyOf("Fireblast").isReady());
    }

    @Test
    void anEnemyWhoHasCastNothingIsNotALegalTarget() {
        Fixture f = fixture();

        assertFalse(f.mimic.canUse(f.state, new UnitTarget(f.enemy)));
        assertTrue(f.mimic.getLegalTargets(f.state).isEmpty());
    }

    @Test
    void ignoresCastsFromOutsideItsRange() {
        Fixture f = fixture();
        f.map.moveUnit(f.enemy, f.map.getTile(new Position(0, 6)));

        f.enemyCasts(f.enemy, build(f.state, FIREBLAST));

        assertFalse(f.mimic.canUse(f.state, new UnitTarget(f.enemy)));
    }

    /** Joker's own side is not worth watching - Mimic steals, it does not borrow. */
    @Test
    void ignoresFriendlyCasts() {
        Fixture f = fixture();
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(20, 20, 20, 300));
        f.map.moveUnit(ally, f.map.getTile(new Position(1, 0)));

        f.enemyCasts(ally, build(f.state, FIREBLAST));

        assertFalse(f.mimic.canUse(f.state, new UnitTarget(ally)));
    }

    /**
     * Move and Attack are built directly in Java rather than from a JSON file, so they
     * carry no definition id - which is exactly what makes them uncopyable, with no list
     * of exclusions to keep up to date.
     */
    @Test
    void neverRecordsMoveOrAttack() {
        Fixture f = fixture();

        f.enemyCasts(f.enemy, new Move());
        f.enemyCasts(f.enemy, new Attack());

        assertFalse(f.mimic.canUse(f.state, new UnitTarget(f.enemy)));
    }

    /** The design-side escape hatch: some abilities break if they leave the unit they were built for. */
    @Test
    void neverRecordsAnAbilityTaggedNoCopy() {
        Fixture f = fixture();

        f.enemyCasts(f.enemy, build(f.state, PSYCHIC));

        assertFalse(f.mimic.canUse(f.state, new UnitTarget(f.enemy)),
            "psychic_projection carries no_copy, so there is nothing to take");
    }

    @Test
    void onlyTheMostRecentCastCounts() {
        Fixture f = fixture();
        f.enemyCasts(f.enemy, build(f.state, FIREBLAST));
        f.enemyCasts(f.enemy, build(f.state, SOUL_RIP));

        f.mimic.onUse(f.state, new UnitTarget(f.enemy));

        assertNotNull(f.jokersCopyOf("Soul Rip"));
        assertNull(f.jokersCopyOf("Fireblast"), "the earlier cast was overwritten, not queued");
    }

    /** The window is counted in Joker's own turns, and a memory that runs out is gone. */
    @Test
    void aMemoryExpiresAfterTheStealWindow() {
        Fixture f = fixture();
        f.enemyCasts(f.enemy, build(f.state, FIREBLAST));

        for (int turn = 0; turn < 2; turn++) {
            f.endJokersTurn();
            f.startJokersTurn();
        }
        assertTrue(f.mimic.canUse(f.state, new UnitTarget(f.enemy)), "still remembered on turn 3 of 3");

        f.endJokersTurn();
        f.startJokersTurn();

        assertFalse(f.mimic.canUse(f.state, new UnitTarget(f.enemy)));
    }

    @Test
    void aSecondStealReplacesTheFirstRatherThanStacking() {
        Fixture f = fixture();
        f.enemyCasts(f.enemy, build(f.state, FIREBLAST));
        f.mimic.onUse(f.state, new UnitTarget(f.enemy));

        f.enemyCasts(f.enemy, build(f.state, SOUL_RIP));
        f.mimic.onUse(f.state, new UnitTarget(f.enemy));

        assertNull(f.jokersCopyOf("Fireblast"), "the first copy was handed back");
        assertNotNull(f.jokersCopyOf("Soul Rip"));
        assertEquals(1, f.joker.getEffects().stream()
            .filter(e -> !e.isExpired() && e instanceof MimicEffect).count(),
            "and only one Mimic entry is left in the sidebar");
    }

    /**
     * The point of retaining instances: a copy parked by a replacement keeps cooling down
     * in the background, so stealing it back later returns it as Joker left it.
     */
    @Test
    void aParkedCopyKeepsCoolingDownAndReturnsWithItsRemainingCooldown() {
        Fixture f = fixture();
        f.enemyCasts(f.enemy, build(f.state, SOUL_RIP));
        f.mimic.onUse(f.state, new UnitTarget(f.enemy));
        Ability soulRip = f.jokersCopyOf("Soul Rip");
        soulRip.increaseCooldown(4);

        // Replace it, then let two of Joker's turns pass while it sits parked.
        f.enemyCasts(f.enemy, build(f.state, FIREBLAST));
        f.mimic.onUse(f.state, new UnitTarget(f.enemy));
        assertEquals(List.of(soulRip), f.mimic.getHeldAbilities());

        f.endJokersTurn();
        f.startJokersTurn();
        f.endJokersTurn();
        f.startJokersTurn();

        assertEquals(2, soulRip.getCurrentCooldown(), "ticked twice while parked");

        f.enemyCasts(f.enemy, build(f.state, SOUL_RIP));
        f.mimic.onUse(f.state, new UnitTarget(f.enemy));

        assertSame(soulRip, f.jokersCopyOf("Soul Rip"), "the same instance came back");
        assertEquals(2, soulRip.getCurrentCooldown(), "carrying the cooldown it left with, not a fresh one");
    }

    @Test
    void theCopyIsUnequippedWhenItsDurationRunsOut() {
        Fixture f = fixture();
        f.enemyCasts(f.enemy, build(f.state, FIREBLAST));
        f.mimic.onUse(f.state, new UnitTarget(f.enemy));
        Ability copy = f.jokersCopyOf("Fireblast");

        for (int turn = 0; turn < 10; turn++) {
            f.endJokersTurn();
            f.startJokersTurn();
        }

        assertNull(f.jokersCopyOf("Fireblast"), "the loan is over");
        assertNull(f.mimic.getEquippedCopy());
        assertEquals(List.of(copy), f.mimic.getHeldAbilities(), "but the instance is kept for next time");
    }

    /** A dead enemy has nothing left to study. */
    @Test
    void forgetsAnEnemyThatDies() {
        Fixture f = fixture();
        f.enemyCasts(f.enemy, build(f.state, FIREBLAST));
        f.enemy.instantKill(f.state, f.joker);

        f.endJokersTurn();
        f.startJokersTurn();

        assertFalse(f.mimic.canUse(f.state, new UnitTarget(f.enemy)));
    }

    /** Mimic is a plain unit-targeted ability; the tile shape is not one it accepts. */
    @Test
    void refusesANonUnitTarget() {
        Fixture f = fixture();
        f.enemyCasts(f.enemy, build(f.state, FIREBLAST));

        Target tile = new TileTarget(f.map.getTile(new Position(1, 0)));
        assertFalse(f.mimic.canUse(f.state, tile));
    }
}
