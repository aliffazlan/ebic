package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.UpgradeDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class SanguineTest {

    private static final Map<String, Double> BASE_STATS = Map.of(
        "bonus_pct_damage", 0.025, "max_hp_heal", 0.4);
    private static final UpgradeDefinition UPGRADE = new UpgradeDefinition(
        "summary", null, null, Map.of("current_hp_damage", 0.1), List.of(), List.of(), false);

    private static Unit newNoctis(boolean upgraded, UnitStats unitStats) {
        Unit noctis = new EliteUnit("Noctis", Team.PLAYER_ONE, unitStats);
        AbilityDefinition definition =
            new AbilityDefinition("Sanguine", "passive", "desc", BASE_STATS, List.of(), List.of(), UPGRADE);
        Sanguine sanguine = new Sanguine(definition);
        // AbilityFactory.create is what normally stamps this on - canUpgrade() needs it set.
        sanguine.setDefinition(definition);
        noctis.addAbility(sanguine);
        if (upgraded) {
            sanguine.upgrade();
        }
        return noctis;
    }

    private static GameState scenario(Unit noctis, Unit target) {
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(noctis);
        p2.addUnit(target);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(noctis, map.getTile(new Position(0, 0)));
        map.moveUnit(target, map.getTile(new Position(1, 0)));
        return state;
    }

    @Test
    void amplifiesDamageByTheTargetsMissingHealthPercentage() {
        Unit noctis = newNoctis(false, new UnitStats(10, 0, 0, 500));
        Unit target = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 100));
        target.getHealthPool().setCurrent(50);
        GameState state = scenario(noctis, target);

        // STRENGTH categorically beats INTELLIGENCE, so this is a clean 10 base damage before the amp.
        CombatEngine.performAttack(state, noctis, target, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        // 50% missing HP * 2.5%/point = 125% bonus -> 10 * 2.25 = 22.5, rounds to 23.
        assertEquals(50 - 23, target.getHealth());
    }

    @Test
    void upgradedAlsoAddsACurrentHpBonusBeforeTheAmpApplies() {
        Unit noctis = newNoctis(true, new UnitStats(10, 0, 0, 500));
        Unit target = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 100));
        target.getHealthPool().setCurrent(50);
        GameState state = scenario(noctis, target);

        CombatEngine.performAttack(state, noctis, target, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        // (10 base + 50*10% current-HP bonus) * (1 + 50*2.5%) = 15 * 2.25 = 33.75, rounds to 34.
        assertEquals(50 - 34, target.getHealth());
    }

    @Test
    void killingWithAnAttackHealsForAFractionOfTheVictimsMaxHp() {
        Unit noctis = newNoctis(false, new UnitStats(50, 0, 0, 500));
        noctis.getHealthPool().setCurrent(100);
        Unit target = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 40));
        GameState state = scenario(noctis, target);

        CombatEngine.performAttack(state, noctis, target, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        assertEquals(0, target.getHealth(), "the attack should be lethal");
        assertEquals(100 + (int) Math.round(40 * 0.4), noctis.getHealth(), "healed for 40% of the victim's max HP");
    }

    @Test
    void doesNotTriggerOnSomeoneElsesAttack() {
        Unit noctis = newNoctis(false, new UnitStats(10, 0, 0, 500));
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(10, 0, 0, 500));
        Unit target = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 100));
        target.getHealthPool().setCurrent(50);

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(noctis);
        p1.addUnit(ally);
        p2.addUnit(target);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(noctis, map.getTile(new Position(0, 0)));
        map.moveUnit(ally, map.getTile(new Position(0, 1)));
        map.moveUnit(target, map.getTile(new Position(1, 0)));

        CombatEngine.performAttack(state, ally, target, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        assertEquals(50 - 10, target.getHealth(), "no amp - only Noctis's own attacks trigger Sanguine");
    }
}
