package com.walnutt.data;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.walnutt.ability.Ability;
import com.walnutt.ability.impl.AcidicBrew;
import com.walnutt.ability.impl.Backstab;
import com.walnutt.ability.impl.Backtrack;
import com.walnutt.ability.impl.Blizzard;
import com.walnutt.ability.impl.BlizzardFist;
import com.walnutt.ability.impl.Bloodwake;
import com.walnutt.ability.impl.BranchlingAura;
import com.walnutt.ability.impl.CapacitorBank;
import com.walnutt.ability.impl.CloakAndDagger;
import com.walnutt.ability.impl.ColdEmbrace;
import com.walnutt.ability.impl.Counterstrike;
import com.walnutt.ability.impl.Cripple;
import com.walnutt.ability.impl.Decay;
import com.walnutt.ability.impl.Dilation;
import com.walnutt.ability.impl.Dislocation;
import com.walnutt.ability.impl.Dispersion;
import com.walnutt.ability.impl.Doom;
import com.walnutt.ability.impl.Duel;
import com.walnutt.ability.impl.EnergyBreak;
import com.walnutt.ability.impl.EnergyShield;
import com.walnutt.ability.impl.Eruption;
import com.walnutt.ability.impl.Eureka;
import com.walnutt.ability.impl.EyeOfTheStorm;
import com.walnutt.ability.impl.Feast;
import com.walnutt.ability.impl.Fireblast;
import com.walnutt.ability.impl.Frostbite;
import com.walnutt.ability.impl.Gyroscope;
import com.walnutt.ability.impl.HiddenPotential;
import com.walnutt.ability.impl.HolyShield;
import com.walnutt.ability.impl.HomingMissile;
import com.walnutt.ability.impl.Implosion;
import com.walnutt.ability.impl.InfernalBlade;
import com.walnutt.ability.impl.KillerDrone;
import com.walnutt.ability.impl.Longshot;
import com.walnutt.ability.impl.Manifestation;
import com.walnutt.ability.impl.Mimic;
import com.walnutt.ability.impl.Nanobots;
import com.walnutt.ability.impl.Objurgation;
import com.walnutt.ability.impl.OblivionConfinement;
import com.walnutt.ability.impl.OrbitalBeam;
import com.walnutt.ability.impl.Overgrowth;
import com.walnutt.ability.impl.Overheat;
import com.walnutt.ability.impl.OverwhelmingOdds;
import com.walnutt.ability.impl.PerplexingShot;
import com.walnutt.ability.impl.PlasmaCannon;
import com.walnutt.ability.impl.PoisonBloom;
import com.walnutt.ability.impl.PoisonSting;
import com.walnutt.ability.impl.PsychicProjection;
import com.walnutt.ability.impl.PylonAbility;
import com.walnutt.ability.impl.PylonOrbitalBeam;
import com.walnutt.ability.impl.Recall;
import com.walnutt.ability.impl.Refraction;
import com.walnutt.ability.impl.Reload;
import com.walnutt.ability.impl.Sanguine;
import com.walnutt.ability.impl.SanityEclipse;
import com.walnutt.ability.impl.Selfless;
import com.walnutt.ability.impl.ShrinkRay;
import com.walnutt.ability.impl.SnowBlast;
import com.walnutt.ability.impl.SnowGolem;
import com.walnutt.ability.impl.SoulRip;
import com.walnutt.ability.impl.Sprout;
import com.walnutt.ability.impl.StaticLink;
import com.walnutt.ability.impl.SteadyFocus;
import com.walnutt.ability.impl.SuperiorMastery;
import com.walnutt.ability.impl.TimelessStrike;
import com.walnutt.ability.impl.Translocation;

/**
 * Registry mapping an ability's JSON id (a design_ideas/abilities/<unit>/<id>.json
 * basename, e.g. "soul_rip") to a hand-written Ability/PassiveAbility constructor.
 * Unique mechanics stay in code; the numbers they read come from AbilityDefinition.
 *
 * Adding ability #N later is exactly one new entry here plus one new class - no
 * other engine code changes.
 */
