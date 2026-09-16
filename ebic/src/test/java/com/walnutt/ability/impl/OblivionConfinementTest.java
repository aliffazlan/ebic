package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.data.AbilityFactory;
import com.walnutt.data.JsonDataLoader;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class OblivionConfinementTest {

    private record Fixture(GameState state, OblivionConfinement ability, Unit caster, Unit victim) {
    }

    /** Built from the real JSON so the upgrade block is present and the numbers are the shipped ones. */
    private static Fixture fixture() {
        Unit caster = new BasicUnit("Harbinger", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        OblivionConfinement ability = (OblivionConfinement) AbilityFactory.create("oblivion_confinement",
            new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot())
                .loadAllAbilities().get("oblivion_confinement"));
        caster.addAbility(ability);
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 100, 100));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(caster);
        p2.addUnit(victim);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(caster, map.getTile(new Position(0, 0)));
        map.moveUnit(victim, map.getTile(new Position(0, 2)));
        return new Fixture(state, ability, caster, victim);
    }

    /** Ends and restarts the victim's turn, which is how the imprisonment expires - the "escape". */
    private static void letThemEscape(Fixture f) {
        f.victim.endTurn(f.state);
        f.victim.startTurn(f.state);
    }

    @Test
    void imprisonsAndStealsIntelligenceOnCastOnly() {
        Fixture f = fixture();
        UnitTarget target = new UnitTarget(f.victim);
        assertTrue(f.ability.canUse(f.state, target));
        f.ability.onUse(f.state, target);

        assertTrue(f.victim.hasStatus(StatusFlag.STUNNED));
        assertTrue(f.victim.hasStatus(StatusFlag.INVULNERABLE));
        assertEquals(65, f.victim.getAttributeValue(Attribute.INTELLIGENCE), "100 less 35%");
        assertEquals(35, f.caster.getAttributeValue(Attribute.INTELLIGENCE));

        letThemEscape(f);

        assertFalse(f.victim.hasStatus(StatusFlag.STUNNED));
        // The whole toll is taken on the way in, and nothing further happens on the way out -
        // there is no more "steal again on escape" mechanic, upgraded or not.
        assertEquals(65, f.victim.getAttributeValue(Attribute.INTELLIGENCE));
        assertEquals(35, f.caster.getAttributeValue(Attribute.INTELLIGENCE));
    }

    /** Upgraded: every blow the caster lands anywhere - not just the imprisonment cast - also steals intelligence. */
    @Test
    void upgradedItAlsoStealsIntelligenceOnAnyDamageDealt() {
        Fixture f = fixture();
        f.ability.upgrade();

        Unit bystander = new BasicUnit("Bystander", Team.PLAYER_TWO, new UnitStats(0, 0, 100, 100));
        f.state.getPlayers().get(1).addUnit(bystander);
        f.state.getMap().moveUnit(bystander, f.state.getMap().getTile(new Position(1, 0)));

        DamageEvent hit = new DamageEvent(f.caster, bystander, 10);
        bystander.takeDamage(f.state, hit);

        assertEquals(90, bystander.getAttributeValue(Attribute.INTELLIGENCE), "10% of 100 stolen just after the blow");
        assertEquals(10, f.caster.getAttributeValue(Attribute.INTELLIGENCE));
    }

    /** The passive steal always takes at least 1, even off a victim with very little intelligence left. */
    @Test
    void upgradedThePassiveStealAlwaysTakesAtLeastOne() {
        Fixture f = fixture();
        f.ability.upgrade();

        Unit weakling = new BasicUnit("Weakling", Team.PLAYER_TWO, new UnitStats(0, 0, 3, 100));
        f.state.getPlayers().get(1).addUnit(weakling);
        f.state.getMap().moveUnit(weakling, f.state.getMap().getTile(new Position(1, 1)));

        DamageEvent hit = new DamageEvent(f.caster, weakling, 10);
        weakling.takeDamage(f.state, hit);

        assertEquals(2, weakling.getAttributeValue(Attribute.INTELLIGENCE),
            "10% of 3 rounds to 0, floored up to the minimum of 1");
    }

    /** Only damage dealt AFTER the upgrade triggers the passive - it isn't retroactive either. */
    @Test
    void thePassiveStealOnlyAppliesToDamageDealtAfterTheUpgrade() {
        Fixture f = fixture();
        Unit bystander = new BasicUnit("Bystander", Team.PLAYER_TWO, new UnitStats(0, 0, 100, 100));
        f.state.getPlayers().get(1).addUnit(bystander);
        f.state.getMap().moveUnit(bystander, f.state.getMap().getTile(new Position(1, 0)));

        DamageEvent beforeUpgrade = new DamageEvent(f.caster, bystander, 10);
        bystander.takeDamage(f.state, beforeUpgrade);
        assertEquals(100, bystander.getAttributeValue(Attribute.INTELLIGENCE), "not upgraded yet, no passive steal");

        f.ability.upgrade();
        DamageEvent afterUpgrade = new DamageEvent(f.caster, bystander, 10);
        bystander.takeDamage(f.state, afterUpgrade);
        assertEquals(90, bystander.getAttributeValue(Attribute.INTELLIGENCE), "upgraded now, the passive fires");
    }
}
