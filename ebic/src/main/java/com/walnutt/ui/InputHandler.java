package com.walnutt.ui;

import java.util.List;

import com.walnutt.combat.Attribute;
import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.map.Tile;
import com.walnutt.unit.Unit;

/**
 * Pluggable input source. A terminal MVP implements this with a Scanner; a future
 * frontend implements the same interface (e.g. reading from a queue fed by network
 * requests) without the engine (Game/TurnManager/Ability) changing at all.
 */
public interface InputHandler {
    ActionChoice chooseAction(GameState state, Player player);

    /**
     * {@code opponent} is the other party in this encounter (whichever of attacker/defender
     * isn't {@code unit}) - a UI can use it to show/highlight both sides together.
     *
     * The answer must be one of {@code unit.getUsableAttributes()}: a unit cannot bring an
     * attribute it has none of. No extra parameter carries that set, because every
     * implementation already has {@code unit} and can ask it directly - which keeps one
     * source of truth for the rule. A unit with no usable attribute is never asked at all
     * (see Attack.buildEncounter), so implementations may assume the list is non-empty.
     */
    Attribute chooseAttribute(GameState state, Unit unit, Unit opponent);

    /**
     * Requests both sides' attribute picks for one encounter. Default is sequential
     * (attacker asked, then defender) - correct for a single shared terminal, where
     * there's no real "simultaneity" to offer anyway. A networked handler with two
     * independent human seats (see WebInputHandler) should override this to request
     * both picks concurrently, so neither player waits on the other's answer before
     * even seeing the prompt.
     */
    default Attribute[] chooseAttributePair(GameState state, Unit attacker, Unit defender) {
        Attribute attackerChoice = chooseAttribute(state, attacker, defender);
        Attribute defenderChoice = chooseAttribute(state, defender, attacker);
        return new Attribute[] { attackerChoice, defenderChoice };
    }

    /**
     * Generic "pick one of these" dialogue, raised from inside an ability's own onUse
     * (Maxwell's Eureka choosing which gadget to construct) the same way Attack.onUse
     * already raises chooseAttributePair - so no engine code needs to know such a
     * dialogue exists.
     *
     * Defaulted rather than abstract for the same reason chooseAttributePair is: the
     * scripted InputHandler doubles across the test suite would otherwise all need
     * updating for a method they never exercise. Taking the first option is a
     * deterministic, always-legal answer - but it is NOT a real implementation, and any
     * handler fronting an actual decision-maker (a UI, the bot) must override it.
     *
     * An option with {@code enabled == false} is shown but must never be returned - Shawl's
     * dialogue lists an ally's already-unlocked abilities so the player can see why they are
     * not choosable, and answering with one would upgrade something twice.
     *
     * @param unit    whose ability raised the dialogue, so a UI can show who is choosing
     * @param title   what is being chosen, e.g. "Construct a gadget"
     * @return one of the ENABLED {@code options}, or null if the player cancelled or there
     *         was nothing choosable. Every caller must treat null as "do nothing and charge
     *         nothing" - it is what makes cancelling a cast free.
     */
    default ChoiceOption chooseOption(GameState state, Unit unit, String title, List<ChoiceOption> options) {
        return options.stream().filter(ChoiceOption::enabled).findFirst().orElse(null);
    }

    /** Draft phase: player picks one of the offered candidates. */
    UnitDefinition choosePick(GameState state, Player player, List<UnitDefinition> options);

    /**
     * Whether this seat would rather not be offered {@code definition} at all - asked by the
     * draft BEFORE a pair is shown to anyone, so a refusal can be answered by quietly dealing
     * a different pair rather than by the seat picking a hero it cannot play.
     *
     * False for a human: a person is offered the whole roster and decides for themselves.
     * The computer refuses the handful of heroes it cannot play well (ai.HeroRatings.AVOIDED),
     * which used to show up as the bot simply picking one anyway whenever a round happened to
     * offer two of them.
     */
    default boolean refusesToDraft(Player player, UnitDefinition definition) {
        return false;
    }

    /** Placement phase: player picks a legal tile for the given unit. */
    Tile choosePlacementTile(GameState state, Player player, Unit unitToPlace, List<Tile> candidates);
}