public final class AbilityFactory {
    private static final Map<String, Function<AbilityDefinition, Ability>> REGISTRY = Map.ofEntries(
        Map.entry("backstab", Backstab::new),
        Map.entry("poison_sting", PoisonSting::new),
        Map.entry("soul_rip", SoulRip::new),
        Map.entry("objurgation", Objurgation::new),
        Map.entry("counterstrike", Counterstrike::new),
        Map.entry("duel", Duel::new),
        Map.entry("overwhelming_odds", OverwhelmingOdds::new),
        Map.entry("oblivion_confinement", OblivionConfinement::new),
        Map.entry("sanity_eclipse", SanityEclipse::new),
        Map.entry("decay", Decay::new),
        Map.entry("cloak_and_dagger", CloakAndDagger::new),
        Map.entry("poison_bloom", PoisonBloom::new),
        Map.entry("cold_embrace", ColdEmbrace::new),
        Map.entry("frostbite", Frostbite::new),
        Map.entry("psychic_projection", PsychicProjection::new),
        Map.entry("refraction", Refraction::new),
        Map.entry("energy_break", EnergyBreak::new),
        Map.entry("implosion", Implosion::new),
        Map.entry("manifestation", Manifestation::new),
        Map.entry("dispersion", Dispersion::new),
        Map.entry("doom", Doom::new),
        Map.entry("infernal_blade", InfernalBlade::new),
        Map.entry("holy_shield", HolyShield::new),
        Map.entry("selfless", Selfless::new),
        Map.entry("blizzard", Blizzard::new),
        Map.entry("snow_golem", SnowGolem::new),
        Map.entry("blizzard_fist", BlizzardFist::new),
        Map.entry("snow_blast", SnowBlast::new),
        Map.entry("orbital_beam", OrbitalBeam::new),
        Map.entry("pylon", PylonAbility::new),
        Map.entry("dislocation", Dislocation::new),
        Map.entry("pylon_beam", PylonOrbitalBeam::new),
        Map.entry("backtrack", Backtrack::new),
        Map.entry("dilation", Dilation::new),
        Map.entry("timeless_strike", TimelessStrike::new),
        Map.entry("feast", Feast::new),
        Map.entry("cripple", Cripple::new),
        Map.entry("bloodwake", Bloodwake::new),
        Map.entry("sanguine", Sanguine::new),
        Map.entry("static_link", StaticLink::new),
        Map.entry("eye_of_the_storm", EyeOfTheStorm::new),
        Map.entry("overheat", Overheat::new),
        Map.entry("fireblast", Fireblast::new),
        Map.entry("eruption", Eruption::new),
        Map.entry("longshot", Longshot::new),
        Map.entry("steady_focus", SteadyFocus::new),
        Map.entry("overgrowth", Overgrowth::new),
        Map.entry("sprout", Sprout::new),
        Map.entry("branchling_aura", BranchlingAura::new),
        Map.entry("eureka", Eureka::new),
        // Maxwell's nine gadgets. Not listed by any unit's JSON - they reach a unit only
        // through Eureka, whose GADGET_IDS is the list; MaxwellGadgetPoolTest keeps the
        // two in step.
        Map.entry("energy_shield", EnergyShield::new),
        Map.entry("gyroscope", Gyroscope::new),
        Map.entry("homing_missile", HomingMissile::new),
        Map.entry("killer_drone", KillerDrone::new),
        Map.entry("nanobots", Nanobots::new),
        Map.entry("plasma_cannon", PlasmaCannon::new),
        Map.entry("reload", Reload::new),
        Map.entry("shrink_ray", ShrinkRay::new),
        Map.entry("translocation", Translocation::new),
        Map.entry("perplexing_shot", PerplexingShot::new),
        Map.entry("superior_mastery", SuperiorMastery::new),
        Map.entry("mimic", Mimic::new),
        Map.entry("capacitor_bank", CapacitorBank::new),
        Map.entry("hidden_potential", HiddenPotential::new),
        Map.entry("acidic_brew", AcidicBrew::new),
        // Carried only by Mercurial's shadow, which upgraded Manifestation leaves behind.
        Map.entry("recall", Recall::new)
    );

