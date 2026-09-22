package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.UpgradeDefinition;
import com.walnutt.effect.impl.BloodwakeEffect;
import com.walnutt.event.KillEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class BloodwakeTest {

    private static final Map<String, Double> STATS = Map.of(
        "cooldown", 7.0, "duration", 3.0, "bonus_damage", 30.0);
    private static final UpgradeDefinition UPGRADE =
        new UpgradeDefinition("summary", null, null, Map.of(), List.of(), List.of(), false);

    private static Bloodwake newBloodwake() {
        AbilityDefinition definition =
            new AbilityDefinition("Bloodwake", "active", "desc", STATS, List.of(), List.of(), UPGRADE);
        Bloodwake bloodwake = new Bloodwake(definition);
        // AbilityFactory.create is what normally stamps this on - canUpgrade() needs it set.
        bloodwake.setDefinition(definition);
        return bloodwake;
    }

    private static GameState scenario(Unit noctis, Unit victim) {
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(noctis);
        p2.addUnit(victim);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(5);
        map.moveUnit(noctis, map.getTile(new Position(0, 0)));
        map.moveUnit(victim, map.getTile(new Position(1, 0)));
        return state;
    }

    @Test
    void grantsFreeDoubleMovementButNotDoubleAttacksUnlessUpgraded() {
        Unit noctis = new EliteUnit("Noctis", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 500));
        Move move = new Move();
        Attack attack = new Attack();
        noctis.addAbility(move);
        noctis.addAbility(attack);
        Bloodwake bloodwake = newBloodwake();
        noctis.addAbility(bloodwake);
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 500));
        GameState state = scenario(noctis, victim);

        bloodwake.onUse(state, new NoTarget());

        assertEquals(0, move.getMoveCost(state), "moves are free while Bloodwake is active");
        assertEquals(1, attack.getMoveCost(state), "attacks stay their normal cost unless upgraded");

        noctis.markMoved();
        assertFalse(noctis.hasMovedThisTurn(), "one bonus move action lets Noctis move a second time");
        noctis.markMoved();
        assertTrue(noctis.hasMovedThisTurn(), "but only one extra move, not unlimited");

        noctis.markAttacked();
        assertTrue(noctis.hasAttackedThisTurn(), "base Bloodwake grants no extra attack");
    }

    @Test
    void upgradedAlsoFreesAndDoublesAttacks() {
        Unit noctis = new EliteUnit("Noctis", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 500));
        Attack attack = new Attack();
        noctis.addAbility(attack);
        Bloodwake bloodwake = newBloodwake();
        noctis.addAbility(bloodwake);
        bloodwake.upgrade();
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 500));
        GameState state = scenario(noctis, victim);

        bloodwake.onUse(state, new NoTarget());

        assertEquals(0, attack.getMoveCost(state), "upgraded Bloodwake frees attacks too");
        noctis.markAttacked();
        assertFalse(noctis.hasAttackedThisTurn(), "and lets Noctis attack a second time");
        noctis.markAttacked();
        assertTrue(noctis.hasAttackedThisTurn());
    }

    @Test
    void bonusDamageAccumulatesEachOfNoctisTurnsAndAppliesToHisAttacks() {
        Unit noctis = new EliteUnit("Noctis", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 500));
        Bloodwake bloodwake = newBloodwake();
        noctis.addAbility(bloodwake);
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 500));
        GameState state = scenario(noctis, victim);

        bloodwake.onUse(state, new NoTarget());
        // The cast itself already grants one turn's worth of bonus damage (30).
        CombatEngine.performAttack(state, noctis, victim, Attribute.STRENGTH, Attribute.INTELLIGENCE);
        assertEquals(500 - (10 + 30), victim.getHealth(), "base 10 STR damage plus the accumulated 30");

        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));
        victim.getHealthPool().setCurrent(500);
        CombatEngine.performAttack(state, noctis, victim, Attribute.STRENGTH, Attribute.INTELLIGENCE);
        assertEquals(500 - (10 + 60), victim.getHealth(), "a second turn escalates the bonus to 60");
    }

    @Test
    void killDuringTheEffectResetsDurationForTheBaseForm() {
        Unit noctis = new EliteUnit("Noctis", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 500));
        Bloodwake bloodwake = newBloodwake();
        noctis.addAbility(bloodwake);
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 500));
        GameState state = scenario(noctis, victim);

        bloodwake.onUse(state, new NoTarget());
        BloodwakeEffect effect = noctis.getActiveEffect(BloodwakeEffect.class).orElseThrow();
        effect.setRemainingTurns(1);

        state.getEventBus().publish(state, new KillEvent(noctis, victim));

        assertEquals(3, effect.getRemainingTurns(), "base form resets back to the base duration");
    }

    @Test
    void recastingWhileActive_upgradedExtendsInsteadOfResetting() {
        Unit noctis = new EliteUnit("Noctis", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 500));
        Bloodwake bloodwake = newBloodwake();
        noctis.addAbility(bloodwake);
        bloodwake.upgrade();
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 500));
        GameState state = scenario(noctis, victim);

        bloodwake.onUse(state, new NoTarget());
        BloodwakeEffect effect = noctis.getActiveEffect(BloodwakeEffect.class).orElseThrow();
        effect.setRemainingTurns(1);

        bloodwake.onUse(state, new NoTarget());

        assertEquals(1 + 3, effect.getRemainingTurns(), "upgraded form extends instead of resetting");
    }

    @Test
    void accumulatedBonusDamageSurvivesARefresh() {
        Unit noctis = new EliteUnit("Noctis", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 500));
        Bloodwake bloodwake = newBloodwake();
        noctis.addAbility(bloodwake);
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 500));
        GameState state = scenario(noctis, victim);

        bloodwake.onUse(state, new NoTarget());
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE)); // 30 -> 60
        bloodwake.onUse(state, new NoTarget()); // recast: refreshes, must not reset the accumulated 60

        CombatEngine.performAttack(state, noctis, victim, Attribute.STRENGTH, Attribute.INTELLIGENCE);
        assertEquals(500 - (10 + 60), victim.getHealth(), "the accumulated bonus survives a refresh");
    }
}
