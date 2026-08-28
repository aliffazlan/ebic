package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.AbilityCastEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.Stat;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.ChampionUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class SuperiorMasteryTest {

    private record Fixture(GameState state, GameMap map, Unit joker, SuperiorMastery mastery,
                           PerplexingShot shot, Unit enemy) {

        /** Drives one cast the way TurnManager does: PRE, onUse, POST. */
        void cast(Ability ability, Target target) {
            state.getEventBus().publish(state,
                new AbilityCastEvent(joker, ability, target, AbilityCastEvent.Phase.PRE));
            ability.onUse(state, target);
            state.getEventBus().publish(state,
                new AbilityCastEvent(joker, ability, target, AbilityCastEvent.Phase.POST));
        }

        void startJokersTurn() {
            joker.startTurn(state);
            state.setRemainingMoves(3);
            state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));
        }
    }

    private static Fixture fixture() {
        Unit joker = new ChampionUnit("Joker", Team.PLAYER_ONE, new UnitStats(54, 68, 85, 990, 2));
        SuperiorMastery mastery = new SuperiorMastery(new AbilityDefinition(
            "Superior Mastery", "passive", "desc",
            Map.of("cast_range_bonus", 2.0, "cooldown_reduction", 1.0)));
        PerplexingShot shot = new PerplexingShot(new AbilityDefinition(
            "Perplexing Shot", "active", "desc",
            Map.of("cast_range", 2.0, "cooldown", 3.0, "damage", 30.0, "bonus_damage", 20.0, "bounces", 2.0)));
        joker.addAbility(new Move());
        joker.addAbility(new Attack());
        joker.addAbility(shot);
        joker.addAbility(mastery);

        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(20, 20, 20, 500));
        Player one = new Player("P1", Team.PLAYER_ONE);
        Player two = new Player("P2", Team.PLAYER_TWO);
        one.addUnit(joker);
        two.addUnit(enemy);
        GameMap map = new GameMap(6);
        GameState state = new GameState(map, List.of(one, two), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(joker, map.getTile(new Position(0, 0)));
        map.moveUnit(enemy, map.getTile(new Position(0, 1)));
        return new Fixture(state, map, joker, mastery, shot, enemy);
    }

    /** A cast-range bonus on the OWNER, so it lifts every ability including any arriving later. */
    @Test
    void grantsPermanentCastRangeOnAttach() {
        Fixture f = fixture();

        assertEquals(2, (int) f.joker.getEffective(Stat.CAST_RANGE));
        assertEquals(4, f.shot.getRange(), "cast range 2 plus the bonus");
    }

    /**
     * The bug this mirrors was shipped once already for Gyroscope: Move and Attack must
     * override getRange(), so a cast-range bonus never promises a step or a swing the
     * engine will refuse.
     */
    @Test
    void doesNotWidenMoveOrAttack() {
        Fixture f = fixture();

        Move move = (Move) f.joker.getAbilities().stream().filter(a -> a instanceof Move).findFirst().orElseThrow();
        Attack attack = (Attack) f.joker.getAbilities().stream()
            .filter(a -> a instanceof Attack).findFirst().orElseThrow();

        assertEquals(1, move.getRange());
        assertEquals(2, attack.getRange(), "attack range comes from ATTACK_RANGE, untouched by the bonus");
    }

    @Test
    void aCastCutsACooldownOffEveryOtherAbilityButNotItsOwn() {
        Fixture f = fixture();
        Mimic mimic = mimic();
        f.joker.addAbility(mimic);
        mimic.increaseCooldown(2);

        f.cast(f.shot, new UnitTarget(f.enemy));

        assertEquals(1, mimic.getCurrentCooldown(), "the other ability was refunded a turn");
        assertEquals(3, f.shot.getCurrentCooldown(),
            "the ability being cast pays its full cooldown - the cut fires before it is applied");
    }

    /** Walking and swinging are not casts; neither triggers the refund. */
    @Test
    void moveAndAttackTriggerNothing() {
        Fixture f = fixture();
        f.shot.increaseCooldown(3);
        Move move = (Move) f.joker.getAbilities().stream().filter(a -> a instanceof Move).findFirst().orElseThrow();

        f.cast(move, new TileTarget(f.map.getTile(new Position(1, 0))));

        assertEquals(3, f.shot.getCurrentCooldown());
    }

    /**
     * The rule that stops the refund from looping. Perplexing Shot is deliberately handed
     * back a zero cooldown, so nothing but the lock itself can be refusing the second cast.
     */
    @Test
    void anAbilityCannotBeCastTwiceInOneTurnEvenAtZeroCooldown() {
        Fixture f = fixture();

        f.cast(f.shot, new UnitTarget(f.enemy));
        f.shot.decreaseCooldown(99);

        assertTrue(f.shot.isReady(), "cooldown is genuinely clear");
        assertTrue(f.joker.isAbilityRestricted(f.shot));
        assertFalse(f.shot.canUse(f.state, new UnitTarget(f.enemy)), "but it still refuses");
        assertTrue(f.shot.getLegalTargets(f.state).isEmpty(),
            "so the client highlights nothing and the bot never offers it");
    }

    @Test
    void theLockClearsAtTheStartOfTheNextTurn() {
        Fixture f = fixture();
        f.cast(f.shot, new UnitTarget(f.enemy));
        f.shot.decreaseCooldown(99);

        f.startJokersTurn();

        assertFalse(f.joker.isAbilityRestricted(f.shot));
        assertTrue(f.shot.canUse(f.state, new UnitTarget(f.enemy)));
    }

    /** Move and Attack keep their own once-per-turn flags and are never touched by the lock. */
    @Test
    void moveAndAttackAreNeverLockedByTheRule() {
        Fixture f = fixture();
        Move move = (Move) f.joker.getAbilities().stream().filter(a -> a instanceof Move).findFirst().orElseThrow();
        Attack attack = (Attack) f.joker.getAbilities().stream()
            .filter(a -> a instanceof Attack).findFirst().orElseThrow();

        f.cast(move, new TileTarget(f.map.getTile(new Position(1, 0))));
        f.cast(attack, new UnitTarget(f.enemy));

        assertFalse(f.joker.isAbilityRestricted(move));
        assertFalse(f.joker.isAbilityRestricted(attack));
    }

    /** The lock is visible in the sidebar rather than being an unexplained greyed-out button. */
    @Test
    void namesWhatWasSpentThisTurnInItsTooltip() {
        Fixture f = fixture();
        var effect = f.joker.getActiveEffect(com.walnutt.effect.impl.SuperiorMasteryEffect.class).orElseThrow();
        assertEquals(null, effect.getExtraInfo(), "nothing to report before anything is cast");

        f.cast(f.shot, new UnitTarget(f.enemy));

        assertEquals("Spent this turn: Perplexing Shot", effect.getExtraInfo());
    }

    private static Mimic mimic() {
        return new Mimic(new AbilityDefinition("Mimic", "active", "desc", Map.of(
            "cooldown", 2.0, "cast_range", 3.0, "duration", 10.0, "steal_window", 3.0, "max_abilities", 1.0)));
    }
}
