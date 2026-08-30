package com.walnutt.ability.impl;

import java.util.ArrayList;
import java.util.List;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.AbilityFactory;
import com.walnutt.effect.impl.InsightEffect;
import com.walnutt.event.PostDamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.Stat;
import com.walnutt.status.StatModifier;
import com.walnutt.ui.ChoiceOption;
import com.walnutt.unit.Unit;

/**
 * Shawl - accrues Insight by dealing damage and spends it to permanently unlock one of an
 * ally's abilities, chosen from a dialogue at cast time.
 *
 * Structurally this is Maxwell's Eureka (impl/Eureka.java): a currency held as an Effect so
 * it renders itself, plus an InputHandler.chooseOption dialogue raised from inside onUse.
 * The differences are that the dialogue lists a chosen TARGET's abilities rather than a
 * fixed pool, and that its own upgrade pays Shawl per unlock he has granted.
 *
 * Cancelling costs nothing. Insight and the cooldown are spent only once a real choice comes
 * back, which is the same early-return-without-spending shape Eureka uses.
 */
public class HiddenPotential extends Ability {

    /** Option id for the placeholder shown when a target has nothing worth unlocking. */
    private static final String NOTHING_TO_UPGRADE = "__none__";

    private int cost;
    private int statBonus;
    private int hpBonus;
    /**
     * Abilities this Shawl has unlocked, anywhere, including this one. The counter his own
     * upgrade pays out on - see {@link #payOutBonus}.
     */
    private int upgradesGranted;
    /** How many of those have already been paid for, so a retroactive top-up cannot double up. */
    private int bonusesPaid;

