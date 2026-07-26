package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.DoomEffect;
import com.walnutt.event.KillEvent;
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

class DoomTest {

    @Test
    void silencesAndEscalatesDamage_untilTheVictimLandsAKill() {
        Unit lucifer = new BasicUnit("Lucifer", Team.PLAYER_ONE, new UnitStats(80, 30, 20, 800));
        Doom doom = new Doom(new AbilityDefinition("Doom", "active", "desc",
            Map.of("cooldown", 11.0, "cast_range", 3.0, "base_dmg", 20.0, "dmg_increase", 20.0)));
        lucifer.addAbility(doom);
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500));
        Unit bystander = new BasicUnit("Bystander", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(lucifer);
        p1.addUnit(bystander);
        p2.addUnit(victim);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(lucifer, map.getTile(new Position(0, 0)));
        map.moveUnit(victim, map.getTile(new Position(0, 2)));
        map.moveUnit(bystander, map.getTile(new Position(0, -1)));

        doom.onUse(state, new UnitTarget(victim));
        assertTrue(victim.hasStatus(StatusFlag.SILENCED));

        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_TWO));
        assertEquals(500 - 20, victim.getHealth(), "first tick deals base_dmg");

        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_TWO));
        assertEquals(500 - 20 - 40, victim.getHealth(), "second tick escalates by dmg_increase");

        // Victim lands a kill (on the bystander) - Doom should end immediately, silence lifted.
        state.getEventBus().publish(state, new KillEvent(victim, bystander));
        DoomEffect effect = findDoom(victim);
        assertNotNull(effect);
        assertTrue(effect.isExpired());
        assertFalse(victim.hasStatus(StatusFlag.SILENCED), "curse lifted the instant the victim gets a kill");

        int healthAfterKill = victim.getHealth();
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_TWO));
        assertEquals(healthAfterKill, victim.getHealth(), "no further damage once the curse has expired");
    }

    @Test
    void cannotBeRecastOnAnAlreadyDoomedTarget() {
        Unit lucifer = new BasicUnit("Lucifer", Team.PLAYER_ONE, new UnitStats(80, 30, 20, 800));
        Doom doom = new Doom(new AbilityDefinition("Doom", "active", "desc",
            Map.of("cooldown", 11.0, "cast_range", 3.0, "base_dmg", 20.0, "dmg_increase", 20.0)));
        lucifer.addAbility(doom);
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(lucifer);
        p2.addUnit(victim);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(lucifer, map.getTile(new Position(0, 0)));
        map.moveUnit(victim, map.getTile(new Position(0, 2)));

        assertTrue(doom.canUse(state, new UnitTarget(victim)));
        doom.onUse(state, new UnitTarget(victim));
        assertFalse(doom.canUse(state, new UnitTarget(victim)), "already-doomed target can't be re-cursed");
    }

    private DoomEffect findDoom(Unit unit) {
        for (Effect effect : unit.getEffects()) {
            if (effect instanceof DoomEffect doomEffect) {
                return doomEffect;
            }
        }
        return null;
    }
}
