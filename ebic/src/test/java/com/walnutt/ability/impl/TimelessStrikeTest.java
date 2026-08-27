package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.TimelessStrikeStunEffect;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class TimelessStrikeTest {

    @Test
    void chainsAdditionalAttacksAndEscalatesStun_untilTheChainStopsOnItsOwn() {
        // All points in STRENGTH so WeightedEncounter's roll is 100% deterministic,
        // and STRENGTH beats a 0-everything defender every time -> every chained hit lands.
        Unit chronos = new BasicUnit("Chronos", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 100));
        chronos.addAbility(new TimelessStrike(new AbilityDefinition("Timeless Strike", "passive", "desc",
            Map.of("duration", 1.0))));
        // A single nonzero stat (INTELLIGENCE) makes the target's own WeightedEncounter
        // roll 100% deterministic too - STRENGTH always beats it, so every chained hit
        // is guaranteed to land instead of a 1-in-3 chance of a random block.
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 1, 100_000));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(chronos);
        p2.addUnit(target);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(chronos, map.getTile(new Position(0, 0)));
        map.moveUnit(target, map.getTile(new Position(1, 0)));

        // The initial (non-chained) attack that kicks the whole thing off.
        CombatEngine.performAttack(state, chronos, target, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        // The chain should have run multiple times (capped internally) rather than
        // hanging forever or firing zero extra attacks - verified indirectly via the
        // stun actually landing (only chained hits, not the original, apply it) and
        // the whole thing terminating (test doesn't hang/stack-overflow).
        assertTrue(target.hasStatus(StatusFlag.STUNNED), "at least one chained hit should have landed and stunned the target");
        assertTrue(target.getHealth() < 100_000, "chained attacks should have dealt additional damage beyond the first hit");
    }

    /**
     * Two procs must leave ONE stun of 2 turns, not two 1-turn stuns sitting on top of
     * each other - the latter expire independently, so the target woke up a turn early
     * and the sidebar showed a duplicated effect.
     */
    @Test
    void repeatProcsExtendASingleStunInsteadOfStackingSeparateOnes() {
        Unit chronos = new BasicUnit("Chronos", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 100));
        chronos.addAbility(new TimelessStrike(new AbilityDefinition("Timeless Strike", "passive", "desc",
            Map.of("duration", 1.0, "damage_multiplier", 0.5))));
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 1, 100_000));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(chronos);
        p2.addUnit(target);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(chronos, map.getTile(new Position(0, 0)));
        map.moveUnit(target, map.getTile(new Position(1, 0)));

        CombatEngine.performAttack(state, chronos, target, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        List<Effect> stuns = target.getEffects().stream()
            .filter(e -> e instanceof TimelessStrikeStunEffect)
            .toList();
        assertEquals(1, stuns.size(), "exactly one stun instance, however many times the chain procced");
        // The chain runs to MAX_CHAIN_DEPTH (8) here, applying a stun on each proc past
        // the first, so the single instance should have accumulated more than one turn.
        assertTrue(stuns.get(0).getRemainingTurns() > 1,
            "repeat procs should have extended the same stun, got " + stuns.get(0).getRemainingTurns());
    }

    /** Chained hits are halved per-hit, so the chain can't out-damage the opener several times over. */
    @Test
    void chainedHitsDealReducedDamageComparedToTheOpeningHit() {
        Unit chronos = new BasicUnit("Chronos", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 100));
        chronos.addAbility(new TimelessStrike(new AbilityDefinition("Timeless Strike", "passive", "desc",
            Map.of("duration", 1.0, "damage_multiplier", 0.5))));
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 1, 100_000));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(chronos);
        p2.addUnit(target);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(chronos, map.getTile(new Position(0, 0)));
        map.moveUnit(target, map.getTile(new Position(1, 0)));

        CombatEngine.performAttack(state, chronos, target, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        // Opener 50 at full, then 8 chained hits at 25 each (the depth cap) = 250.
        // Without the multiplier this would be 50 + 8*50 = 450.
        int dealt = 100_000 - target.getHealth();
        assertEquals(50 + (8 * 25), dealt, "opener at full damage, every chained hit halved");
    }

    @Test
    void doesNotChainWhenTheAttackerIsSomeoneElse() {
        Unit chronos = new BasicUnit("Chronos", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 100));
        TimelessStrike timelessStrike = new TimelessStrike(new AbilityDefinition("Timeless Strike", "passive", "desc",
            Map.of("duration", 1.0)));
        chronos.addAbility(timelessStrike);
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 100));
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(chronos);
        p1.addUnit(ally);
        p2.addUnit(target);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(ally, map.getTile(new Position(0, 0)));
        map.moveUnit(target, map.getTile(new Position(1, 0)));

        // Ally attacking (not Chronos) should not trigger Chronos's passive at all.
        CombatEngine.performAttack(state, ally, target, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        assertTrue(target.getHealth() == 1000 - 50, "only the single ally attack should have landed, no chaining");
    }
}
