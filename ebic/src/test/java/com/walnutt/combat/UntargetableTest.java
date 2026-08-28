package com.walnutt.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Attack;
import com.walnutt.ability.impl.Fireblast;
import com.walnutt.ability.impl.SoulRip;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.StatusEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/**
 * StatusFlag.UNTARGETABLE shipped declared and read by absolutely nothing, which is why an
 * attack on a cloaked Evayne resolved normally and merely landed for 0. These pin the rule
 * that replaced it, and - just as importantly - the line it does NOT cross: targeting
 * governs SELECTION, invulnerability governs DAMAGE, and area effects that sweep a radius
 * still reach a sealed-off unit and are still zeroed there.
 */
class UntargetableTest {

    private record Fixture(GameState state, GameMap map, Unit attacker, Attack attack,
                           Unit victim, Unit ally) {
    }

    private static Fixture fixture() {
        Unit attacker = new EliteUnit("Attacker", Team.PLAYER_ONE, new UnitStats(40, 10, 10, 500, 1));
        Attack attack = new Attack();
        attacker.addAbility(attack);
        Unit victim = new EliteUnit("Victim", Team.PLAYER_TWO, new UnitStats(10, 10, 40, 500, 1));
        Unit ally = new EliteUnit("Ally", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 500, 1));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(attacker);
        p1.addUnit(ally);
        p2.addUnit(victim);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(attacker, map.getTile(new Position(0, 0)));
        map.moveUnit(victim, map.getTile(new Position(0, 1)));
        map.moveUnit(ally, map.getTile(new Position(1, 0)));
        return new Fixture(state, map, attacker, attack, victim, ally);
    }

    private static Fireblast fireblast() {
        return new Fireblast(new AbilityDefinition("Fireblast", "active", "desc",
            Map.of("cooldown", 2.0, "cast_range", 3.0, "damage", 24.0, "burn_stacks", 3.0)));
    }

    /** The three flags that seal a unit off, each via a real effect from a shipped ability. */
    @Test
    void anInvulnerableUnitCannotBeAttacked() {
        Fixture f = fixture();
        assertTrue(f.attack.canUse(f.state, new UnitTarget(f.victim)), "attackable to begin with");

        f.victim.addEffect(new StatusEffect("Sealed", 3, StatusFlag.INVULNERABLE));

        assertFalse(f.victim.isTargetable());
        assertFalse(f.attack.canUse(f.state, new UnitTarget(f.victim)));
        assertTrue(f.attack.getLegalTargets(f.state).isEmpty(),
            "and gone from legal targets, so the client highlights nothing and the bot skips it");
    }

    @Test
    void theUntargetableFlagAloneAlsoSealsAUnitOff() {
        Fixture f = fixture();
        f.victim.addEffect(new StatusEffect("Elsewhere", 3, StatusFlag.UNTARGETABLE));

        assertFalse(f.victim.isTargetable());
        assertFalse(f.attack.canUse(f.state, new UnitTarget(f.victim)));
    }

    @Test
    void anAbilityCannotBeAimedAtASealedOffUnitEither() {
        Fixture f = fixture();
        Fireblast blast = fireblast();
        f.attacker.addAbility(blast);
        assertTrue(blast.canUse(f.state, new UnitTarget(f.victim)));

        f.victim.addEffect(new StatusEffect("Sealed", 3, StatusFlag.INVULNERABLE));

        assertFalse(blast.canUse(f.state, new UnitTarget(f.victim)));
        assertTrue(blast.getLegalTargets(f.state).isEmpty());
    }

    /** Confirmed with the user: nothing may target them, from either side. */
    @Test
    void friendlyCastsAreBlockedToo() {
        Fixture f = fixture();
        // Soul Rip heals an ally; it is the one shipped ability whose canUse accepts both teams.
        SoulRip soulRip = new SoulRip(new AbilityDefinition("Soul Rip", "active", "desc",
            Map.of("cooldown", 4.0, "cast_range", 3.0)));
        f.attacker.addAbility(soulRip);
        assertTrue(soulRip.canUse(f.state, new UnitTarget(f.ally)), "an ally is targetable to begin with");

        f.ally.addEffect(new StatusEffect("Sealed", 3, StatusFlag.INVULNERABLE));

        assertFalse(soulRip.canUse(f.state, new UnitTarget(f.ally)),
            "an ally you have sealed off is out of your reach too");
    }

    /**
     * The line the rule does not cross. An area ability sweeps a radius rather than picking
     * a target, so it still reaches a sealed-off unit - and INVULNERABLE still zeroes it
     * inside takeDamage, which is the layer that was working all along.
     */
    @Test
    void areaDamageStillReachesThemAndIsStillZeroedByInvulnerability() {
        Fixture f = fixture();
        f.victim.addEffect(new StatusEffect("Sealed", 3, StatusFlag.INVULNERABLE));

        f.victim.takeDamage(f.state, new DamageEvent(f.attacker, f.victim, 100));

        assertEquals(500, f.victim.getHealth(), "damage is refused by invulnerability, not by targeting");
    }

    /** And Sanity's Eclipse's explicit bypass keeps working, as its own text promises. */
    @Test
    void aBypassingSourceStillDamagesThem() {
        Fixture f = fixture();
        f.victim.addEffect(new StatusEffect("Sealed", 3, StatusFlag.INVULNERABLE));

        DamageEvent event = new DamageEvent(f.attacker, f.victim, 100);
        event.setBypassInvulnerability(true);
        f.victim.takeDamage(f.state, event);

        assertEquals(400, f.victim.getHealth());
    }

    /** A tile-shaped target is unaffected - there is no unit in it to seal off. */
    @Test
    void tileTargetedAbilitiesAreUnaffected() {
        Fixture f = fixture();
        f.victim.addEffect(new StatusEffect("Sealed", 3, StatusFlag.INVULNERABLE));

        com.walnutt.ability.Move move = new com.walnutt.ability.Move();
        f.attacker.addAbility(move);
        Target emptyTile = new com.walnutt.ability.target.TileTarget(f.map.getTile(new Position(-1, 0)));

        assertTrue(move.canUse(f.state, emptyTile));
    }
}
