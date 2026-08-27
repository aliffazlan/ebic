# Changelog

All notable changes to EBIC are documented here. This project follows the spirit of
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

You can now play on your own against the computer. The roster grows to **5 champions and
14 elites**, basic attacks gain real range, and five existing units get reworked or
retuned.

### Play against the computer

- **New "Play vs the computer" option in the lobby.** No join code, nobody to wait for —
  pick a difficulty and you are straight into the draft.
- The bot drafts, lays out its army, and plays full turns: it advances, focuses wounded
  and high-value targets, uses its abilities, and goes for your champion.
- It never wastes its **free basic attacks**. Attacks by basic units cost no move points,
  and the bot always takes every one worth taking before spending a point on anything
  else — the single most common thing human players leave on the table.
- **It plays the attribute stand-off properly.** Rather than always leading with its
  biggest stat, it plays the mathematically unexploitable mix for each encounter, as
  attacker and as defender. You cannot find a pattern to read, because there isn't one —
  and a bot with huge Strength will happily attack with Intelligence if that is the right
  answer to how you are likely to defend.
- Its army starts sensibly arranged: champion at the back, durable units at the front,
  long-range units behind the line.
- One difficulty (**Standard**) for now. The lobby has a difficulty selector ready for
  more.

### New mechanic — attack range

- Basic attacks are no longer locked to adjacent tiles. Every unit now has an **attack
  range**, shown alongside its attributes on the unit panel and on draft cards.
- Most units stay at range 1. **Spitter, Zenith, Yuki, Auroth and Harbinger** attack at
  range 2; **Branch** at 2 and **Artemis** at 4.
- Attack range is a real stat, so abilities can raise or lower it for a few turns.
- Legal-target highlighting understands range automatically — a ranged unit lights up
  everything it can actually reach.

### New units

**Ember** — a spell-focused champion who burns everything around her.

- *Burn* — a stacking damage-over-time. Each stack deals damage at the start of the
  victim's turn, and new stacks refresh the timer instead of extending it.
- *Overheat* (passive) — tracks the damage Ember deals to each enemy. Once one has
  soaked up enough, it overheats and sets **every other adjacent enemy** alight. Fires
  at most once per turn, and any damage past the threshold carries over toward the next
  one, so a single huge hit banks fuel rather than cashing in all at once.
- *Fireblast* — a direct fireball: damage plus three stacks of Burn.
- *Eruption* — sets a tile alight for several turns. Enemies standing there when it
  lands catch fire immediately, and any enemy that ends its turn in the flames gains
  another stack. Ember's own troops walk through it unharmed.

**Artemis** — a long-ranged elite glass cannon.

- *Longshot* (passive) — attacks hit harder the further away the target is.
- *Steady Focus* — takes aim for two turns: +3 attack range, but rooted in place and
  unable to shoot anything closer than three tiles.

**Branch** — a utility elite who grows cover and traps.

- *Overgrowth* — rings a targeted tile with Branchlings, which block movement, damage
  adjacent enemies and heal adjacent allies every turn. Occupied tiles are skipped, and
  overlapping Branchlings stack — a fully surrounded ally is healed six times over.
- *Sprout* — plants a single Branchling anywhere on the map and teleports to it at the
  start of Branch's next turn, shielding itself and nearby allies on arrival. Killing the
  Branchling first cancels the teleport, though the cooldown is already spent.
- Branchlings cannot move, attack or act at all.

### Unit reworks

**Grivath** — completely reworked from a damage-trading bruiser into a stat thief.

- *Cripple* no longer reduces Grivath's own damage. A landed attack now steals 1 point
  of every attribute plus 3 extra of whichever attribute the target defended with, along
  with 5 maximum **and** current health — all doubled against champions and elites. An
  attack that is successfully defended still shaves a point off the defending attribute
  but steals no health.
- *Feast* is no longer a leap and no longer roots Grivath. It is now a self-cast buff
  granting one free automatic attack per turn against an adjacent enemy. Victims are
  still rooted; lifesteal reduced 50% → **25%**.

**Spitter**

- *Poison Bloom* is now a **separate effect from Poison**. Casting it applies both: the
  poison that does the damage, and a bloom that feeds it. While the bloom lasts the poison
  grows instead of fading; when the bloom ends — or the host dies — it bursts and spreads
  that poison to nearby enemies. Cleansing the bloom denies the burst. The bloom lasts
  **3 turns**, so the burst is now something you actually see in a match, and initial
  stacks are nerfed 10 → **4**.
