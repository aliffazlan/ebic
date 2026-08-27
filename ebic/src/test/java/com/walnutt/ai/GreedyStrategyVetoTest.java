package com.walnutt.ai;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.AbilityFactory;
import com.walnutt.data.JsonDataLoader;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.ui.ActionChoice;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/**
 * Several abilities can legally be aimed at your own units - Blizzard's canUse has no
 * team check at all, and Soul Rip deliberately accepts either side. A hint says "never"
 * by scoring {@link Double#NEGATIVE_INFINITY}, and that veto has to be absolute: it must
 * survive the blunder path, which exists so that a future easier difficulty (and the
 * random-play baseline) can pick a worse move on purpose. Picking a *worse* move and
 * freezing your own teammate are not the same thing.
 */
class GreedyStrategyVetoTest {

    @Test
    void aVetoedAbilityIsNeverChosenEvenWhenTheBotIsBlunderingEveryTurn() {
        GameState state = stateWithBlizzardAndAnAdjacentAlly();
        Player player = state.getPlayer(Team.PLAYER_ONE);
        Unit ally = player.getUnits().get(1);

        // blunderRate 1.0: every single decision comes from the random pool.
        BotConfig alwaysBlunders = BotConfig.standard().withoutThinkDelay().withBlunderRate(1.0);
        BotStrategy strategy = new GreedyStrategy(alwaysBlunders);

        for (int i = 0; i < 500; i++) {
            state.setRemainingMoves(3);
            player.getUnits().forEach(Unit::resetTurnFlags);

            ActionChoice choice = strategy.decide(state, player);
            if (choice.isEndTurn()) {
                continue;
            }
            if (choice.getTarget() instanceof UnitTarget unitTarget) {
                assertNotSame(ally, unitTarget.getUnit(),
                    "the bot blundered into casting " + choice.getAbility().getName() + " on its own ally");
            }
        }
    }

    /** The veto is a hint decision, so confirm the hint really does refuse an ally outright. */
    @Test
    void theBlizzardHintRefusesAnAllyAndAcceptsAnEnemy() {
        GameState state = stateWithBlizzardAndAnAdjacentAlly();
        Player player = state.getPlayer(Team.PLAYER_ONE);
        Unit caster = player.getUnits().get(0);
        Unit ally = player.getUnits().get(1);
        Unit enemy = state.getPlayer(Team.PLAYER_TWO).getUnits().get(0);

        Ability blizzard = caster.getAbilities().stream()
            .filter(a -> a.getName().equalsIgnoreCase("Blizzard")).findFirst().orElseThrow();
        AbilityHint hint = AbilityHints.forAbility(blizzard.getName());
        assertNotSame(AbilityHints.GENERIC, hint, "Blizzard must have its own hint - it can target allies");

        BotConfig config = BotConfig.standard();
        PositionEvaluator evaluator = new PositionEvaluator(config);
        double onAlly = hint.score(new HintContext(state, caster, blizzard, new UnitTarget(ally), config, evaluator));
        double onEnemy = hint.score(new HintContext(state, caster, blizzard, new UnitTarget(enemy), config, evaluator));

        assertTrue(onAlly == Double.NEGATIVE_INFINITY, "casting Blizzard on an ally must be vetoed outright");
        assertTrue(onEnemy > 0, "casting Blizzard on an enemy should be worth doing");
    }

    /** Yuki plus a teammate she could freeze, and one enemy she should freeze instead. */
    private GameState stateWithBlizzardAndAnAdjacentAlly() {
        Map<String, AbilityDefinition> definitions =
            new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot()).loadAllAbilities();
        AbilityDefinition blizzardDefinition = definitions.values().stream()
            .filter(d -> "Blizzard".equalsIgnoreCase(d.name()))
            .findFirst().orElseThrow(() -> new IllegalStateException("no Blizzard in design_ideas/"));

        GameMap map = new GameMap(4);
        Unit caster = new EliteUnit("Caster", Team.PLAYER_ONE, new UnitStats(15, 20, 40, 450, 2));
        caster.addAbility(new Move());
        caster.addAbility(new Attack());
        caster.addAbility(AbilityFactory.create("blizzard", blizzardDefinition));

        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(20, 20, 20, 300));
        ally.addAbility(new Move());
        ally.addAbility(new Attack());
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(20, 20, 20, 300));
        enemy.addAbility(new Attack());

        map.moveUnit(caster, map.getTile(new Position(0, 0)));
        map.moveUnit(ally, map.getTile(new Position(1, 0)));
        map.moveUnit(enemy, map.getTile(new Position(-1, 0)));

        Player one = new Player("P1", Team.PLAYER_ONE);
        one.addUnit(caster);
        one.addUnit(ally);
        Player two = new Player("P2", Team.PLAYER_TWO);
        two.addUnit(enemy);

        GameState state = new GameState(map, List.of(one, two), new Random(4));
        state.setRemainingMoves(3);
        state.setInputHandler(new BotHandler(BotConfig.standard().withoutThinkDelay()));
        assertNotNull(state.getMap().getTile(new Position(0, 0)));
        return state;
    }
}
