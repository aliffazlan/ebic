package com.walnutt.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.walnutt.status.Stat;
import com.walnutt.unit.Unit;
import com.walnutt.web.Identifiers;

/**
 * Registry of per-ability scoring knowledge, keyed by the same normalized ability id
 * the JSON filenames and web DTOs already use - deliberately mirroring
 * data.AbilityFactory, so teaching the bot about an ability is one line in one place,
 * exactly like registering the ability itself was.
 *
 * Only a minority of abilities are hinted. That is the intended state: an unhinted
 * ability still gets played legally and sensibly by {@link #GENERIC}, and the ones
 * worth teaching surface naturally from self-play. Two categories genuinely need a
 * hint rather than the fallback:
 *   - abilities whose canUse permits targeting your own units (Blizzard roots ANY unit,
 *     Soul Rip damages enemies but heals allies), where the generic rule would happily
 *     root a teammate;
 *   - abilities whose value is not visible in their target (Duel's payoff is the
 *     permanent stat gain, Doom's is an escalating curse with no duration).
 *
 * Passive abilities never reach here - they are filtered out before enumeration - so
 * there is no point hinting Counterstrike, Cripple, Poison Sting and friends.
 */
public final class AbilityHints {

    /**
     * The fallback every unhinted ability uses. It judges only by what is observable
     * without knowing the ability: who it is pointed at, how many enemies are near where
     * it lands, and how significant the cooldown suggests it is.
     */
    public static final AbilityHint GENERIC = context -> {
        BotConfig config = context.config();
        double base = config.genericAbilityValue();
        // A longer cooldown implies a bigger effect, but sub-linearly - a 12-turn
        // cooldown is not six times the payoff of a 2-turn one.
        base *= 1 + Math.sqrt(Math.max(0, context.ability().getMaxCooldown())) / 4.0;

        Unit target = context.targetUnit();
        if (target != null) {
            if (context.isEnemy(target)) {
                return base * context.targetPriority(target);
            }
            // An unknown ability aimed at an ally is probably a buff, but "probably" is
            // doing real work here, so it must score below any enemy-facing option.
            return base * 0.4;
        }

        // Tile or self-targeted: worth roughly what it can reach.
        int enemiesNearby = context.unitsNearAim(1, true).size();
        return base * (0.5 + 0.5 * enemiesNearby);
    };

    private static final Map<String, AbilityHint> HINTS = new HashMap<>();