- *Poison Sting* works on a bloomed target again, and stacks with the bloom: attacking one
  is worth 3 stacks (2 from the sting, 1 from the bloom).
- *Poison Sting* nerfed: duration 3 → **2** turns, damage 4 → **5** per remaining turn.

**Mercurial**

- *Manifestation* can now only land on a tile **bordering an enemy**. The range is still
  the whole map, but it's a strike rather than a free escape.

**Valor** — buffed across the kit.

- *Duel*: cooldown 10 → **6**, duration 4 → **3**, stat bonus 10 → **20**, win
  multiplier 3 → **2.5**.
- *Counterstrike*: counters now deal **80%** damage (was 50%), lifesteal 100% → **25%**.
- *Overwhelming Odds*: radius 2 → **3**, damage and healing 20 → **16**.

**Chronos**

- *Timeless Strike*: chained attacks now deal **50%** damage. A 50-damage opener that
  chains twice deals 50 + 25 + 25 rather than 50 + 50 + 50.

**Harbinger**

- *Objurgation* nerfed: consumes only **50%** of intelligence instead of all of it,
  converted at a 1:1 ratio into health. Cooldown 3 → **4** turns.

### Rules

- **Basic units now act entirely for free.** Their moves cost no action points, just like
  their attacks already did, so a swarm of basics no longer competes with your champion and
  elites for the three points a turn. They can still each move once and attack once.

### Map

- **The battlefield is smaller and wider.** It was a regular 217-tile hexagon, so armies
  spent the opening turns walking. It is now an elongated hexagon — one radius step
  smaller with two rows trimmed off the top and bottom — for **135 tiles**: still wide
  enough to flank in, shallow enough that the two sides meet quickly.

### Interface

- **Selecting a spell now outlines its cast range** on the board, so you can see how far
  something reaches even when nothing is currently standing in that area. Legal targets
  are still highlighted on top. Basic attacks show their range too, which matters now
  that it varies by unit. Abilities that reach the whole map draw no band.
- **Clicks now resolve to what you meant.** Casting a tile-targeted spell like Eruption
  on an occupied tile used to require clicking a stray pixel of hex not covered by the
  unit standing there; clicking the unit cancelled the cast. A tile spell clicked on a
  unit now targets that unit's tile, and a unit spell clicked on the ground targets
  whoever is standing there.
- **Self-cast abilities fire immediately** when selected, instead of asking you to
  confirm with a separate "no target" button.

### Fixed

- **Timeless Strike's stun stacked wrong.** Two procs created two separate one-turn
  stuns that expired independently instead of one two-turn stun, so targets woke up
  early. The stun is also now a proper debuff, so it can be cleansed.
- **Effects that granted an effect to their own owner crashed the game.** Expiry walked
  a live list while running each effect's finishing hook. Nothing shipped had triggered
  it, but Branch's Sprout would have crashed on its first cast.
- **Two ability descriptions showed "0" where a number belonged.** Cloak and Dagger and
  Decay each referenced a tunable that didn't exist under that name.
- **Percentages displayed as fractions.** Counterstrike advertised "0.5 % less damage"
  instead of "50%". All percentage values now render correctly.
- **Sprout resolved instantly.** The teleport fired as soon as Branch ended the turn it
  was cast on, giving the opponent no window to destroy the Branchling and interrupt it.
- **Branchling auras** now tick at the end of their controller's turn rather than the
  start, so they resolve after that side has finished moving around them.
- **Eruption can no longer be cast on ground that is already burning.** It gave no
  benefit — a second patch on one tile just burns whoever stands there twice a round —
  so it only ever wasted the cooldown.
- **Poison Bloom was indistinguishable from ordinary poison.** It applied an effect named
  plain "Poison", so casting it looked like nothing had happened.
- **Poison Bloom grew far faster than intended.** A single Spitter attack on a blooming
  target extended it three times over instead of once, which is why blooms rarely lived
  long enough to burst.
- **Static Link outlived Discharge.** Killing him didn't cancel the link — it kept draining
  the target and triggering Eye of the Storm for as long as the target stood beside his
  corpse. The same bug applied to Dirge: a dead Dirge's Decay went on permanently stealing
  max health and strength from everything next to his body.

### Changed

- **Every ability description rewritten** — all 48 of them — for consistent phrasing,
  correct spelling, and accurate numbers. Descriptions now surface values that were
  previously invisible, such as Poison Bloom's infection radius.
- Draft rounds may now leave a champion and a couple of elites unoffered, since the pool
  is larger than a single draft consumes.
