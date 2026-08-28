package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.NoTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.BarrierEffect;
import com.walnutt.event.AbilityCastEvent;
import com.walnutt.event.DamageEvent;
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

class ReloadTest {

    private record Fixture(GameState state, Reload reload, PlasmaCannon cannon, Unit maxwell, Unit enemy) {
    }

    private static Fixture fixture() {
        Unit maxwell = new EliteUnit("Maxwell", Team.PLAYER_ONE, new UnitStats(18, 12, 84, 510, 2));
        Reload reload = new Reload(new AbilityDefinition("Reload", "passive", "desc", Map.of()));
        PlasmaCannon cannon = new PlasmaCannon(new AbilityDefinition("Plasma Cannon", "active", "desc", Map.of(
            "cooldown", 4.0, "cast_range", 2.0, "damage", 60.0, "duration", 2.0)));
        maxwell.addAbility(reload);
        maxwell.addAbility(cannon);
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 500));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(maxwell);
        p2.addUnit(enemy);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(maxwell, map.getTile(new Position(0, 0)));
        map.moveUnit(enemy, map.getTile(new Position(0, 1)));
        return new Fixture(state, reload, cannon, maxwell, enemy);
    }

    @Test
    void aQuietRoundRefreshesEveryCooldown() {
        Fixture f = fixture();
        f.cannon().resetToMax();
        assertEquals(4, f.cannon().getCurrentCooldown());

        f.reload().onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        assertTrue(f.cannon().isReady(), "nothing happened last round, so everything is reloaded");
    }

    @Test
    void actingDuringTheRoundPreventsIt() {
        Fixture f = fixture();
        f.cannon().resetToMax();

        f.reload().onAbilityUsed(f.state(),
            new AbilityCastEvent(f.maxwell(), f.cannon(), new NoTarget(), AbilityCastEvent.Phase.POST));
        f.reload().onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        assertEquals(4, f.cannon().getCurrentCooldown(), "Maxwell acted, so no reload");
    }

    /** An ally acting is not Maxwell acting. */
    @Test
    void someoneElseActingDoesNotPreventIt() {
        Fixture f = fixture();
        f.cannon().resetToMax();

        f.reload().onAbilityUsed(f.state(),
            new AbilityCastEvent(f.enemy(), f.cannon(), new NoTarget(), AbilityCastEvent.Phase.POST));
        f.reload().onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        assertTrue(f.cannon().isReady());
    }

    @Test
    void takingRealDamagePreventsIt() {
        Fixture f = fixture();
        f.cannon().resetToMax();

        f.maxwell().takeDamage(f.state(), new DamageEvent(f.enemy(), f.maxwell(), 30));
        f.reload().onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        assertEquals(4, f.cannon().getCurrentCooldown());
    }

    /**
     * The explicit rule from the brief: a barrier eating the hit entirely means Maxwell was
     * never really disturbed, so he still reloads. This works without any barrier-specific
     * code because PostDamageEvent carries the post-mitigation number.
     */
    @Test
    void damageFullyAbsorbedByABarrierDoesNotCount() {
        Fixture f = fixture();
        f.cannon().resetToMax();
        f.maxwell().addEffect(new BarrierEffect("Energy Shield", "desc", 5, 80));

        f.maxwell().takeDamage(f.state(), new DamageEvent(f.enemy(), f.maxwell(), 30));
        assertEquals(510, f.maxwell().getHealth(), "the barrier ate all of it");

        f.reload().onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));
        assertTrue(f.cannon().isReady(), "absorbed damage does not interrupt the reload");
    }

    /** Only what the barrier could not absorb counts - a partial absorb still interrupts. */
    @Test
    void damageThatSpillsPastABarrierDoesCount() {
        Fixture f = fixture();
        f.cannon().resetToMax();
        f.maxwell().addEffect(new BarrierEffect("Energy Shield", "desc", 5, 20));

        f.maxwell().takeDamage(f.state(), new DamageEvent(f.enemy(), f.maxwell(), 30));
        assertEquals(500, f.maxwell().getHealth(), "10 got through");

        f.reload().onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));
        assertEquals(4, f.cannon().getCurrentCooldown());
    }

    @Test
    void theFlagsResetSoAOneOffActionOnlyBlocksOneRound() {
        Fixture f = fixture();
        f.cannon().resetToMax();

        f.reload().onAbilityUsed(f.state(),
            new AbilityCastEvent(f.maxwell(), f.cannon(), new NoTarget(), AbilityCastEvent.Phase.POST));
        f.reload().onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));
        assertEquals(4, f.cannon().getCurrentCooldown());

        f.reload().onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));
        assertTrue(f.cannon().isReady(), "the next quiet round reloads normally");
    }
}
