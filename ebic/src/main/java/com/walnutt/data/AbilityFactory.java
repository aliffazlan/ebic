package com.walnutt.data;

import java.util.Map;
import java.util.function.Function;

import com.walnutt.ability.Ability;
import com.walnutt.ability.impl.Backstab;
import com.walnutt.ability.impl.Backtrack;
import com.walnutt.ability.impl.Blizzard;
import com.walnutt.ability.impl.BlizzardFist;
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
import com.walnutt.ability.impl.Refraction;
import com.walnutt.ability.impl.Reload;
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
        Map.entry("capacitor_bank", CapacitorBank::new)
    );

    private AbilityFactory() {
    }

    public static boolean isImplemented(String id) {
        return REGISTRY.containsKey(id);
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
        return ability;
    }
}
