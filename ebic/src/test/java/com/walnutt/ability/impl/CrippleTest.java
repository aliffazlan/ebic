package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class CrippleTest {

    private static final Map<String, Double> STATS = Map.of(
        "stat_steal", 1.0, "defended_bonus", 3.0, "hp_steal", 5.0, "non_basic_multiplier", 2.0);

    private static Unit newGrivath() {
        Unit grivath = new BasicUnit("Grivath", Team.PLAYER_ONE, new UnitStats(60, 10, 10, 750));
        grivath.addAbility(new Cripple(new AbilityDefinition("Cripple", "passive", "desc", STATS)));
        return grivath;
    }

    private static GameState scenario(Unit grivath, Unit target) {
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(grivath);
        p2.addUnit(target);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(grivath, map.getTile(new Position(0, 0)));
        map.moveUnit(target, map.getTile(new Position(1, 0)));
        return state;
    }

    /**
     * The worked example from the rework brief: Grivath lands a hit on an elite that
     * defended with strength, so he takes 8 STR / 2 AGI / 2 INT and 10 HP off it.
     */
    @Test
    void aLandedHitOnANonBasicStealsDoubledStatsAndHealth() {
        Unit grivath = newGrivath();
        Unit target = new EliteUnit("Elite", Team.PLAYER_TWO, new UnitStats(40, 40, 40, 500));
        GameState state = scenario(grivath, target);

        // STRENGTH beats INTELLIGENCE, so this is a landed hit; the defender is recorded
        // as having defended with STRENGTH via the attribute pair below.
        CombatEngine.performAttack(state, grivath, target, Attribute.AGILITY, Attribute.STRENGTH);

        assertEquals(40 - 8, target.getAttributeValue(Attribute.STRENGTH), "defended attribute: (1+3) x2");
        assertEquals(40 - 2, target.getAttributeValue(Attribute.AGILITY), "other attributes: 1 x2");
        assertEquals(40 - 2, target.getAttributeValue(Attribute.INTELLIGENCE));

        assertEquals(60 + 8, grivath.getAttributeValue(Attribute.STRENGTH), "Grivath gains exactly what was lost");
        assertEquals(10 + 2, grivath.getAttributeValue(Attribute.AGILITY));
        assertEquals(10 + 2, grivath.getAttributeValue(Attribute.INTELLIGENCE));

        assertEquals(500 - 10, target.getMaxHealth(), "10 max HP stolen");
        assertEquals(750 + 10, grivath.getMaxHealth());
        assertEquals(750 + 10, grivath.getHealth(), "current HP rises with the ceiling, not just the cap");
    }

    @Test
    void aLandedHitOnABasicStealsTheUndoubledAmounts() {
        Unit grivath = newGrivath();
        Unit target = new BasicUnit("Basic", Team.PLAYER_TWO, new UnitStats(40, 40, 40, 500));
        GameState state = scenario(grivath, target);

        CombatEngine.performAttack(state, grivath, target, Attribute.AGILITY, Attribute.STRENGTH);

        assertEquals(40 - 4, target.getAttributeValue(Attribute.STRENGTH), "defended attribute: 1+3, no doubling");
        assertEquals(40 - 1, target.getAttributeValue(Attribute.AGILITY));
        assertEquals(500 - 5, target.getMaxHealth());
    }

    /** A successful defense still shaves the blocking attribute, but steals no health. */
    @Test
    void aDefendedHitStealsOnlyTheDefendedAttributeAndNoHealth() {
        Unit grivath = newGrivath();
        Unit target = new EliteUnit("Elite", Team.PLAYER_TWO, new UnitStats(40, 40, 40, 500));
        GameState state = scenario(grivath, target);

        // AGILITY beats STRENGTH, so the defender wins the matchup.
        CombatEngine.performAttack(state, grivath, target, Attribute.STRENGTH, Attribute.AGILITY);

        assertEquals(40 - 2, target.getAttributeValue(Attribute.AGILITY), "1 point, doubled for a non-basic");
        assertEquals(40, target.getAttributeValue(Attribute.STRENGTH), "untouched on a failed attack");
        assertEquals(40, target.getAttributeValue(Attribute.INTELLIGENCE), "untouched on a failed attack");
        assertEquals(500, target.getMaxHealth(), "no health is stolen when the attack is defended");
        assertEquals(750, grivath.getMaxHealth());
    }

    @Test
    void doesNotTriggerOnSomeoneElsesAttack() {
        Unit grivath = newGrivath();
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(60, 0, 0, 500));
        Unit target = new EliteUnit("Elite", Team.PLAYER_TWO, new UnitStats(40, 40, 40, 500));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(grivath);
        p1.addUnit(ally);
        p2.addUnit(target);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(grivath, map.getTile(new Position(0, 0)));
        map.moveUnit(ally, map.getTile(new Position(0, 1)));
        map.moveUnit(target, map.getTile(new Position(1, 0)));

        CombatEngine.performAttack(state, ally, target, Attribute.AGILITY, Attribute.STRENGTH);

        assertEquals(40, target.getAttributeValue(Attribute.STRENGTH), "only Grivath's own attacks drain");
        assertEquals(500, target.getMaxHealth());
    }
}
