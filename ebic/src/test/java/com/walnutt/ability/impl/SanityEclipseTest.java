package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.StatusEffect;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class SanityEclipseTest {

    @Test
    void explodesAfterDelay_dealingIntDiffDamage_bypassingInvulnerability() {
        Unit caster = new BasicUnit("Harbinger", Team.PLAYER_ONE, new UnitStats(0, 0, 80, 100));
        SanityEclipse ability = new SanityEclipse(new AbilityDefinition("Sanity's Eclipse", "active", "desc",
            Map.of("cooldown", 9.0, "cast_range", 4.0, "delay", 1.0, "radius", 1.0, "int_diff_dmg", 1.0)));
        caster.addAbility(ability);
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 20, 200));
        target.addEffect(new StatusEffect("Imprisoned", 5, StatusFlag.INVULNERABLE));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(caster);
        p2.addUnit(target);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(caster, map.getTile(new Position(0, 0)));
        map.moveUnit(target, map.getTile(new Position(3, 0)));

        TileTarget tileTarget = new TileTarget(map.getTile(target.getPosition()));
        assertTrue(ability.canUse(state, tileTarget));
        ability.onUse(state, tileTarget);

        // Not yet exploded before the delay elapses.
        assertEquals(200, target.getHealth());

        caster.endTurn(state); // delay=1 -> orb expires at the end of the caster's own turn

        assertEquals(200 - 60, target.getHealth()); // (80 - 20) * 1.0 = 60, despite target being invulnerable
    }
}