    public HiddenPotential(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 1));
        setRange(definition.getInt("cast_range", 4));
        this.cost = definition.getInt("cost", 10);
        this.statBonus = definition.getInt("stat_bonus", 20);
        this.hpBonus = definition.getInt("hp_bonus", 100);
    }

    @Override
    protected void onUpgraded() {
        this.cost = statInt("cost", cost);
        this.statBonus = statInt("stat_bonus", statBonus);
        this.hpBonus = statInt("hp_bonus", hpBonus);
        // The retroactive payout is deliberately NOT made here: it needs a GameState to heal
        // with, and upgrade() has none. onUse tops it up immediately afterwards, and onUse is
        // the only path that ever upgrades anything.
    }

    /**
     * The pool lives on Shawl as an Effect so the running total renders in the sidebar with
     * no new UI. Created on attach rather than lazily, so it reads 0 from turn one rather
     * than appearing out of nowhere on first blood.
     */
    @Override
    protected void onAttached(Unit newOwner) {
        if (newOwner.getActiveEffect(InsightEffect.class).isEmpty()) {
            newOwner.addEffect(new InsightEffect(
                "Gained whenever this unit deals damage, and spent to unlock an ally's hidden potential.",
                cost));
        }
    }

    private InsightEffect pool() {
        return owner == null ? null : owner.getActiveEffect(InsightEffect.class).orElse(null);
    }

    public int getInsight() {
        InsightEffect pool = pool();
        return pool == null ? 0 : pool.getAmount();
    }

    public int getCost() {
        return cost;
    }

    public int getUpgradesGranted() {
        return upgradesGranted;
    }

    /**
     * One Insight per instance of damage Shawl deals - a swing, a tick of acid, anything
     * traced back to him.
     *
     * Hooked on the POST event rather than the mutable pre-damage one so a blow that was
     * cancelled, fully absorbed, or reduced to nothing pays nothing. Handlers see every
     * unit's damage, hence the source check - the same shape Frostbite and Dispersion use.
     */
    @Override
    public void onDamageTaken(GameState state, PostDamageEvent event) {
        Unit self = getOwner();
        InsightEffect pool = pool();
        if (self == null || self.isDead() || pool == null) {
            return;
        }
        if (event.damageEvent().getSource() != self || event.damageEvent().getDamage() <= 0) {
            return;
        }
        // Self-damage would be a way to farm the currency without ever engaging.
        if (event.target() == self) {
            return;
        }
        pool.add(1);
        pool.setNextThreshold(cost);
    }

    /**
     * Any living ally in range, Shawl himself included, and enough Insight to pay.
     *
     * Deliberately NOT gated on the target having anything worth unlocking: the dialogue
     * says so instead. Refusing the click outright would leave a player guessing why an
     * ally was not highlighted, and the answer ("all of their abilities are summon kit") is
     * not something the board can show.
     */
    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (!(target instanceof UnitTarget unitTarget)) {
            return false;
        }
        Unit ally = unitTarget.getUnit();
        InsightEffect pool = pool();
        return ally != null && !ally.isDead()
            && ally.getTeam() == owner.getTeam()
            && isInRange(state, ally.getPosition())
            && pool != null && pool.canAfford(cost);
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit ally = ((UnitTarget) target).getUnit();
        List<ChoiceOption> options = optionsFor(state, ally);

        ChoiceOption chosen = state.getInputHandler().chooseOption(state, owner,
            "Unlock " + ally.getName() + "'s hidden potential (" + cost + " Insight)", options);
        // Null is a cancel, and NOTHING_TO_UPGRADE can only come back from a handler that
        // ignores the disabled flag. Neither spends anything.
        if (chosen == null || NOTHING_TO_UPGRADE.equals(chosen.id())) {
            return;
        }

        Ability picked = findUpgradeable(ally, chosen.id());
        if (picked == null) {
            return;
        }
        InsightEffect pool = pool();
        if (pool == null || !pool.spend(cost)) {
            return;
        }

        picked.upgrade();
        upgradesGranted++;
        payOutBonus(state);
        pool.setNextThreshold(cost);

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    /**
     * Shawl's own upgrade: every unlock he has granted is worth {@code stat_bonus} to each
     * attribute and {@code hp_bonus} to his maximum and current health.
     *
     * Paid as a top-up rather than a recalculation, so the first payout covers every unlock
     * granted before it (the "applies retroactively" half of the design) and each later one
     * covers exactly itself. Until he is upgraded this is a no-op, and bonusesPaid stays at
     * zero so the backlog is still waiting when he is.
     */
    private void payOutBonus(GameState state) {
        if (!isUpgraded() || owner == null) {
            return;
        }
        int owed = upgradesGranted - bonusesPaid;
        if (owed <= 0) {
            return;
        }
        bonusesPaid = upgradesGranted;
        owner.addPermanentModifier(StatModifier.flat(Stat.STRENGTH, (double) statBonus * owed, this));
        owner.addPermanentModifier(StatModifier.flat(Stat.AGILITY, (double) statBonus * owed, this));
        owner.addPermanentModifier(StatModifier.flat(Stat.INTELLIGENCE, (double) statBonus * owed, this));
        // Raises the ceiling; the heal is what actually fills the new room, since a bigger
        // maximum on its own leaves current health where it was.
        owner.addPermanentModifier(StatModifier.flat(Stat.MAX_HEALTH, (double) hpBonus * owed, this));
        owner.heal(state, hpBonus * owed);
    }

    /**
     * One entry per ability of the target worth showing, in the order it wields them.
     *
     * Move, Attack and a summon's own kit are absent entirely: they carry no upgrade in the
     * design, and listing "Move - cannot be upgraded" on every single unit would be noise.
     * Everything that IS designed to be upgradeable is listed even when it cannot be picked,
     * so the reason is on screen rather than inferred from an absence.
     */
    private List<ChoiceOption> optionsFor(GameState state, Unit ally) {
        List<ChoiceOption> options = new ArrayList<>();
        for (Ability ability : ally.getAbilities()) {
            AbilityDefinition definition = ability.getDefinition();
            String id = ability.getDefinitionId();
            if (id == null || definition == null || !definition.isUpgradeable()) {
                continue;
            }
            if (!ability.canUpgrade()) {
                options.add(ChoiceOption.disabled(id, ability.getName(),
                    ability.getDescription(), "Already upgraded"));
            } else if (!AbilityFactory.isUpgradeImplemented(id)) {
                options.add(ChoiceOption.disabled(id, ability.getName(),
                    ability.getDescription(), "Not yet available"));
            } else {
                options.add(new ChoiceOption(id, ability.getName(),
                    definition.formattedUpgradeSummary(), upgradeDetail(ability)));
            }
        }
        if (options.isEmpty()) {
            options.add(ChoiceOption.disabled(NOTHING_TO_UPGRADE, ally.getName(),
                "This unit has no abilities that can be unlocked.", "Nothing to upgrade"));
        }
        return options;
    }

    private static String upgradeDetail(Ability ability) {
        return ability.isPassive() ? "Passive" : "Cooldown: " + ability.getMaxCooldown()
            + " turn" + (ability.getMaxCooldown() == 1 ? "" : "s");
    }

    /**
     * Re-resolves the chosen id against the target's live kit rather than trusting a
     * reference captured before the dialogue: the answer arrives over a socket and an
     * arbitrary amount of time can pass, in which the ally can die or lose the ability.
     */
    private static Ability findUpgradeable(Unit ally, String definitionId) {
        if (ally.isDead()) {
            return null;
        }
        for (Ability ability : ally.getAbilities()) {
            if (definitionId.equals(ability.getDefinitionId())
                && ability.canUpgrade()
                && AbilityFactory.isUpgradeImplemented(definitionId)) {
                return ability;
            }
        }
        return null;
    }
}
