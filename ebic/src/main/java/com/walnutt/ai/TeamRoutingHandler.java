package com.walnutt.ai;

import java.util.List;
import java.util.Map;

import com.walnutt.combat.Attribute;
import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.ui.ActionChoice;
import com.walnutt.ui.ChoiceOption;
import com.walnutt.ui.ConcurrentSetupHandler;
import com.walnutt.ui.InputHandler;
import com.walnutt.unit.Unit;

/**
 * Gives each team its own handler, so one match can mix a human seat and a bot seat -
 * or two bot seats, which is what self-play tuning needs.
 *
 * Every method dispatches on whichever team the call is about, which the engine always
 * supplies (a Player for turn/setup calls, a Unit for encounter calls).
 */
public final class TeamRoutingHandler implements InputHandler, ConcurrentSetupHandler {
    private final Map<Team, InputHandler> input;
    private final Map<Team, ConcurrentSetupHandler> setup;

    public TeamRoutingHandler(InputHandler playerOneInput, ConcurrentSetupHandler playerOneSetup,
                               InputHandler playerTwoInput, ConcurrentSetupHandler playerTwoSetup) {
        this.input = Map.of(Team.PLAYER_ONE, playerOneInput, Team.PLAYER_TWO, playerTwoInput);
        this.setup = Map.of(Team.PLAYER_ONE, playerOneSetup, Team.PLAYER_TWO, playerTwoSetup);
    }

    /** Convenience for the usual case, where each seat is one object filling both roles. */
    public static <A extends InputHandler & ConcurrentSetupHandler,
                   B extends InputHandler & ConcurrentSetupHandler>
            TeamRoutingHandler of(A playerOne, B playerTwo) {
        return new TeamRoutingHandler(playerOne, playerOne, playerTwo, playerTwo);
    }

    @Override
    public ActionChoice chooseAction(GameState state, Player player) {
        return input.get(player.getTeam()).chooseAction(state, player);
    }

    @Override
    public Attribute chooseAttribute(GameState state, Unit unit, Unit opponent) {
        return input.get(unit.getTeam()).chooseAttribute(state, unit, opponent);
    }

    /**
     * Resolves each side through its own team's handler, in its actual role.
     *
     * This override is mandatory, and the reason is subtle enough to be worth stating.
     * The interface default would route correctly - it calls this class's own
     * chooseAttribute, which dispatches by team - but chooseAttribute cannot say *which
     * side is attacking*, and the encounter is not symmetric: only the attacker deals
     * damage, so the two sides are solving different games. BotHandler.chooseAttribute
     * therefore has to assume the attacking role, and under the default path a bot
     * defending would answer with the attacker's strategy. Here the roles are known, so
     * each seat is asked the question it is actually facing.
     *
     * Resolving sequentially is fine in a way it would not be for two humans: a bot seat
     * answers instantly, so asking it first costs a human seat nothing. Only a
     * human-versus-human encounter needs genuinely concurrent prompting, and that case
     * never reaches this class - it keeps using WebInputHandler's own override.
     */
    @Override
    public Attribute[] chooseAttributePair(GameState state, Unit attacker, Unit defender) {
        InputHandler attackerHandler = input.get(attacker.getTeam());
        InputHandler defenderHandler = input.get(defender.getTeam());

        Attribute[] answers = new Attribute[2];
        // Bot seats first, so a human seat is never left waiting on the bot's arithmetic.
        if (attackerHandler instanceof BotHandler bot) {
            answers[0] = bot.chooseAttributeAs(state, attacker, defender, AttributeChooser.Role.ATTACKER);
        }
        if (defenderHandler instanceof BotHandler bot) {
            answers[1] = bot.chooseAttributeAs(state, defender, attacker, AttributeChooser.Role.DEFENDER);
        }
        if (answers[0] == null) {
            answers[0] = attackerHandler.chooseAttribute(state, attacker, defender);
        }
        if (answers[1] == null) {
            answers[1] = defenderHandler.chooseAttribute(state, defender, attacker);
        }
        return answers;
    }

    /**
     * Unlike the attribute encounter, one dialogue belongs to exactly one seat - whoever
     * owns the casting unit - so plain team dispatch is all this needs.
     */
    @Override
    public ChoiceOption chooseOption(GameState state, Unit unit, String title, List<ChoiceOption> options) {
        return input.get(unit.getTeam()).chooseOption(state, unit, title, options);
    }

    @Override
    public UnitDefinition choosePick(GameState state, Player player, List<UnitDefinition> options) {
        return input.get(player.getTeam()).choosePick(state, player, options);
    }

    /**
     * Per seat, which is the whole point: in a bot match the computer refuses its handful of
     * heroes while the human across the table is still offered every one of them.
     */
    @Override
    public boolean refusesToDraft(Player player, UnitDefinition definition) {
        return input.get(player.getTeam()).refusesToDraft(player, definition);
    }

    @Override
    public Tile choosePlacementTile(GameState state, Player player, Unit unitToPlace, List<Tile> candidates) {
        return input.get(player.getTeam()).choosePlacementTile(state, player, unitToPlace, candidates);
    }

    @Override
    public UnitDefinition choosePick(GameState state, Player player, String roundLabel,
                                      List<UnitDefinition> options, List<UnitDefinition> opponentOptions) {
        return setup.get(player.getTeam()).choosePick(state, player, roundLabel, options, opponentOptions);
    }

    @Override
    public Map<Unit, Position> arrangePlacement(GameState state, Player player,
                                                 Map<Unit, Position> defaultArrangement) {
        return setup.get(player.getTeam()).arrangePlacement(state, player, defaultArrangement);
    }
}
