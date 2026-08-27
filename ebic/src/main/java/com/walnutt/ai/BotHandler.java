package com.walnutt.ai;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.walnutt.combat.Attribute;
import com.walnutt.data.UnitDefinition;
import com.walnutt.game.DefaultArrangement;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.ui.ActionChoice;
import com.walnutt.ui.ConcurrentSetupHandler;
import com.walnutt.ui.InputHandler;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitType;

/**
 * A computer-controlled player, in the shape the engine already understands: it is just
 * another InputHandler/ConcurrentSetupHandler, so nothing in Game, TurnManager, Ability
 * or the web bridge needs to know a bot exists.
 *
 * The decision-making lives in {@link BotStrategy} and friends; this class is the
 * adapter, plus two guards that exist because a bot fails differently from a person:
 *
 *  - Any exception while thinking is caught and downgraded to "end turn". GameSession
 *    treats a thrown exception as a fatal match error, so an unhandled bot bug would
 *    kill a real player's game rather than merely play a bad turn.
 *  - Actions per turn are capped. TurnManager silently re-prompts when an action turns
 *    out to be illegal; a human eventually gives up, a bot would loop forever, pinning
 *    the match thread and spamming the opponent.
 */
public final class BotHandler implements InputHandler, ConcurrentSetupHandler {
    private static final Logger LOG = System.getLogger(BotHandler.class.getName());

    /**
     * Comfortably above a real turn's ceiling (one free attack per unit plus three
     * move-point actions), so this only ever trips on a genuine loop, never on a
     * legitimately busy turn.
     */
    private static final int MAX_ACTIONS_PER_TURN = 80;

    private final BotConfig config;
    private final BotStrategy strategy;
    private int actionsThisTurn;

    public BotHandler(BotConfig config) {
        this(config, new GreedyStrategy(config));
    }

    public BotHandler(BotConfig config, BotStrategy strategy) {
        this.config = config;
        this.strategy = strategy;
    }

    public BotConfig getConfig() {
        return config;
    }

    // ---- InputHandler: the match loop ----

    @Override
    public ActionChoice chooseAction(GameState state, Player player) {
        if (actionsThisTurn >= MAX_ACTIONS_PER_TURN) {
            LOG.log(Level.WARNING, "Bot hit its per-turn action cap; ending the turn to avoid looping.");
            return endTurn();
        }

        ActionChoice choice;
        try {
            choice = strategy.decide(state, player);
        } catch (RuntimeException e) {
            LOG.log(Level.ERROR, "Bot failed while choosing an action; ending its turn instead", e);
            return endTurn();
        }

        if (choice == null || choice.isEndTurn()) {
            return endTurn();
        }

        actionsThisTurn++;
        pause();
        return choice;
    }

    private ActionChoice endTurn() {
        actionsThisTurn = 0;
        return ActionChoice.endTurn();
    }

    /**
     * Only reachable if a future caller asks for one side's attribute without going
     * through {@link #chooseAttributePair}, which today nothing does - Attack.onUse is
     * the sole call site. Assumes the attacking role, the more consequential of the two
     * to get wrong, since the payoff matrix is not symmetric.
     */
    @Override
    public Attribute chooseAttribute(GameState state, Unit unit, Unit opponent) {
        return chooseAttributeAs(state, unit, opponent, AttributeChooser.Role.ATTACKER);
    }

    /**
     * The role-aware entry point. Exposed because a router splitting one encounter across
     * two different handlers (see TeamRoutingHandler) knows which side is attacking and
     * must be able to say so - the InputHandler interface itself has nowhere to express it.
     */
    public Attribute chooseAttributeAs(GameState state, Unit self, Unit opponent, AttributeChooser.Role role) {
        return AttributeChooser.choose(self, opponent, role, state.getRandom());
    }

    /**
     * Overridden so each side is solved in its actual role. The interface default calls
     * chooseAttribute twice without saying which unit is attacking, and the attacker is
     * the only one who deals damage, so treating both sides alike would have the bot
     * defending with the attacker's strategy.
     */
    @Override
    public Attribute[] chooseAttributePair(GameState state, Unit attacker, Unit defender) {
        return new Attribute[] {
            AttributeChooser.choose(attacker, defender, AttributeChooser.Role.ATTACKER, state.getRandom()),
            AttributeChooser.choose(defender, attacker, AttributeChooser.Role.DEFENDER, state.getRandom())
        };
    }

