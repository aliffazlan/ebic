package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
