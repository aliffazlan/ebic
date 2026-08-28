package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.effect.StatusEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class NanobotsTest {

    /** A curse with no cure - the shape Lucifer's Doom uses to survive a cleanse. */
    private static final class StubbornCurse extends StatusEffect {
        StubbornCurse() {
            super("Stubborn Curse", 10, EffectCategory.DEBUFF, StatusFlag.SILENCED);
            this.dispellable = false;
        }
    }

    private record Fixture(GameState state, Nanobots nanobots, Unit maxwell, Unit ally) {
    }

    private static Fixture fixture() {
        Unit maxwell = new EliteUnit("Maxwell", Team.PLAYER_ONE, new UnitStats(18, 12, 84, 510, 2));
        Nanobots nanobots = new Nanobots(new AbilityDefinition("Nanobots", "active", "desc", Map.of(
            "cooldown", 7.0, "cast_range", 2.0, "heal", 40.0, "duration", 2.0)));
        maxwell.addAbility(nanobots);
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 300));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(maxwell);
        p1.addUnit(ally);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(maxwell, map.getTile(new Position(0, 0)));
        map.moveUnit(ally, map.getTile(new Position(0, 1)));
        return new Fixture(state, nanobots, maxwell, ally);
    }

    @Test
    void cleansesAndHealsImmediatelyOnCastRatherThanWaitingATurn() {
        Fixture f = fixture();
        f.ally().takeDamage(f.state(), new DamageEvent(null, f.ally(), 100));
        f.ally().addEffect(new StatusEffect("Stunned", 3, EffectCategory.DEBUFF, StatusFlag.STUNNED));
        assertEquals(200, f.ally().getHealth());

        f.nanobots().onUse(f.state(), new UnitTarget(f.ally()));

        assertEquals(240, f.ally().getHealth(), "healed on the turn it was cast");
        assertFalse(f.ally().hasStatus(StatusFlag.STUNNED), "and cleansed on the turn it was cast");
    }

    @Test
    void pulsesAgainAtTheStartOfEachOfTheTargetsTurns() {
        Fixture f = fixture();
        f.ally().takeDamage(f.state(), new DamageEvent(null, f.ally(), 200));
        f.nanobots().onUse(f.state(), new UnitTarget(f.ally()));
        assertEquals(140, f.ally().getHealth(), "100 + the on-cast 40");

        f.state().getEventBus().publish(f.state(), new com.walnutt.event.TurnStartEvent(Team.PLAYER_ONE));
        assertEquals(180, f.ally().getHealth(), "a second pulse at the start of its own turn");

        // The opponent's turn starting must not pulse it.
        f.state().getEventBus().publish(f.state(), new com.walnutt.event.TurnStartEvent(Team.PLAYER_TWO));
        assertEquals(180, f.ally().getHealth());
    }

    @Test
    void leavesADebuffThatCannotBeCleansedAlone() {
        Fixture f = fixture();
        f.ally().addEffect(new StubbornCurse());
        f.ally().addEffect(new StatusEffect("Rooted", 3, EffectCategory.DEBUFF, StatusFlag.ROOTED));

        f.nanobots().onUse(f.state(), new UnitTarget(f.ally()));

        assertFalse(f.ally().hasStatus(StatusFlag.ROOTED), "an ordinary debuff goes");
        assertTrue(f.ally().hasStatus(StatusFlag.SILENCED), "an undispellable one stays");
    }

    /** Its own buff must survive the cleanse it performs, or it would strip itself on cast. */
    @Test
    void doesNotCleanseItself() {
        Fixture f = fixture();
        f.nanobots().onUse(f.state(), new UnitTarget(f.ally()));

        assertTrue(f.ally().getEffects().stream()
                .anyMatch(e -> e.getName().equals("Nanobots") && !e.isExpired()),
            "the nanobots effect is a BUFF, so dispelDebuffs leaves it alone");
    }

    @Test
    void refusesEnemies() {
        Fixture f = fixture();
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 300));
        f.state().getMap().moveUnit(enemy, f.state().getMap().getTile(new Position(1, 0)));

        assertFalse(f.nanobots().canUse(f.state(), new UnitTarget(enemy)));
        assertTrue(f.nanobots().canUse(f.state(), new UnitTarget(f.ally())));
    }

    /** Guards the "effects list mutated from inside an event dispatch" hazard. */
    @Test
    void cleansingFromInsideATurnStartDispatchDoesNotThrow() {
        Fixture f = fixture();
        f.ally().addEffect(new StatusEffect("Rooted", 5, EffectCategory.DEBUFF, StatusFlag.ROOTED));
        f.nanobots().onUse(f.state(), new UnitTarget(f.ally()));
        f.ally().addEffect(new StatusEffect("Stunned", 5, EffectCategory.DEBUFF, StatusFlag.STUNNED));

        f.state().getEventBus().publish(f.state(), new com.walnutt.event.TurnStartEvent(Team.PLAYER_ONE));

        assertFalse(f.ally().hasStatus(StatusFlag.STUNNED));
        assertEquals(0, f.ally().getEffects().stream().filter(Effect::isExpired).count(),
            "expired effects should have been cleared out, not left dangling");
    }
}
