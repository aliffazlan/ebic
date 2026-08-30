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
        assertEquals(75, f.victim.getAttributeValue(Attribute.INTELLIGENCE), "100 less 25%");
        assertEquals(25, f.caster.getAttributeValue(Attribute.INTELLIGENCE));

        letThemEscape(f);

        assertFalse(f.victim.hasStatus(StatusFlag.STUNNED));
        // The whole toll is taken on the way in now. Taking a second helping on the way out is
        // the upgrade, not the baseline - see oblivion_confinement.json.
        assertEquals(75, f.victim.getAttributeValue(Attribute.INTELLIGENCE));
        assertEquals(25, f.caster.getAttributeValue(Attribute.INTELLIGENCE));
    }

    @Test
    void upgradedItTakesASecondHelpingWhenTheTargetReturns() {
        Fixture f = fixture();
        f.ability.upgrade();

        f.ability.onUse(f.state, new UnitTarget(f.victim));
        assertEquals(75, f.victim.getAttributeValue(Attribute.INTELLIGENCE));

        letThemEscape(f);

        // Reckoned from what is left, not from the original: 25% of 75 rounds to 19.
        assertEquals(56, f.victim.getAttributeValue(Attribute.INTELLIGENCE));
        assertEquals(44, f.caster.getAttributeValue(Attribute.INTELLIGENCE));
    }

    /** A cast made before the upgrade keeps the terms it was made under. */
    @Test
    void anImprisonmentAlreadyRunningIsNotRetroactivelyUpgraded() {
        Fixture f = fixture();
        f.ability.onUse(f.state, new UnitTarget(f.victim));
        f.ability.upgrade();

        letThemEscape(f);

        assertEquals(75, f.victim.getAttributeValue(Attribute.INTELLIGENCE));
    }
}
