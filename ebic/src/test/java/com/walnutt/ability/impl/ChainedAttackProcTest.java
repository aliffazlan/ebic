package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.TriggerHandler;
import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.PostDamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/**
 * Chronos's Timeless Strike chains up to nine attacks off one click, republishing a
 * PostAttackEvent for each. Passives that are meant to fire once per attack *cast* must
 * not ride that chain - Energy Break sent cooldowns to absurd numbers this way, and
 * Counterstrike would hand out a free counter per chained hit.
 */
class ChainedAttackProcTest {

    /** An inert ability with a cooldown, purely so Energy Break has something to inflate. */
    private static final class DummyAbility extends Ability {
        DummyAbility() {
            super("Dummy", "", false);
            setMaxCooldown(3);
        }

        @Override
        public void onUse(GameState state, Target target) {
        }
    }

    /** Counts damage events by cause, which is how a 0-damage counter can still be counted. */
    private static final class CauseCounter extends TriggerHandler {
        private int count;
        private final String cause;

        CauseCounter(String cause) {
            this.cause = cause;
        }

        @Override
        public void onDamageTaken(GameState state, PostDamageEvent event) {
            if (cause.equals(event.damageEvent().getCauseLabel())) {
                count++;
            }
        }
    }

    private static TimelessStrike timelessStrike() {
        return new TimelessStrike(new AbilityDefinition("Timeless Strike", "passive", "desc", Map.of(
            "duration", 1.0, "damage_multiplier", 0.5)));
    }

    private static GameState stateWith(Unit chronos, Unit opponent) {
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(chronos);
        p2.addUnit(opponent);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(chronos, map.getTile(new Position(0, 0)));
        map.moveUnit(opponent, map.getTile(new Position(0, 1)));
        return state;
    }

    /**
     * Pure Strength into pure Intelligence, so every chained WeightedEncounter can only
     * roll the one matchup and the chain is guaranteed to keep landing damage.
     */
    @Test
    void energyBreakBurnsCooldownsOncePerCast_notOncePerChainedHit() {
        Unit chronos = new EliteUnit("Chronos", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 1200));
        DummyAbility chronosAbility = new DummyAbility();
        chronos.addAbility(chronosAbility);
        chronos.addAbility(timelessStrike());

        Unit wei = new EliteUnit("Wei", Team.PLAYER_TWO, new UnitStats(0, 0, 40, 5000));
        wei.addAbility(new EnergyBreak(new AbilityDefinition("Energy Break", "passive", "desc", Map.of(
            "cooldown_increase", 1.0, "bonus_increase", 3.0))));

        GameState state = stateWith(chronos, wei);
        CauseCounter attacks = new CauseCounter("Attack");
        state.getEventBus().addGlobalListener(attacks);

        CombatEngine.performAttack(state, chronos, wei, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        assertTrue(attacks.count > 1, "the chain should actually have fired, or this proves nothing");
        assertEquals(1, chronosAbility.getCurrentCooldown(),
            "Energy Break should burn a cooldown once for the cast, not once per chained hit");
    }

    @Test
    void counterstrikeAnswersOncePerCast_notOncePerChainedHit() {
        Unit chronos = new EliteUnit("Chronos", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 1200));
        chronos.addAbility(timelessStrike());

        Unit valor = new EliteUnit("Valor", Team.PLAYER_TWO, new UnitStats(0, 0, 40, 5000));
        valor.addAbility(new Counterstrike(new AbilityDefinition("Counterstrike", "passive", "desc", Map.of(
            "damage_multiplier", 0.8, "lifesteal", 0.25))));

        GameState state = stateWith(chronos, valor);
        CauseCounter counters = new CauseCounter("Counterstrike");
        CauseCounter attacks = new CauseCounter("Attack");
        state.getEventBus().addGlobalListener(counters);
        state.getEventBus().addGlobalListener(attacks);

        CombatEngine.performAttack(state, chronos, valor, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        assertTrue(attacks.count > 1, "the chain should actually have fired");
        assertEquals(1, counters.count, "one counter for the cast, not one per chained hit");
    }

    /** The narrow fix must not disarm the passives on ordinary, unchained attacks. */
    @Test
    void anOrdinaryAttackStillProcsBoth() {
        Unit attacker = new EliteUnit("Attacker", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 1200));
        DummyAbility attackerAbility = new DummyAbility();
        attacker.addAbility(attackerAbility);

        Unit wei = new EliteUnit("Wei", Team.PLAYER_TWO, new UnitStats(0, 0, 40, 5000));
        wei.addAbility(new EnergyBreak(new AbilityDefinition("Energy Break", "passive", "desc", Map.of(
            "cooldown_increase", 1.0, "bonus_increase", 3.0))));
        wei.addAbility(new Counterstrike(new AbilityDefinition("Counterstrike", "passive", "desc", Map.of(
            "damage_multiplier", 0.8, "lifesteal", 0.25))));

        GameState state = stateWith(attacker, wei);
        CauseCounter counters = new CauseCounter("Counterstrike");
        state.getEventBus().addGlobalListener(counters);

        CombatEngine.performAttack(state, attacker, wei, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        assertEquals(1, attackerAbility.getCurrentCooldown(), "Energy Break still fires on a normal attack");
        assertEquals(1, counters.count, "and so does Counterstrike");
    }
}
