Longshot: Increases attack range by 2.

Steady Focus: Becomes toggleable. When used, the steady focus buff is permenant. If the ability is used again, the buff is removed. The ability goes on cooldown when used, so eg, when the ability is used, it cant be toggled off for 2 turns (cd 2 turns), as the ability is on cooldown.

Static Link: Increases cast range to 2, and link breaks if target is more than 2 tiles away instead of 1. The free attack still procs even if the linked target is outside of normal attack range

Cold Embrace: When used on allies, the buff no longer fully disables them. Allies can still move, but cant attack or use abilities (can probably implement this as a silence + disarm instead of a stun). Enemies are still fully disabled.

Frostbite: Increases debuff duration to 5 turns. Insta kill threshold for basic units increased to 40%.

Overgrowth: When a branchling is killed, a branchigga (stronger branchling) is spawned where it dies. Branchiggas last for 6 turns (from when it spawned, not from when overgrowth was cast), and can move and attack like a normal unit. Branchiggas also inherit the branchling aura. Branchiggas dont spawn when branchlings expire normally, and dont apply to Sprout branchlings.

Sprout: Cooldown reduce to 1 turn.

Backtrack: Performs an attack (weighted encounter), at all adjacent enemies after dashing. Increases range to 4.

Dilation: Becomes a passive ability. The effect is always active.

Timeless Strike: The first repeated attack is rolled again if it originally misses.

Decay: A secondary decay aura steals additonal 5 health within 2 tile radius, which only affects enemies. Units can be affected by both, ie units adjacent are effect by both so lose 5+5=10 health, and 2 strength.

Soul Rip: When casting on enemies, steals an extra 2 strength just before dealing damage. When casting on allies, gives 2 strength just after healing.

Eruption: Becomes a multi target ability, targetting 2 tiles at one. slight rework for normal version (also affects upgraded): casting on an already ignited tile just refreshes the duration rather than preventing a cast altogther.

Fireblast: Original ability nerf to 2 cast range, 2 burn stacks, cooldown 3 turns. Upgraded ability increases cast range to 3, cooldown reduced to 2.

Overheat: Original ability nerf so that proccing removes excess count. Upgraded ability makes it such that the count is preserved after proccing.

Backstab: Damage increased to 0.8 agi to damage. Failed attacks now damage for half the backstab damage. Also applies to cloak and dagger, and is multiplicative (since cloak and dagger doubles backstab, but a failed attack halves, it just does 1x damage).

Cloak and Dagger: A successful attack during cloak and dagger roots and silences the target for 1 turn.

Cripple: Always steals the full amount (ie the amount on a successful attack) even if the attack fail.

Feast: Lifesteal and root on attack modifiers are always active. Casting this ability only triggers the auto attack component.

Oblivion Confinement: Original ability nerf/rework: Doesnt steal additional int after the ability ends anymore, but int stolen increased to 25%. Upgraded ability: steals int after ability ends.

Sanity's Eclipse: Recasts the spell after it lands at the same location (also with delay).

Objurgation: Original ability reworked: grants a barrier that lasts 4 turns when going below 50% HP. Barrier consumes 20% int. Procs whenever damage is taken while under HP threshold (and not on cd). Barrier is given just before damage is taken. Upgraded ability consumes ALL intelligence on fatal damage, and HEALS instead (similar to current implementation). The basic procs still grant barriers.

Eye of the storm: Increase total lightning strikes to 3, and increases the vulnerability debuff to 4. The debuff increase is not retroactive.

Mimic: Copied abilities no longer expire (can still get replaced when copying other abilities). Copied abilities become their upgraded versions.

Perplexing Shot: Bounce count increased to 10.

Superior Mastery: Passively grants a free ability cast each turn (ie the first ability use for this unit does not use an action).

Psychic Projection: Basic ability rework: projection no longer counts towards actions (ie acts like a baisc). Upgrade: Psychic projection lasts indefinitely. Recasting while another projection exists just teleports (or despawns and resummons, depending on which implementation makes sense). Caster is no longer stunned.

Refraction: Gains a second charge of refraction, that has 50% efficiency. eg in a turn, takes 100 damages -> gets 100 damage refracted -> takes 100 damage again -> 50 gets refracted while 50 actually gets hit.

Doom: Adjacent enemy units take 50% of dooms damage.

Infernal Blade: Successfully attacking a unit affected by infernal blade does 30 extra damage, and stuns for 1 turn. Stun duration does not stack and add on like the original debuff.

