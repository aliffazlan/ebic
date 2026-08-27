package com.walnutt.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.StatusFlag;
import com.walnutt.effect.Effect;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.ChampionUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/**
 * The evaluator is the part of the bot meant to outlive the current strategy - a future
 * search calls it at every leaf - so it is pinned by the properties that must hold for
 * any sane definition of "winning", rather than by specific numbers that would have to be
 * rewritten every time a weight is tuned.
 */
class PositionEvaluatorTest {
    private final PositionEvaluator evaluator = new PositionEvaluator(BotConfig.standard());

    @Test
    void aSymmetricPositionIsLevelForBothSides() {
        GameState state = mirroredState();

        assertEquals(evaluator.evaluate(state, Team.PLAYER_ONE),
            evaluator.evaluate(state, Team.PLAYER_TWO), 1e-9,
            "identical armies in mirrored positions must evaluate equally");
    }

    @Test
    void killingTheEnemyChampionIsTheBestPossibleOutcome() {
        GameState state = mirroredState();
        double before = evaluator.evaluate(state, Team.PLAYER_ONE);

        state.getPlayer(Team.PLAYER_TWO).getChampion().orElseThrow().getHealthPool().setCurrent(0);

        double after = evaluator.evaluate(state, Team.PLAYER_ONE);
        assertTrue(after > before);
        assertEquals(PositionEvaluator.WIN, after, 1e-9, "a won match should evaluate as won, not merely as ahead");
    }

    @Test
    void losingYourOwnChampionIsTheWorstPossibleOutcome() {
        GameState state = mirroredState();
        state.getPlayer(Team.PLAYER_ONE).getChampion().orElseThrow().getHealthPool().setCurrent(0);

        assertEquals(-PositionEvaluator.WIN, evaluator.evaluate(state, Team.PLAYER_ONE), 1e-9);
    }

    @Test
    void damagingTheEnemyImprovesThePositionAndTakingDamageWorsensIt() {
        GameState state = mirroredState();
        double level = evaluator.evaluate(state, Team.PLAYER_ONE);

        Unit enemyBasic = state.getPlayer(Team.PLAYER_TWO).getUnits().get(1);
        enemyBasic.getHealthPool().setCurrent(enemyBasic.getHealth() - 100);
        double afterHurtingThem = evaluator.evaluate(state, Team.PLAYER_ONE);
        assertTrue(afterHurtingThem > level, "hurting the enemy should improve the position");

        Unit ownBasic = state.getPlayer(Team.PLAYER_ONE).getUnits().get(1);
        ownBasic.getHealthPool().setCurrent(ownBasic.getHealth() - 100);
        assertTrue(evaluator.evaluate(state, Team.PLAYER_ONE) < afterHurtingThem,
            "taking the same damage back should give the advantage up again");
    }

    @Test
    void aChampionIsWorthMoreThanABasicOfTheSameHealth() {
        Unit champion = new ChampionUnit("Champ", Team.PLAYER_ONE, new UnitStats(30, 30, 30, 500));
        Unit basic = new BasicUnit("Basic", Team.PLAYER_ONE, new UnitStats(30, 30, 30, 500));

        assertTrue(evaluator.unitValue(champion) > evaluator.unitValue(basic),
            "the champion is the win condition, so equal health should not mean equal worth");
    }

    /** A unit that cannot swing is not a present danger, whatever its stats say. */
    @Test
    void aDisarmedUnitCountsAsLessOfAThreat() {
        Unit armed = new BasicUnit("Armed", Team.PLAYER_TWO, new UnitStats(60, 10, 10, 300));
        Unit disarmed = new BasicUnit("Disarmed", Team.PLAYER_TWO, new UnitStats(60, 10, 10, 300));
        disarmed.addEffect(new Effect("Disarmed", "Cannot attack", 2) {
            @Override
            public java.util.Set<StatusFlag> getStatusFlags() {
                return java.util.Set.of(StatusFlag.DISARMED);
            }
        });

        assertTrue(evaluator.threat(disarmed) < evaluator.threat(armed));
    }

    /** Two identical armies, each anchored at opposite ends of the map. */
    private GameState mirroredState() {
        GameMap map = new GameMap(5);
        Player one = new Player("P1", Team.PLAYER_ONE);
        Player two = new Player("P2", Team.PLAYER_TWO);

        Unit championOne = new ChampionUnit("C1", Team.PLAYER_ONE, new UnitStats(50, 50, 50, 900));
        Unit basicOne = new BasicUnit("B1", Team.PLAYER_ONE, new UnitStats(20, 20, 20, 300));
        Unit championTwo = new ChampionUnit("C2", Team.PLAYER_TWO, new UnitStats(50, 50, 50, 900));
        Unit basicTwo = new BasicUnit("B2", Team.PLAYER_TWO, new UnitStats(20, 20, 20, 300));

        map.moveUnit(championOne, map.getTile(new Position(-5, 0)));
        map.moveUnit(basicOne, map.getTile(new Position(-4, 0)));
        map.moveUnit(championTwo, map.getTile(new Position(5, 0)));
        map.moveUnit(basicTwo, map.getTile(new Position(4, 0)));

        one.addUnit(championOne);
        one.addUnit(basicOne);
        two.addUnit(championTwo);
        two.addUnit(basicTwo);
        return new GameState(map, List.of(one, two), new Random(5));
    }
}