    // ---- InputHandler: the sequential (terminal/self-play) setup path ----

    @Override
    public UnitDefinition choosePick(GameState state, Player player, List<UnitDefinition> options) {
        return pickBest(options);
    }

    /**
     * The champion takes the tile deepest in its own territory; everything else takes the
     * most forward tile still free.
     *
     * Both orderings break ties on distance to the player's OWN anchor, which is not
     * decoration. The full match map is an elongated hexagon, so a great many tiles tie
     * on distance from the enemy anchor - on a radius-7 map the corner (7,0) and the
     * mid-field tile (4,3) are both exactly 14 away. Ordering on that alone let the
     * arbitrary winner of the tie decide, and it put one champion at the very front of
     * its own army. The own-anchor tiebreak makes the corner win outright.
     */
    @Override
    public Tile choosePlacementTile(GameState state, Player player, Unit unitToPlace, List<Tile> candidates) {
        Position enemyAnchor = enemyAnchor(state, player);
        Position ownAnchor = DefaultArrangement.anchorFor(state, player);
        Comparator<Tile> towardEnemy =
            Comparator.<Tile>comparingInt(tile -> state.getMap().getDistance(tile.getPosition(), enemyAnchor))
                .thenComparingInt(tile -> state.getMap().getDistance(tile.getPosition(), ownAnchor));
        Comparator<Tile> towardHome =
            Comparator.<Tile>comparingInt(tile -> -state.getMap().getDistance(tile.getPosition(), enemyAnchor))
                .thenComparingInt(tile -> state.getMap().getDistance(tile.getPosition(), ownAnchor));

        if (candidates.isEmpty()) {
            // PlacementFlow should never ask for a tile with nowhere to put one, but
            // orElse(candidates.get(0)) would evaluate its argument eagerly and throw
            // IndexOutOfBounds rather than report what actually went wrong.
            throw new IllegalArgumentException("No legal placement tiles offered for " + unitToPlace.getName());
        }
        Comparator<Tile> preference =
            unitToPlace.getUnitType() == UnitType.CHAMPION ? towardHome : towardEnemy;
        return candidates.stream().min(preference).orElseThrow();
    }

    // ---- ConcurrentSetupHandler: the web setup path ----

    @Override
    public UnitDefinition choosePick(GameState state, Player player, String roundLabel,
                                      List<UnitDefinition> options, List<UnitDefinition> opponentOptions) {
        pause();
        return pickBest(options);
    }

    @Override
    public Map<Unit, Position> arrangePlacement(GameState state, Player player,
                                                 Map<Unit, Position> defaultArrangement) {
        pause();
        try {
            return BotPlacement.arrange(state, player);
        } catch (RuntimeException e) {
            // A bad arrangement would abort the match before it started; the engine's own
            // default is always legal, so fall back to it rather than fail the match.
            LOG.log(Level.ERROR, "Bot failed to arrange its army; using the default layout", e);
            return defaultArrangement;
        }
    }

    private UnitDefinition pickBest(List<UnitDefinition> options) {
        if (options.isEmpty()) {
            throw new IllegalArgumentException("Draft offered no options to pick from");
        }
        UnitDefinition best = options.get(0);
        for (UnitDefinition option : options) {
            best = HeroRatings.preferred(best, option);
        }
        return best;
    }

    private Position enemyAnchor(GameState state, Player player) {
        for (Player other : state.getPlayers()) {
            if (other.getTeam() != player.getTeam()) {
                return DefaultArrangement.anchorFor(state, other);
            }
        }
        return DefaultArrangement.anchorFor(state, player);
    }

    /** Lets a human watching follow what the bot is doing. Zero in self-play and tests. */
    private void pause() {
        if (config.thinkDelayMillis() <= 0) {
            return;
        }
        try {
            Thread.sleep(config.thinkDelayMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