Eureka: Using an ability grants 1 Inspiration charge (can trigger multiple times per turn). (real abilities only, not move or attack).

Capacitor Bank: Gains 2 charges per turn, max charges to 6.

Energy Shield: Gains a passive shield. The shield has a barrier that regenerates 20 barrier at the start of each turn, with a maximum barrier of 80. This barrier is separate from Energy Shield barrier.

Gyroscope: Increases cast range and attack range bonuses to 2.

Homing Missile: Shoots 2 missiles at once. The original missile (from the ability) takes 2 turns to arrive. The additional missile deals 10 aoe damage (no bonus damage for original target), but stuns all units caught in the aoe for 1 turn. Stunning missile takes 1 turn to arrive.

Killer Drone: Drones last indefinitely.

Nanobots: After the buff ends, the nanobots stay in the target's body. They stay until the target recieves a dispellable debuff, in which they will remove that debuff then expire. No healing is provided past the original duration.

Plasma Cannon: Increases damage to 120.

Reload: Using abilities no longer prevents reload. Attacking, moving (forced movement eg translocation doesn't count), and taking damage still cancels.

Shrink Ray: Increases stat loss to 40%, hp loss to 20%.

Translocation: Global cast range (still has the limited move range). Can target tiles with another unit, in which case will swap the two units around.

Dispersion: Becomes an active ability. Still has the passive component. When activated, 150% of damage recieved is dealt to surrounding enemies for 3 turns. This unit still takes the original 25% damage reduction, only outgoing reflected damage is increased. 6 turn cooldown.

Manifestation: After teleporting, a shadow is left behind, which cannot move and lasts 2 turns. The shadow gains the recall ability, which when used, teleports the unit back into the original location (and despawns the shadow). Implementation wise, it just teleports the caster to the shadow, so technically any force move abilities can change where the recall is by moving the shadow. Shadow spawns with same stats and same hp (current and max) as the caster. see mercurial_shadow.json

Acidic Brew: Increases cast range to 3, and becomes an AOE with radius 1. 

Hidden Potential: Gains +20 all stats, and +100 Max/Current HP for every ability upgraded. Applies retroactively, and for future upgrades. This ability can be upgraded multiple times, for no additional effect other than increasing the count of abilities upgrade.

Poison Bloom: When a unit dies with poison bloom, the poison bloom debuff will also spread alongside the poison. Spreaded poison blooms do not apply the initial stacks of poison, but do spread poison again when it itself expires.

Poison Sting: Each duration stack of poison increases incoming damage dealt by 2 (ie vulnerability).

Holy Shield: Barrier HP increased to 100. Regenerates 20 barrier at the start of each turn as long as the barrier isn't destroyed (cant regerate past maximum barrier). Barrier also explodes and dispels regardless if it was destroyed or expired.

Selfless: Damage redirected to 40%. This unit only takes 50% of the reflected damage.

Counterstrike: Damage is instead increased by 20%. (ie 80% attack damage -> 120% attack damage).

Duel: Winning a duel refreshes duel cooldown. Duelled targets take 100% more damage from each other.

Overwhemling Odds: Gains a passive component. Globally, if enemies outnumber allies, then each attack heals self for 5 HP per unit (doesn't do more damage, just a heal). Otherwise if allies outnumber enemies, each attack does 10 more damage per unit. Only applies to self. The effects proc on counterstrike, and are not affected by counterstrike modifiers (ie not affected by 20% damage reduction).

Energy Break: Each successful attack deals 2 extra damage per cooldown the target has, and heals self for 1 HP per cooldown.

Implosion: Performs 2 free attacks just before dealing damage.

Blizzard: Now also disarms. Snow golem blizzards get the same buff.

Snow Golem: Original ability rework: snow golem is a basic unit, with free movement and attacks. Upgraded ability: Becomes multi target, targetting 2 tiles. Now summons 2 golems with slightly reduced stats. Resummoning while an original golem is alive despawns it. Resummoning while 2 upgraded golems are alive despawns both. When this ability is first upgraded, refresh its cooldown.

Dislocation: Original ability: cooldown increased to 2. Upgraded ability: Pylons are no longer destroyed when using dislocation, instead it takes 50 damage. Pylon still procs its death effect on dislocation even if it doesnt die.

Orbital Beam: Original ability: damage buffed to 60. Upgraded ability: Damage increased to 100. For every pylon that exists, an additional beam is randomly casted on a random enemy unit globally. Units can be targetted by beams multiple times.

Pylon: Original ability: cooldown reduced to 3. Upgraded: cooldown reduced to 1