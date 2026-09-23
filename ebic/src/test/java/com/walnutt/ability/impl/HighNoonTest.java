package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.TriggerHandler;

import com.walnutt.ability.Attack;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.combat.NormalEncounter;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.UpgradeDefinition;
import com.walnutt.effect.impl.HighNoonMarkEffect;
import com.walnutt.event.GameEvent;
import com.walnutt.event.PassiveProcEvent;
import com.walnutt.event.PostDamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class HighNoonTest {

    private static final Map<String, Double> STATS = Map.of(
        "duration", 3.0, "chance", 0.4, "crit_damage", 2.5);

    private static Random alwaysSucceeds() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }

    private static Random alwaysFails() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.999;
            }
        };
    }

    private static HighNoon newHighNoon(Map<String, Double> upgradeStats, String upgradeType) {
        UpgradeDefinition upgrade =
            new UpgradeDefinition("summary", null, upgradeType, upgradeStats, List.of(), List.of(), false);
        AbilityDefinition definition =
            new AbilityDefinition("High Noon", "passive", "desc", STATS, List.of(), List.of(), upgrade);
        HighNoon highNoon = new HighNoon(definition);
        // AbilityFactory.create is what normally stamps this on - canUpgrade() needs it set.
        highNoon.setDefinition(definition);
        return highNoon;
    }

    private static GameState scenario(Random random, Unit flint, Unit... enemies) {
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(flint);
        for (Unit enemy : enemies) {
            p2.addUnit(enemy);
        }
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), random);
        state.setRemainingMoves(5);
        map.moveUnit(flint, map.getTile(new Position(0, 0)));
        for (Unit enemy : enemies) {
            map.moveUnit(enemy, map.getTile(new Position(1, 0)));
        }
        return state;
    }

    @Test
    void baseFormCannotBeCast() {
        Unit flint = new EliteUnit("Flint", Team.PLAYER_ONE, new UnitStats(20, 20, 20, 560));
        HighNoon highNoon = newHighNoon(Map.of("cooldown", 5.0, "cast_range", 2.0, "count", 2.0), "active");
        flint.addAbility(highNoon);
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 500));
        GameState state = scenario(alwaysFails(), flint, victim);

        assertFalse(highNoon.canUse(state, new UnitTarget(victim)), "the base form is a passive with no cast");
    }

    @Test
    void upgradingTurnsItIntoAnActiveTargetableAbility() {
        Unit flint = new EliteUnit("Flint", Team.PLAYER_ONE, new UnitStats(20, 20, 20, 560));
        HighNoon highNoon = newHighNoon(Map.of("cooldown", 5.0, "cast_range", 2.0, "count", 2.0), "active");
        flint.addAbility(highNoon);
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 500));
        GameState state = scenario(alwaysFails(), flint, victim);

        highNoon.upgrade();

        assertTrue(highNoon.canUse(state, new UnitTarget(victim)));
    }

    @Test
    void successfulAttackHasAChanceToMarkTheTarget() {
        Unit flint = new EliteUnit("Flint", Team.PLAYER_ONE, new UnitStats(20, 0, 0, 560));
        flint.addAbility(newHighNoon(Map.of(), null));
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 500));
        GameState state = scenario(alwaysSucceeds(), flint, victim);

        CombatEngine.performAttack(state, new NormalEncounter(flint, victim, Attribute.STRENGTH, Attribute.INTELLIGENCE));

        assertTrue(victim.getActiveEffect(HighNoonMarkEffect.class).isPresent());
    }

    @Test
    void failedAttackNeitherPlacesNorConsumesAMark() {
        Unit flint = new EliteUnit("Flint", Team.PLAYER_ONE, new UnitStats(0, 20, 0, 560));
        flint.addAbility(newHighNoon(Map.of(), null));
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 500));
        GameState state = scenario(alwaysSucceeds(), flint, victim);

        // INTELLIGENCE beats AGILITY - a clean miss - even with a Random that would otherwise
        // always succeed the chance roll.
        CombatEngine.performAttack(state, new NormalEncounter(flint, victim, Attribute.AGILITY, Attribute.INTELLIGENCE));

        assertTrue(victim.getActiveEffect(HighNoonMarkEffect.class).isEmpty());
        assertEquals(500, victim.getHealth());
    }

    @Test
    void attackingAMarkedTargetConsumesItAndDoesNotConsumeAnAction() {
        Unit flint = new EliteUnit("Flint", Team.PLAYER_ONE, new UnitStats(20, 0, 0, 560));
        Attack attack = new Attack();
        flint.addAbility(attack);
        flint.addAbility(newHighNoon(Map.of(), null));
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 500));
        victim.addEffect(new HighNoonMarkEffect(flint, 3, 2.5));
        GameState state = scenario(alwaysFails(), flint, victim);
        flint.markAttacked(); // simulate Flint already having used his ordinary attack this turn

        assertTrue(attack.canUse(state, new UnitTarget(victim)), "a marked target is attackable despite the used-up turn");
        int movesBefore = state.getRemainingMoves();

        attack.onUse(state, new UnitTarget(victim));

        // STRENGTH beats INTELLIGENCE: 20 base, critted 2.5x by the consumed mark.
        assertEquals(500 - 50, victim.getHealth());
        assertTrue(victim.getActiveEffect(HighNoonMarkEffect.class).isEmpty(), "the mark was consumed");
        assertEquals(movesBefore, state.getRemainingMoves(), "the marked attack cost no move point");
    }

    @Test
    void onlyThePlacingUnitCanConsumeAnothersMark() {
        Unit flint1 = new EliteUnit("Flint1", Team.PLAYER_ONE, new UnitStats(20, 0, 0, 560));
        Unit flint2 = new EliteUnit("Flint2", Team.PLAYER_ONE, new UnitStats(20, 0, 0, 560));
        Attack attack = new Attack();
        flint1.addAbility(attack);
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 500));
        victim.addEffect(new HighNoonMarkEffect(flint2, 3, 2.5));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(flint1);
        p1.addUnit(flint2);
        p2.addUnit(victim);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), alwaysFails());
        state.setRemainingMoves(5);
        map.moveUnit(flint1, map.getTile(new Position(0, 0)));
        map.moveUnit(flint2, map.getTile(new Position(0, 1)));
        map.moveUnit(victim, map.getTile(new Position(1, 0)));

        attack.onUse(state, new UnitTarget(victim));

        // Plain 20 damage, no crit - flint1 isn't the one who placed the mark.
        assertEquals(500 - 20, victim.getHealth());
        assertTrue(victim.getActiveEffect(HighNoonMarkEffect.class).isPresent(), "flint2's mark is untouched");
        assertTrue(flint1.hasAttackedThisTurn(), "an unmarked-for-flint1 attack still costs the normal action");
    }

    @Test
    void barrageFiresCountAttacksAndChainsOnAConsumedMark() {
        Unit flint = new EliteUnit("Flint", Team.PLAYER_ONE, new UnitStats(100, 0, 0, 560));
        HighNoon highNoon = newHighNoon(Map.of("cooldown", 5.0, "cast_range", 2.0, "count", 2.0), "active");
        flint.addAbility(highNoon);
        highNoon.upgrade();
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 100, 100000));
        victim.addEffect(new HighNoonMarkEffect(flint, 3, 2.5));
        GameState state = scenario(alwaysFails(), flint, victim);

        highNoon.onUse(state, new UnitTarget(victim));

        // STRENGTH always beats INTELLIGENCE, so every shot deals flint's full 100 strength
        // (WeightedEncounter always resolves to each unit's only nonzero attribute). The first
        // shot consumes the pre-placed mark (100 * 2.5 = 250) and adds one more attack to the
        // barrage (count=2 -> 3 total): 250 + 100 + 100 = 450. alwaysFails() means no new mark
        // is ever placed mid-barrage, keeping the chain to exactly one extra shot.
        assertEquals(100000 - 450, victim.getHealth());
    }

    @Test
    void barrageStopsImmediatelyIfTheTargetDiesPartway() {
        Unit flint = new EliteUnit("Flint", Team.PLAYER_ONE, new UnitStats(1000, 0, 0, 560));
        HighNoon highNoon = newHighNoon(Map.of("cooldown", 5.0, "cast_range", 2.0, "count", 5.0), "active");
        flint.addAbility(highNoon);
        highNoon.upgrade();
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 100, 500));
        GameState state = scenario(alwaysFails(), flint, victim);

        highNoon.onUse(state, new UnitTarget(victim));

        assertTrue(victim.isDead());
        assertEquals(0, victim.getHealth(), "health never goes negative from the remaining unfired shots");
    }

    @Test
    void everyBarrageShotCanTriggerDoubleDraw() {
        Unit flint = new EliteUnit("Flint", Team.PLAYER_ONE, new UnitStats(100, 0, 0, 560));
        HighNoon highNoon = newHighNoon(Map.of("cooldown", 5.0, "cast_range", 2.0, "count", 3.0), "active");
        flint.addAbility(highNoon);
        highNoon.upgrade();
        AbilityDefinition doubleDrawDefinition =
            new AbilityDefinition("Double Draw", "passive", "desc", Map.of(), List.of(), List.of(), null);
        flint.addAbility(new DoubleDraw(doubleDrawDefinition));
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 100, 100000));
        GameState state = scenario(alwaysFails(), flint, victim);
        int[] doubleDraws = {0};
        state.getEventBus().addGlobalListener(new TriggerHandler() {
            @Override
            public void onDamageTaken(GameState s, PostDamageEvent event) {
                if ("Double Draw".equals(event.damageEvent().getCauseLabel())) {
                    doubleDraws[0]++;
                }
            }
        });

        highNoon.onUse(state, new UnitTarget(victim));

        // 3 successful barrage shots, one Double Draw each - and Double Draw's own shot never
        // chains into another one.
        assertEquals(3, doubleDraws[0]);
        assertFalse(highNoon.isBarrageInProgress());
    }

    @Test
    void consumingAMarkPublishesExactlyOneProcForTheMarkedUnit() {
        Unit flint = new EliteUnit("Flint", Team.PLAYER_ONE, new UnitStats(100, 0, 0, 560));
        flint.addAbility(newHighNoon(Map.of(), null));
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 100, 100000));
        victim.addEffect(new HighNoonMarkEffect(flint, 3, 2.5));
        GameState state = scenario(alwaysFails(), flint, victim);
        List<PassiveProcEvent> procs = new java.util.ArrayList<>();
        state.getEventBus().addGlobalListener(new TriggerHandler() {
            @Override
            public void onGameEvent(GameState s, GameEvent event) {
                if (event instanceof PassiveProcEvent proc) {
                    procs.add(proc);
                }
            }
        });

        CombatEngine.performAttack(state, new NormalEncounter(flint, victim, Attribute.STRENGTH, Attribute.INTELLIGENCE));
        CombatEngine.performAttack(state, new NormalEncounter(flint, victim, Attribute.STRENGTH, Attribute.INTELLIGENCE));

        assertEquals(1, procs.size(), "only the hit that consumed the mark signals it");
        assertEquals(victim, procs.get(0).unit());
        assertEquals("High Noon Mark Consumed", procs.get(0).label());
    }
}