    /**
     * Abilities whose UPGRADED behaviour is actually implemented, and so the only ones
     * Shawl's Hidden Potential offers as a live choice. Everything else with an upgrade
     * block designed in JSON is still listed in his dialogue, but disabled.
     *
     * This exists because most upgrades are not merely a bigger number: Static Link's
     * raises cast_range, which Ability.upgrade applies on its own, but its link would still
     * snap at one tile until StaticLink itself reads link_range. Offering that would sell a
     * player an upgrade that half works, which reads as a bug rather than as a feature not
     * finished yet.
     *
     * Membership is therefore a promise: an id here has been checked to change something
     * observable when upgraded, and AbilityUpgradeTest fails the build if one does not.
     *
     * As of v0.3.0 it covers every upgradeable ability, so the disabled path in
     * HiddenPotential.optionsFor is currently unreachable - deliberately kept rather than
     * deleted, so a hero added later with a designed-but-unbuilt upgrade is withheld from
     * Shawl's dialogue instead of being sold half-working. AbilityUpgradeTest asserts the
     * coverage, so letting one slip is a build failure rather than a silent regression.
     */
    private static final Set<String> UPGRADE_IMPLEMENTED = Set.of(
        // Nothing but numbers the ability already read - the base class re-applies these.
        "sprout",           // cooldown
        "pylon",            // cooldown
        "fireblast",        // cooldown + cast_range
        "plasma_cannon",    // damage
        "perplexing_shot",  // bounces
        "shrink_ray",       // stat_reduction + hp_reduction
        "capacitor_bank",   // charge_per_turn + max_charges
        "gyroscope",        // both range boosts, re-granted to the owner
        // Shawl's own two. Both were written against their upgraded shape from the start -
        // Acidic Brew is radius-aware with a base radius of 0, and Hidden Potential's payout
        // is the one piece of behaviour it adds.
        "acidic_brew",      // cast_range + radius
        "hidden_potential", // repeatable; pays out per unlock granted

        // Numbers the ability already read, or one extra clause bolted onto it.
        "longshot",             // permanent attack-range bonus
        "frostbite",            // longer, plus a higher shatter bar for basics
        "soul_rip",             // strength crosses with the soul
        "decay",                // a second, wider, enemies-only rot
        "oblivion_confinement", // a second helping of intelligence on the way out
        "doom",                 // the tick spills onto neighbours
        "infernal_blade",       // striking a branded unit burns and stuns
        "selfless",             // takes more of the blow, and less of it lands
        "counterstrike",        // the penalty reverses into a bonus
        "energy_break",         // landed attacks feed on the cooldowns they pile up
        "eureka",               // casting anything pays Inspiration back
        "poison_sting",         // the poison softens as well as kills
        "orbital_beam",         // one extra global beam per pylon
        "holy_shield",          // mends itself, and always erupts
        "energy_shield",        // a second, self-repairing barrier

        // Rules hung off the combat hooks.
        "backstab",             // a defended attack keeps a share of the bonus
        "cripple",              // the full toll even from an attack that fails
        "sanguine",             // attacks also bite for a slice of current HP
        "cloak_and_dagger",     // a landed ambush roots and silences
        "timeless_strike",      // the first chain gets another roll
        "duel",                 // mutual vulnerability, and a refresh on a win
        "implosion",            // free attacks before the damage is priced
        "overwhelming_odds",    // a board-wide passive on Valor's own swings
        "refraction",           // a second, weaker refraction each turn
        "overheat",             // banks the excess instead of burning it off
        "blizzard",             // the snow disarms, golems included
        "backtrack",            // arriving strikes everything beside it

        // Rules flipped on or off.
        "killer_drone",         // no power cell to run down
        "reload",               // casting no longer interrupts it
        "nanobots",             // the bots stay dormant for one more cleanse
        "poison_bloom",         // a host that dies spreads the bloom itself
        "cold_embrace",         // an embraced ally keeps its feet
        "eye_of_the_storm",     // several bolts a turn
        "static_link",          // the link stretches
        "sanity_eclipse",       // the orb falls twice
        "objurgation",          // a killing blow burns every point
        "dislocation",          // the pylon survives, hurt

        // Reshapes: the ability is a different thing afterwards.
        "steady_focus",         // a toggle rather than a timer
        "feast",                // the hunger never lifts
        "bloodwake",            // attacks join in free, and a refresh extends instead of resetting
        "translocation",        // reaches anywhere, and swaps
        "psychic_projection",   // indefinite, and no longer stunning
        "mimic",                // copies kept for good, and upgraded
        "superior_mastery",     // one free cast a turn
        "dilation",             // becomes a field he simply carries
        "dispersion",           // gains an active half

        // The last five, each of which needed machinery built for it.
        "eruption",             // two tiles at once
        "snow_golem",           // two golems, on two tiles
        "manifestation",        // leaves a shadow that can call him back
        "overgrowth",           // a slain Branchling grows a Branchigga
        "homing_missile"        // a stunning missile ahead of the warhead
    );

    private AbilityFactory() {
    }

    public static boolean isImplemented(String id) {
        return REGISTRY.containsKey(id);
    }

    /** Whether upgrading {@code id} would actually do everything its JSON advertises. */
    public static boolean isUpgradeImplemented(String id) {
        return UPGRADE_IMPLEMENTED.contains(id);
    }

    /** The ids in {@link #UPGRADE_IMPLEMENTED}, for the build guard over that promise. */
    public static Set<String> implementedUpgradeIds() {
        return UPGRADE_IMPLEMENTED;
    }

    public static Ability create(String id, AbilityDefinition definition) {
        Function<AbilityDefinition, Ability> constructor = REGISTRY.get(id);
        if (constructor == null) {
            throw new IllegalArgumentException(
                "No Ability implementation registered for '" + id + "' yet - add one to AbilityFactory.");
        }
        Ability ability = constructor.apply(definition);
        // The one place a definition id is stamped onto an ability. Anything built
        // directly in Java (Move, Attack, a summon's internal kit) keeps a null id,
        // which is exactly what makes it uncopyable - see Ability.getDefinitionId.
        ability.setDefinitionId(id);
        ability.setDefinition(definition);
        return ability;
    }
}
