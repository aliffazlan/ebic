package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.NoTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.StatusEffect;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class DilationTest {

    @Test
    void pausesCooldowns_speedsUpBuffs_slowsDownDebuffs_forAdjacentEnemiesOnly() {
        Unit chronos = new BasicUnit("Chronos", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        Dilation dilation = new Dilation(new AbilityDefinition("Dilation", "active", "desc",
            Map.of("cooldown", 7.0, "duration", 4.0, "range", 1.0, "time_modifier", 0.5)));
        chronos.addAbility(dilation);

        Unit nearEnemy = new BasicUnit("Near", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100));
        SoulRip nearAbility = new SoulRip(new AbilityDefinition("Soul Rip", "active", "desc",
            Map.of("cooldown", 3.0, "cast_range", 2.0, "str_multiplier", 0.5)));
        nearEnemy.addAbility(nearAbility);
        nearAbility.resetToMax(); // cooldown = 3

        StatusEffect buff = new StatusEffect("Test Buff", 4, EffectCategory.BUFF);
        StatusEffect debuff = new StatusEffect("Test Debuff", 4, EffectCategory.DEBUFF);
        nearEnemy.addEffect(buff);
        nearEnemy.addEffect(debuff);

        Unit farEnemy = new BasicUnit("Far", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100));
        StatusEffect farDebuff = new StatusEffect("Far Debuff", 4, EffectCategory.DEBUFF);
        farEnemy.addEffect(farDebuff);

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(chronos);
        p2.addUnit(nearEnemy);
        p2.addUnit(farEnemy);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(chronos, map.getTile(new Position(0, 0)));
        map.moveUnit(nearEnemy, map.getTile(new Position(1, 0))); // adjacent
        map.moveUnit(farEnemy, map.getTile(new Position(3, 0))); // not adjacent

        dilation.onUse(state, new NoTarget());
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE)); // Chronos's own turn: pulse the field

        assertTrue(nearEnemy.hasStatus(StatusFlag.TIME_DILATED));
        assertFalse(farEnemy.hasStatus(StatusFlag.TIME_DILATED), "field should only reach adjacent enemies");

        // Cooldowns paused: nearEnemy's startTurn should NOT tick Soul Rip down.
        nearEnemy.startTurn(state);
        assertEquals(3, nearAbility.getCurrentCooldown(), "cooldown ticking should be paused while dilated");

        // Buff ticks FAST (2 turns' worth in one call).
        nearEnemy.endTurn(state);
        assertEquals(2, buff.getRemainingTurns(), "buff should have ticked down by 2 (FAST)");

        // SLOW alternates tick/skip. The pulse only lasts 1 turn, so Chronos has to
        // "renew" it before each of the enemy's turns for the field to keep applying.
        int afterFirstDilatedTick = debuff.getRemainingTurns();

        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));
        nearEnemy.startTurn(state);
        assertEquals(3, nearAbility.getCurrentCooldown(), "still paused on the second dilated turn");
        nearEnemy.endTurn(state);
        int afterSecondDilatedTick = debuff.getRemainingTurns();

        int totalTicked = (4 - afterFirstDilatedTick) + (afterFirstDilatedTick - afterSecondDilatedTick);
        assertEquals(1, totalTicked, "SLOW should tick a total of 1 turn across these 2 calls (half speed), not 2");

        // Once the field stops being renewed, cooldowns resume ticking normally.
        nearEnemy.startTurn(state);
        assertEquals(2, nearAbility.getCurrentCooldown(), "cooldown resumes ticking once no longer dilated");

        // Unaffected far enemy ticks normally throughout (2 calls -> -2).
        farEnemy.startTurn(state);
        farEnemy.endTurn(state);
        farEnemy.startTurn(state);
        farEnemy.endTurn(state);
        assertEquals(2, farDebuff.getRemainingTurns());
    }
}
