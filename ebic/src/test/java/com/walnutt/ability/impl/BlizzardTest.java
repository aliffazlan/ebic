package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.BlizzardEffect;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class BlizzardTest {

    @Test
    void rootsAndDamagesFlatPerTurn_reapplyingStacksDurationInsteadOfRefreshing() {
        Unit yuki = new BasicUnit("Yuki", Team.PLAYER_ONE, new UnitStats(15, 20, 40, 450));
        Blizzard blizzard = new Blizzard(new AbilityDefinition("Blizzard", "active", "desc",
            Map.of("cooldown", 4.0, "cast_range", 3.0, "duration", 2.0, "damage", 25.0)));
        yuki.addAbility(blizzard);
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(yuki);
        p2.addUnit(target);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(yuki, map.getTile(new Position(0, 0)));
        map.moveUnit(target, map.getTile(new Position(0, 2)));

        blizzard.onUse(state, new UnitTarget(target));
        assertTrue(target.hasStatus(StatusFlag.ROOTED));

        blizzard.onUse(state, new UnitTarget(target)); // reapply while active - stacks, doesn't refresh
        BlizzardEffect effect = findBlizzard(target);
        assertNotNull(effect);
        assertEquals(4, effect.getRemainingTurns(), "duration 2 + 2 = 4, not refreshed back to 2");

        // Tick through 2 turns worth - would already be gone if it had only refreshed to 2.
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_TWO));
        target.endTurn(state);
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_TWO));
        target.endTurn(state);

        assertTrue(target.hasStatus(StatusFlag.ROOTED), "should still be active - the stacked duration outlasts 2 turns");
        assertEquals(500 - 25 * 2, target.getHealth(), "flat damage per turn, not scaled by remaining duration");
    }

    private BlizzardEffect findBlizzard(Unit unit) {
        for (Effect effect : unit.getEffects()) {
            if (effect instanceof BlizzardEffect blizzardEffect) {
                return blizzardEffect;
            }
        }
        return null;
    }
}