    static {
        // --- Abilities that can legally hit your own side ---

        // Roots and damages ANY unit, ally included (Blizzard.canUse has no team check).
        register("blizzard", context -> {
            Unit target = context.targetUnit();
            if (!context.isEnemy(target)) {
                return Double.NEGATIVE_INFINITY;
            }
            double damage = context.ability().getMaxCooldown() > 0 ? 50 : 25; // two ticks of 25
            return damage * context.targetPriority(target);
        });

        // Damages an enemy, heals an ally - scaled by the caster's strength advantage,
        // so it is worthless against something stronger than the caster.
        register("soul_rip", context -> {
            Unit target = context.targetUnit();
            if (target == null) {
                return 0;
            }
            double strengthEdge = context.user().getEffective(Stat.STRENGTH)
                - target.getEffective(Stat.STRENGTH);
            if (context.isEnemy(target)) {
                if (strengthEdge <= 0) {
                    return Double.NEGATIVE_INFINITY; // no damage, just a wasted cooldown
                }
                return strengthEdge * 0.5 * context.targetPriority(target);
            }
            // Healing an ally is only worth it if they have damage to heal.
            double missing = context.woundedFraction(target);
            if (strengthEdge <= 0 || missing < 0.25) {
                return Double.NEGATIVE_INFINITY;
            }
            return strengthEdge * 0.5 * missing * 1.5;
        });

        // --- Abilities whose worth is not visible in the target ---

        // The payoff is winning the duel: permanent +20 to every attribute and a half-health
        // heal. Both units are locked out meanwhile, so it is a good trade only when the
        // bot expects to win the exchange.
        register("duel", context -> {
            Unit target = context.targetUnit();
            if (target == null) {
                return Double.NEGATIVE_INFINITY;
            }
            double ourDamage = AttributeChooser.expectedDamage(context.user(), target);
            double theirDamage = AttributeChooser.expectedDamage(target, context.user());
            double turnsToKillThem = ourDamage <= 0 ? 999 : target.getHealth() / ourDamage;
            double turnsToKillUs = theirDamage <= 0 ? 999 : context.user().getHealth() / theirDamage;
            if (turnsToKillThem >= turnsToKillUs) {
                return Double.NEGATIVE_INFINITY; // losing a duel is catastrophic, never gamble
            }
            return 90 * context.targetPriority(target);
        });

        // No duration - it ends only when the cursed unit gets a kill. Best spent on
        // something that will struggle to kill anything, and it silences on top.
        register("doom", context -> {
            Unit target = context.targetUnit();
            if (!context.isEnemy(target)) {
                return Double.NEGATIVE_INFINITY;
            }
            // Escalating 20/turn and a silence; worth most on a high-value target that
            // is unlikely to farm a kill and clear it.
            double killLikelihood = context.isChampion(target) ? 0.6 : 1.0;
            return 120 * context.targetPriority(target) * killLikelihood;
        });

        // Scales with the caster's intelligence lead over each enemy caught in the blast,
        // and detonates a turn later - so it wants a cluster, not a single body.
        // Keyed on the normalized ability NAME ("Sanity's Eclipse"), which is not the same
        // as its JSON filename (sanity_eclipse.json) - the apostrophe becomes a separator.
        register("sanity_s_eclipse", AbilityHints::scoreSanityEclipse);

        // Compares allies to enemies in the area: damage when ahead, healing when behind.
        register("overwhelming_odds", context -> {
            List<Unit> enemies = context.unitsNearAim(3, true);
            List<Unit> allies = context.unitsNearAim(3, false);
            int advantage = allies.size() - enemies.size();
            if (advantage > 0 && !enemies.isEmpty()) {
                return 16.0 * advantage * enemies.size();
            }
            if (advantage < 0 && !allies.isEmpty()) {
                double wounded = allies.stream().mapToDouble(context::woundedFraction).sum();
                return 16.0 * (-advantage) * wounded;
            }
            return Double.NEGATIVE_INFINITY; // an even fight does nothing at all
        });

        // --- Straightforward damage/utility, hinted mainly to price them properly ---

        register("fireblast", context -> {
            Unit target = context.targetUnit();
            if (!context.isEnemy(target)) {
                return Double.NEGATIVE_INFINITY;
            }
            return (24 + 15) * context.targetPriority(target); // damage plus 3 Burn stacks
        });

        // Ground that burns for 7 turns; worth it only where enemies actually are.
        register("eruption", context -> {
            List<Unit> enemies = context.unitsNearAim(0, true);
            if (enemies.isEmpty()) {
                // Still has value as area denial, but far less than a direct hit.
                return context.config().genericAbilityValue() * 0.3;
            }
            double total = 0;
            for (Unit enemy : enemies) {
                total += 30 * context.targetPriority(enemy);
            }
            return total;
        });

        // A 50 HP barrier that bursts for 50 damage if broken. Best on someone about to
        // be hit, which in practice means someone already wounded and in reach.
        register("holy_shield", context -> {
            Unit ally = context.targetUnit();
            if (ally == null || context.isEnemy(ally)) {
                return Double.NEGATIVE_INFINITY;
            }
            double wounded = context.woundedFraction(ally);
            double contested = context.unitsNearAim(1, true).size();
            return (30 + 40 * wounded + 15 * contested) * (context.isChampion(ally) ? 1.8 : 1.0);
        });

        // Removes a unit from play entirely for a few turns.
        register("oblivion_confinement", context -> {
            Unit target = context.targetUnit();
            if (!context.isEnemy(target)) {
                return Double.NEGATIVE_INFINITY;
            }
            return context.evaluator().threat(target) * 1.5 * context.targetPriority(target);
        });

        // +3 attack range on the caster - only worth a move point if it actually opens
        // up targets the unit could not already reach.
        register("steady_focus", context -> {
            Unit user = context.user();
            int currentRange = (int) user.getEffective(Stat.ATTACK_RANGE);
            int wouldReach = 0;
            for (Unit enemy : context.state().getAllActiveUnits()) {
                if (!context.isEnemy(enemy) || enemy.isDead() || enemy.getPosition() == null) {
                    continue;
                }
                int distance = context.state().getMap().getDistance(user.getPosition(), enemy.getPosition());
                if (distance > currentRange && distance <= currentRange + 3) {
                    wouldReach++;
                }
            }
            return wouldReach == 0 ? Double.NEGATIVE_INFINITY : 20.0 * wouldReach;
        });
    }

    private AbilityHints() {
    }

    private static double scoreSanityEclipse(HintContext context) {
        List<Unit> caught = context.unitsNearAim(1, true);
        if (caught.isEmpty()) {
            return Double.NEGATIVE_INFINITY; // it detonates a turn later; an empty tile wastes it
        }
        double total = 0;
        double ourIntelligence = context.user().getEffective(Stat.INTELLIGENCE);
        for (Unit enemy : caught) {
            double lead = ourIntelligence - enemy.getEffective(Stat.INTELLIGENCE);
            if (lead > 0) {
                total += lead * context.targetPriority(enemy);
            }
        }
        return total;
    }

    private static void register(String abilityId, AbilityHint hint) {
        HINTS.put(abilityId, hint);
    }

    /** The hint for this ability name, or the generic fallback. Never null. */
    public static AbilityHint forAbility(String abilityName) {
        return HINTS.getOrDefault(Identifiers.normalize(abilityName), GENERIC);
    }

    /** Exposed for tests asserting that every registered id matches a real ability. */
    public static java.util.Set<String> registeredIds() {
        return java.util.Set.copyOf(HINTS.keySet());
    }
}
