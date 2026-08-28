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
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.ActionKind;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** The two gadgets that are plain compositions of existing mechanics. */
class EnergyShieldAndPlasmaCannonTest {

    private record Fixture(GameState state, Unit maxwell, Unit ally, Unit enemy) {
    }

    private static Fixture fixture() {
        Unit maxwell = new EliteUnit("Maxwell", Team.PLAYER_ONE, new UnitStats(18, 12, 84, 510, 2));
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 300));
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 300));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(maxwell);
        p1.addUnit(ally);
        p2.addUnit(enemy);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(maxwell, map.getTile(new Position(0, 0)));
        map.moveUnit(ally, map.getTile(new Position(0, 1)));
        map.moveUnit(enemy, map.getTile(new Position(0, 2)));
        return new Fixture(state, maxwell, ally, enemy);
    }

    private static EnergyShield energyShield(Unit owner) {
        EnergyShield shield = new EnergyShield(new AbilityDefinition("Energy Shield", "active", "desc", Map.of(
            "cooldown", 4.0, "cast_range", 3.0, "barrier", 80.0, "duration", 4.0)));
        owner.addAbility(shield);
        return shield;
    }

    private static PlasmaCannon plasmaCannon(Unit owner) {
        PlasmaCannon cannon = new PlasmaCannon(new AbilityDefinition("Plasma Cannon", "active", "desc", Map.of(
            "cooldown", 4.0, "cast_range", 2.0, "damage", 60.0, "duration", 2.0)));
        owner.addAbility(cannon);
        return cannon;
    }

    @Test
    void energyShieldAbsorbsDamageBeforeHealthAndCanBeSelfCast() {
        Fixture f = fixture();
        EnergyShield shield = energyShield(f.maxwell());

        assertTrue(shield.canUse(f.state(), new UnitTarget(f.maxwell())), "self-cast is allowed");
        assertFalse(shield.canUse(f.state(), new UnitTarget(f.enemy())), "enemies are not");

        shield.onUse(f.state(), new UnitTarget(f.ally()));
        f.ally().takeDamage(f.state(), new DamageEvent(f.enemy(), f.ally(), 50));
        assertEquals(300, f.ally().getHealth(), "fully absorbed");

        f.ally().takeDamage(f.state(), new DamageEvent(f.enemy(), f.ally(), 50));
        assertEquals(280, f.ally().getHealth(), "30 of the barrier left, so 20 spilled through");
    }

    @Test
    void plasmaCannonDealsDamageAndDisarms() {
        Fixture f = fixture();
        PlasmaCannon cannon = plasmaCannon(f.maxwell());

        assertFalse(cannon.canUse(f.state(), new UnitTarget(f.ally())), "allies are not valid targets");
        assertTrue(cannon.canUse(f.state(), new UnitTarget(f.enemy())));

        cannon.onUse(f.state(), new UnitTarget(f.enemy()));

        assertEquals(240, f.enemy().getHealth());
        assertTrue(f.enemy().hasStatus(StatusFlag.DISARMED));
        assertTrue(f.enemy().isBlockedFrom(ActionKind.ATTACK));
    }

    /** Every effect these add carries real text, since the sidebar shows it on hover. */
    @Test
    void bothEffectsCarryPlayerFacingDescriptions() {
        Fixture f = fixture();
        energyShield(f.maxwell()).onUse(f.state(), new UnitTarget(f.ally()));
        plasmaCannon(f.maxwell()).onUse(f.state(), new UnitTarget(f.enemy()));

        assertFalse(f.ally().getEffects().get(0).getDescription().isBlank());
        assertFalse(f.enemy().getEffects().get(0).getDescription().isBlank());
    }
}
