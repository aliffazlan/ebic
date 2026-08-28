package com.walnutt.ability.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.AbilityFactory;
import com.walnutt.effect.impl.InspirationEffect;
import com.walnutt.event.AbilityCastEvent;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.ui.ChoiceOption;
import com.walnutt.unit.Unit;

/**
 * Maxwell - accrues Inspiration and spends it to permanently construct a gadget, chosen
 * from a dialogue at cast time.
 *
 * The gadgets are ordinary abilities registered in AbilityFactory exactly like everything
 * else; the only thing unusual is that they reach a unit mid-match instead of at
 * construction. Nothing downstream needs to care - the web bridge resolves abilities off
 * unit.getAbilities() fresh on every prompt, and UnitSnapshot is rebuilt on every push.
 */
public class Eureka extends Ability {

    /**
     * Maxwell's buildable pool. Declared here rather than in maxwell.json because these
     * are not part of his starting kit - UnitFactory would attach every one of them at
     * draft time. MaxwellGadgetPoolTest fails the build if an id here has no JSON
     * definition or no AbilityFactory entry, since either mistake would silently make a
     * gadget unbuildable forever.
     *
     * Order is fixed and is the order the dialogue lists them in.
     */
    public static final List<String> GADGET_IDS = List.of(
        "plasma_cannon",
        "energy_shield",
        "shrink_ray",
        "killer_drone",
        "homing_missile",
        "translocation",
        "nanobots",
        "gyroscope",
        "reload",
        "capacitor_bank"
    );

    private final int passiveInspiration;
    private final int bonusInspiration;
    private final int baseCost;
    private final int costIncrease;
    /**
     * Ids already constructed. Tracked directly rather than by scanning owner.getAbilities()
     * and normalizing names back into ids - that round-trip is not reliable (Harbinger's
     * "Sanity's Eclipse" normalizes to sanity_s_eclipse, not its filename sanity_eclipse),
     * so a gadget whose name did not survive it would be offered forever.
     */
    private final Set<String> built = new LinkedHashSet<>();
    /** Whether the owner has taken any action this turn - drives the bonus, reset each own turn start. */
    private boolean actedThisTurn;

    public Eureka(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 1));
        setRange(0); // self-cast: no target, no distance to check
        this.passiveInspiration = definition.getInt("passive_inspiration", 2);
        this.bonusInspiration = definition.getInt("bonus_inspiration", 1);
        this.baseCost = definition.getInt("cost", 6);
        this.costIncrease = definition.getInt("cost_increase", 10);
    }

    /** What the next gadget costs: the base, plus the increase for each one already built. */
    public int currentCost() {
        return baseCost + built.size() * costIncrease;
    }

    public int getInspiration() {
        InspirationEffect pool = pool();
        return pool == null ? 0 : pool.getAmount();
    }

    /** Ids Maxwell hasn't constructed yet, in GADGET_IDS order. */
    public List<String> remainingGadgets() {
        List<String> remaining = new ArrayList<>();
        for (String id : GADGET_IDS) {
            if (!built.contains(id)) {
                remaining.add(id);
            }
        }
        return remaining;
    }

    /**
     * The Inspiration pool lives as an effect on Maxwell so the count renders in the
     * sidebar for free. Created on attach rather than lazily so it is visible from turn
     * one, before any has been earned.
     */
    @Override
    protected void onAttached(Unit newOwner) {
        if (newOwner.getActiveEffect(InspirationEffect.class).isEmpty()) {
            newOwner.addEffect(new InspirationEffect(
                "Accrues every turn and is spent to construct gadgets.", baseCost));
        }
    }

    /**
     * Looked up by the marker subclass, not by ResourceEffect: once the Capacitor Bank is
     * built Maxwell carries two of those, and getActiveEffect returns whichever comes first.
     */
    private InspirationEffect pool() {
        return owner == null ? null : owner.getActiveEffect(InspirationEffect.class).orElse(null);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target) || !(target instanceof NoTarget)) {
            return false;
        }
        InspirationEffect pool = pool();
        // Deliberately unusable rather than castable-then-refused: the player sees a
        // greyed-out button with the price, not a click that bounces.
        return pool != null && pool.canAfford(currentCost()) && !remainingGadgets().isEmpty();
    }

    @Override
    public void onUse(GameState state, Target target) {
        List<String> remaining = remainingGadgets();
        List<ChoiceOption> options = new ArrayList<>();
        for (String id : remaining) {
            AbilityDefinition definition = state.getAbilityDefinitions().get(id);
            if (definition == null || !AbilityFactory.isImplemented(id)) {
                continue;
            }
            options.add(new ChoiceOption(id, definition.name(), definition.formattedDescription(),
                describeCost(definition)));
        }
        if (options.isEmpty()) {
            return; // nothing buildable; leave the cooldown and the Inspiration untouched
        }

        ChoiceOption chosen = state.getInputHandler().chooseOption(state, owner,
            "Construct a gadget (" + currentCost() + " Inspiration)", options);
        if (chosen == null) {
            return;
        }
        AbilityDefinition definition = state.getAbilityDefinitions().get(chosen.id());
        if (definition == null || !AbilityFactory.isImplemented(chosen.id())) {
            return;
        }

        InspirationEffect pool = pool();
        if (pool == null || !pool.spend(currentCost())) {
            return;
        }
        built.add(chosen.id());
        pool.setNextThreshold(currentCost());
        owner.addAbility(AbilityFactory.create(chosen.id(), definition));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    private static String describeCost(AbilityDefinition definition) {
        if (definition.isPassive()) {
            return "Passive";
        }
        int cooldown = definition.getInt("cooldown", 0);
        return cooldown <= 0 ? "No cooldown" : "Cooldown: " + cooldown + " turn" + (cooldown == 1 ? "" : "s");
    }

    /**
     * Any action by Maxwell arms the bonus. TurnManager publishes an AbilityCastEvent
     * around every ability it runs, Move and Attack included, so this one hook covers all
     * three without inspecting the turn flags separately.
     */
    @Override
    public void onAbilityUsed(GameState state, AbilityCastEvent event) {
        if (event.phase() == AbilityCastEvent.Phase.POST && event.user() == getOwner()) {
            actedThisTurn = true;
        }
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        if (getOwner() != null && event.team() == getOwner().getTeam()) {
            actedThisTurn = false;
        }
    }

    /**
     * Awarded at the END of Maxwell's turn, not the start: that way the number showing
     * when a turn begins is exactly what can be spent during it, and "acted this turn"
     * has actually been decided by the time it is read.
     */
    @Override
    public void onTurnEnd(GameState state, TurnEndEvent event) {
        Unit self = getOwner();
        if (self == null || self.isDead() || event.team() != self.getTeam()) {
            return;
        }
        InspirationEffect pool = pool();
        if (pool == null) {
            return;
        }
        pool.add(passiveInspiration + (actedThisTurn ? bonusInspiration : 0));
        pool.setNextThreshold(currentCost());
    }
}
