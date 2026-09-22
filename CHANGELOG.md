# Changelog

All notable changes to EBIC are documented here. This project follows the spirit of
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

Every version lives in this one file, newest first — the project is small enough that one
scrollable history beats hunting through per-version files. Entries are written for players
rather than for the diff: what changed about playing the game, not which classes moved.

## 0.4.1 — 2026-09-22

More tuning on top of 0.4.0's numbers — and the game finally introduces itself, walking a
new player through their first match.

### Balance

- **Harbinger**: health 1200 → 1080.
- **Sanity's Eclipse**: intelligence-difference damage multiplier 1.5 → 1.8, cooldown 9 → 8.
- **Chronos**: health 1200 → 1140, strength 70 → 58, agility 110 → 104, intelligence 60 → 46.
- **Overwhelming Odds**: the damage and healing dealt per unit of advantage or deficit rises
  to 30 (was 16).
- **Duel**: winning against a basic unit no longer heals the survivor at all — winning
  against anything else still heals, but only 30% of max health (was 50% for every win).
- **Orbital Beam** can no longer be cast on empty ground, only on a unit.
- **Dislocation**: cooldown 2 → 5, and its barrier now lasts 5 turns (was 2) for 100 health
  (was 50).
- **Artemis**: health 440 → 390.
- **Cold Embrace**: duration 3 → 2, damage/heal 30 → 40, cast range 4 → 3.
- **Branch**: Branchlings' health 50 → 100.
- **Grivath**: health 750 → 680, strength 60 → 56, agility 54 → 48, intelligence 48 → 42.
- **Lucifer**: health 800 → 880.
- **Translocation**: every throw is shorter — itself 4 → 3 tiles, an ally 3 → 2, an enemy
  2 → 1.
- **Homing Missile**: cast range 8 → 6.
- **Thaddeus**: health 850 → 920.
- **Holy Shield**: barrier 50 → 80.
- **Implosion**: damage per turn of cooldown rises to 18 (was 12).

### Tutorial

- **A "Tutorial" option now sits first in the lobby.** It walks a brand-new player through
  drafting a champion and three elites, arranging a formation, and fighting a staged battle,
  narrated by Valor throughout. Along the way it covers the shape of a turn (three actions
  for champions and elites, basics move for free) and the Strength/Agility/Intelligence
  attack exchange — what winning and losing an attack actually looks like — closing on a
  scripted victory over Harbinger.

## [0.4.0] — 2026-09-17

A pass across the roster now that a season of 0.3.0 has settled the numbers — ten units
retuned, five abilities rebuilt outright, and an upgrade that finally reaches the fight
already in progress.

### Balance

- **Ember**: health 970 → 960, intelligence 94 → 88.
- **Overheat**: threshold raised 50 → 80. Base and upgrade swap what a proc does — the base
  form now deals 30 damage to every other adjacent enemy instead of spreading Burn, and
  always empties the gauge outright rather than banking anything past the threshold.
  Spreading Burn on a proc is the upgrade's own trick now.
- **Harbinger**: health 1400 → 1200, strength 70 → 66, intelligence 80 → 86.
- **Oblivion Confinement**: intelligence stolen on cast raised to 35%. The upgrade no longer
  takes a second helping when the target escapes — instead Harbinger permanently steals 10%
  of a victim's intelligence (minimum 1) off every blow he lands, from any source, imprisoned
  target or not.
- **Sanity's Eclipse**: intelligence-difference damage multiplier 1 → 1.5.
- **Objurgation**: triggers below 40% health (was 50%), converts intelligence to shielding at
  2.5 health per point (was 1), and its barrier now lasts 3 turns (was 4).
- **Valor**: health 1100 → 1280, strength 60 → 68, agility 50 → 54.
- **Counterstrike**: no longer lifesteals into raw HP. A landed counter now grants a barrier
  worth the same share of the damage dealt instead, lasting 3 turns — proccing again adds
  onto the barrier and refreshes its duration rather than stacking a second one. Its
  description now says the counter deals 80% of attack damage, matching how the rest of the
  kit is worded, rather than "20% less".
- **Duel**: the mutual damage amp between duellists — 200% damage, not something the upgrade
  unlocks — is part of the base ability. Upgraded, it lasts 5 turns (was 3) and the damage
  Valor deals to his rival rises further, to 300% damage — what he takes back in return
  stays at the base 200%.
- **Zenith**: Pylon's own beam now hunts for a target within 2 tiles instead of 1.
- **Auroth**: Frostbite lasts 3 turns (was 2) and its execute threshold rises to 20% health
  (was 10%); upgraded, its duration is now 6 turns (was 5).
- **Dirge**: Decay's strength steal drops to 1 (was 2). Soul Rip's damage multiplier drops to
  40% (was 50%).
- **Grivath**: Feast's upgrade no longer grants a permanent passive. Upgraded, Feast becomes
  unit-targetable at 2 range — cast it on an enemy and Grivath latches onto them: occupying
  their tile, untargetable and invulnerable, and automatically following wherever they go for
  the duration, including through forced relocations (teleports, pulls, swaps). While latched,
  the free attacks always hit the latched unit, twice a turn instead of once — proccing
  immediately on cast as well as on each of Grivath's turns — and Grivath himself is disarmed
  and silenced, unable to attack or cast anything else; moving is the one action left open, and
  choosing to move away from the latched unit ends the latch early. If the latched unit dies —
  or simply stops being a valid target, say by imprisoning itself or cloaking away — Grivath
  pops off and whatever duration remains just acts like the base ability. Cast on himself,
  upgraded Feast still just acts like the base ability too.
- **Shawl**: Hidden Potential now costs 14 Insight (was 10), and Shawl starts every match
  already holding 6.
- **Mercurial**: health 810 → 860, strength 40 → 38, agility 40 → 36. Dispersion's reflect
  share rises to 30% (was 25%).
- **Wei**: health 650 → 710, agility 90 → 84, strength 30 → 36.
- **Yuki**: health 450 → 440. Blizzard's damage rises to 30 (was 25).

### Fixed

- **Unlocking Blizzard mid-match left an already-buried victim un-disarmed.** Upgrading only
  changed what a future storm did, so a unit already rooted by Yuki — or one of her Snow
  Golems — kept its old, non-disarming terms until it was hit again. Upgrading now reaches
  back and disarms every storm she or her golems already have running.
- **Poison Sting had the identical gap, unreported until now.** An already-poisoned target's
  vulnerability bonus now updates the instant Poison Sting is upgraded too, rather than
  waiting for the next sting to reapply it.

## [0.3.0]

An alchemist who does not fight so much as *improve* — and, behind him, the machinery for an
ability to have a second, better form.

### New unit

**Shawl** — a brewer of impossible tonics, who wins by making everyone else better than they were.

- *Hidden Potential* — every blow Shawl lands earns him a point of **Insight**. At 10 Insight,
  pour it into any ally within 4 tiles — Shawl included — and pick one of their abilities to
  **permanently unlock**. There is no cap: one hero can be handed upgrade after upgrade if you
  are willing to spend the match feeding them.
- *Acidic Brew* — a pool of acid on a tile 2 away for 3 turns. Enemies standing in it take 10
  extra damage from **every** source, and 5 more if they end their turn there.
- Hidden Potential can of course be poured into itself, and it is the one ability that accepts
  the treatment twice. Each time, Shawl gains **+20 to every attribute and +100 health for every
  ability he has ever unlocked**, counted backwards over the whole match.

### Upgraded abilities

- **An unlocked ability is gold**, and reads differently for both players — its description,
  its numbers and its rules are all the upgraded ones from the moment Shawl pours the brew.
  There is nothing to hover on an ability you have not unlocked: what it *would* become is
  shown only inside Shawl's own dialogue, when you are choosing.
- **Every one of the 59 designed upgrades works**, and they are not all bigger numbers. Some are:
  Plasma Cannon hits for 120, Perplexing Shot ricochets ten times, Shrink Ray takes twice as
  much. Others change what the ability *is* — **Dilation** stops being a spell and becomes a
  field Chronos simply carries, **Dispersion** gains an active that throws 150% of a blow back,
  **Steady Focus** becomes a toggle Artemis holds as long as she likes, **Feast** makes the
  lifesteal and the root permanent so the cast is nothing but the bite, and **Counterstrike**
  stops countering for 20% *less* damage and starts countering for 20% *more*.
- **A few of the best ones pay off through another ability.** Upgraded Implosion swings twice
  before pricing its damage, so Wei's own Energy Break has already piled the cooldowns up that
  the implosion then charges for. Upgraded Mimic hands Joker copies that never expire *and*
  arrive already unlocked. Upgraded Hidden Potential pays Shawl for every unlock he has ever
  granted, counted backwards.
- **Five of them change what a cast even looks like.** **Eruption** sets *two* tiles alight and
  **Snow Golem** raises a pair on two chosen tiles — both are the first casts in the game to ask
  for two tiles, so the board now walks you through it a click at a time. **Manifestation** leaves
  a shadow of Mercurial standing where he vanished from, carrying his health and a single ability
  — *Recall*, which pulls him back to wherever it is standing. Kill the shadow and there is
  nothing to come back to; shove it with a Translocation and you have moved his escape route.
  **Overgrowth** grows a Branchigga wherever one of its Branchlings is killed — a real unit that
  moves and attacks, on its own six-turn clock, so it outlives the grove that seeded it. And
  **Homing Missile** fires two: a light one that arrives a turn early and stuns everything it
  catches, with the warhead a turn behind.
- Summons' own kits — a Branchling's aura, a Snow Golem's fists, a Pylon's beam — can never be
  unlocked at all.
- **Cancelling costs nothing.** Open the dialogue, look at what an ally has, and back out: the
  Insight and the cooldown are only spent once you actually choose.

### Playing against the computer

- **The computer never drafts Shawl**, joining Maxwell and Joker on the short list of heroes it
  declines — all three win by investing now for a payoff several turns away, which it cannot yet
  plan for.
- **A draft round that would have offered the computer two heroes it declines is now redealt**
  before either player sees it. That was already possible with Maxwell and Joker, and used to
  end with the computer picking one of them anyway.

### Balance

Several abilities were re-tuned in their **base** form so their unlocked form has somewhere to go:

- **Backstab** now adds 0.8 damage per point of agility, up from 0.4.
- **Fireblast** is shorter-ranged (2 tiles), applies 2 stacks of Burn, and comes back in 3 turns.
- **Eruption** can be cast onto ground that is already alight, refreshing how long it burns
  instead of being refused.
- **Overheat** now empties its counter when it procs — damage past the threshold no longer
  carries toward the next one.
- **Oblivion Confinement** steals 25% intelligence on the way in, and no longer steals again on
  the way out.
- **Objurgation** has been rebuilt. Rather than cheating death once, it now throws up a 4-turn
  barrier out of 20% of Harbinger's intelligence whenever he is struck below half health.
- **Psychic Projection** no longer costs an action to cast.
- **Snow Golems** are proper basic units that move and attack on their own.
- **Zenith**: Orbital Beam hits for 60, Pylon comes back in 3 turns, Dislocation in 2.
- **A defended Backstab no longer lands the backstab bonus at all.** It used to carry the whole
  thing, which quietly made a failed swing one of Evayne's better outcomes.
- **Overheat now empties its gauge when it procs.** Banking whatever was past the threshold is
  the upgrade.
- **Oblivion Confinement takes its whole toll on the way in.** Taking a second helping when the
  target returns is the upgrade, not the baseline.
- **Eruption can be cast onto burning ground, and refreshes it.** It used to be refused outright.
  Re-lighting tops the fire back up rather than laying a second one, so a tile never burns its
  occupant twice a round.

### Fixed

- **Dirge's Decay was wrong at both ends of the transfer.** Its victims were charged current
  health twice — the maximum dropped, dragging current health down with it, and then the rot's
  damage landed on top, so a 5-point steal cost a healthy unit 10. Dirge, meanwhile, gained the
  maximum health without any of the blood to fill it, so a long grind left him with a large empty
  pool. A drain now costs its victim exactly what it steals, from both, and hands exactly that
  much to Dirge — the same shape Grivath's Cripple has always used.

## [0.2.1] — 2026-08-29

A place to read the roster outside a match, a hero you can promise yourself in every draft,
shorter ability text with the fine print one key away, and two delayed spells that finally
show you where they are about to land.

### Unit info

- **A new "Unit info" page**, reachable from the main menu. Every draftable hero — 6
  champions and 15 elites — with their statline and their full ability list, the same
  cards you see at draft time, but with nothing to pick and no clock running. Filter by
  champion or elite, or search by name.

### Favourite units

- **Pick a favourite hero in the main menu and you are guaranteed to be offered them.** A
  favourite champion always turns up as one of your two options in the champion round; a
  favourite elite turns up in one of your three elite rounds, chosen at random each match
  so its position never gives it away. You still have to actually take them — it is one of
  two options, not a free pick.
- Your favourite leaves the shared pool the moment it is reserved, so your opponent can
  never be dealt the same hero.
- **If both players favourite the same hero, neither of you gets them.** They are withheld
  from the match entirely rather than handed to whoever happened to be dealt first.
- "None" is always available, and the computer never has a favourite.

### Ability text

- **Ability descriptions are now a short overview** — one or two sentences on what the
  ability actually does. The conditions, interactions and edge cases that used to be
  crammed into the same paragraph have moved out of the way.
- **Hold Left Alt while hovering an ability** to expand the tooltip: every one of those
  finer points as its own line, plus a table of the ability's actual numbers. Works on
  in-match ability buttons, on draft cards, and on the new unit info page. A tooltip that
  has more to show says so, so there is nothing to discover by accident.

### Interface

- **A pending Sanity's Eclipse is drawn on the board.** The whole blast radius is shaded
  pale blue for the turn the orb is in the air, so the delay it advertises is a turn you
  can actually use to walk clear.
- **A homing missile marks its target.** An orange reticle sits on whichever tile the
  locked unit is standing on and moves with them, which is the honest way to show an
  ability whose whole point is that running does not help.
- **Damage dealt at the end of a turn now appears immediately.** Poison ticks, burning
  ground and drone strikes used to sit invisible until the other player took their first
  action, a turn later.

### Fixed

- **Killer Drones would not attack.** Three things at once: a drone was dismantled a moment
  before its final strike could fire, so it only ever got one turn less than advertised;
  and with 20 in every attribute its strike lost or tied nearly every matchup, dealing 0.
  Drones now have a real attacking statline and get every turn they are paid for.
- **Killer Drones could not be deployed onto, or moved through, an occupied tile.** A drone
  takes up no space and never blocks anyone — nothing standing in the way should have
  blocked it either. It now flies over units and deploys wherever you point it.
- **The draft card's unit type was rendered in lowercase**, against the contract everything
  else on both sides follows.

## [0.2.0] — 2026-08-28

Two units who play with the shape of the game itself: an elite who **builds his own kit as
the match goes on**, and a champion who **steals yours**. The roster grows to **6 champions
and 15 elites**.

### New units

**Joker** — a jester and a thief, whose best spells are other people's.

- *Perplexing Shot* — an erratic bolt for 30 damage at range 2, which then ricochets up
  to twice more to a random unit standing beside its last victim, hitting 20 harder each
  jump: 30, then 50, then 70. It never hits the same unit twice and fizzles out if there
  is nothing to jump to — but it does not care whose side anyone is on. Fired into a
  cluster of enemies with nothing of yours nearby it is the hardest single spell in the
  game; fired into a scrum it will happily finish on your own champion.
- *Superior Mastery* (passive) — **+2 cast range on everything he holds**, and every
  ability he casts takes a turn off the cooldown of all his others, copied abilities
  included. The price is that **no ability of his may be cast twice in one turn**, however
  quickly it comes back up — a button whose cooldown reads 0 will still refuse, with the
  reason on hover.
- *Mimic* — he watches every ability an enemy casts within 3 tiles (5, with Superior
  Mastery) and remembers it for 3 turns. Cast Mimic on that enemy to **copy the last
  ability they used** and wield it yourself for 10 turns. A first theft arrives ready to
  use immediately. Only one copy at a time: taking a new one hands the old back — but he
  never really forgets it, and a discarded copy **keeps cooling down in the background**,
  so stealing it again later returns it exactly as he left it.

**Maxwell** — a tinker who starts with almost nothing and constructs whatever the game
turns out to need.

- *Eureka* — his only starting ability. He gains **2 Inspiration at the end of each of his
  turns**, plus **1 more if he did something that turn**. Spend it to permanently build a
  gadget, chosen from a dialogue listing everything he hasn't built yet. The first costs
  **6**, and every one after that costs **10 more** — so a fourth gadget is a real
  investment, not a formality. Eureka is greyed out until the next one is affordable, with
  the running total shown on his effects panel.
- Ten gadgets to build, in any order you like:
  - *Plasma Cannon* — 60 damage at range 2, and disarms for 2 turns.
  - *Energy Shield* — an 80-damage barrier on himself or an ally, for 5 turns.
    Recasting on someone who already has one tops it back up rather than stacking a
    second shield.
  - *Shrink Ray* — cuts 20% off an enemy's attributes and 10% off both its current and
    maximum health for 4 turns. Cast it again and it shrinks them further from their new
    size. Whatever else happens in the meantime, it hands back exactly what it took.
  - *Killer Drone* — deploys a drone for 8 turns. Drones can be moved freely and never
    block movement, but have no attack you can order: each one strikes a random adjacent
    enemy at the end of every turn, including one standing on its own tile.
  - *Homing Missile* — locks onto an enemy up to 8 tiles away and lands a turn later for
    50 damage, plus 20 to everything beside it. It follows the target, so running is no
    escape — but cleansing the lock shoots the missile down.
  - *Translocation* — picks a unit up and sets it down elsewhere. He can throw himself 4
    tiles, an ally 3, and an enemy 2.
  - *Nanobots* — cleanses and heals an adjacent ally for 40, immediately and again at
    the start of each of their turns. The short reach is the price of how strong it is:
    Maxwell has to walk up, or the wounded unit has to fall back to him.
  - *Gyroscope* (passive) — +1 attack range and +1 cast range on **everything**, including
    gadgets built after it.
  - *Reload* (passive) — if he neither acts nor takes damage for a full round, every one of
    his cooldowns comes back. Damage a barrier absorbs entirely doesn't interrupt it.
  - *Capacitor Bank* (passive) — banks a charge at the start of each of his turns, up to
    3, and each charge pays for an ability instead of an action. That is a free cast
    every turn, and a full bank is three abilities in one turn with all three move
    points still in hand. It is the answer to how little he can do early: let the game
    run long and Maxwell gets turns nobody else can have. Charges never pay for his move
    or his attack.

### Rules — untouchable units

- **Invulnerable now means untargetable.** Previously you could attack a vanished Evayne,
  a frozen unit or an imprisoned one and simply deal 0 — the swing was wasted but legal.
  Those units are now no longer valid targets at all: nothing can be aimed at them, by
  either side, so they aren't highlighted and a click does nothing. This covers Cloak and
  Dagger, Lanaya's psychic clone, Auroth's Cold Embrace and Harbinger's Oblivion
  Confinement, and it cuts both ways — you can't heal or shield an ally you have sealed
  off either.
- **Area abilities still reach them**, and are still absorbed to nothing, exactly as
  before. Sanity's Eclipse still punches through, as its own text promises.
- **Evayne can no longer act while cloaked.** She was invisible, untouchable and still
  free to move, attack and cast from total safety. She is now genuinely out of the game
  for the duration — the ambush on her tile is the only thing she does.

### Rules — copying

- **Some abilities can never be copied.** A handful break outright if they leave the unit
  they were built for, so they are marked uncopyable in the design files and Joker simply
  cannot take them: **Psychic Projection**, **Eureka**, and Mimic itself. Enemies holding
  those are not valid Mimic targets at all, rather than being targets that fail on click.
- Movement and basic attacks are never copyable either — Mimic steals spells, not walking.
- **Wei still only burns what you are actually holding.** Energy Break and Implosion price
  a unit by its cooldowns, and a copy Joker has set aside is deliberately out of their
  reach. Superior Mastery is the one thing that reaches a set-aside copy.

### Rules — encounters

- **Basic units now fight on instinct.** An attack with a basic unit on *either* side is
  resolved automatically, weighted by each unit's own attributes, with **no attribute
  prompt for either player**. Ten of your fourteen units are basics, so most of the combat
  in a match now happens without a modal — a measured bot match produced 167 attacks and
  only 22 prompts, all of them between champions and elites. Encounters between two
  non-basic units are unchanged.
- **You can't fight with an attribute you don't have.** An attribute at 0 is no longer a
  legal choice; those buttons appear greyed out with an explanation. A unit whose
  attributes have all been stripped — a Branchling, or a victim of enough Cripple, Decay
  or Shrink Ray — **cannot defend itself at all**: the attacker still picks, gets no
  opposing choice to beat, and lands full damage. Such a unit also **cannot attack**.

### Interface

- **A new construction dialogue.** Casting Eureka opens a card for every gadget still
  available, with its full description and cooldown, and builds whichever you click.
- **Two-click targeting for abilities that need two targets.** Translocation highlights the
  units it can move; pick one and the board switches to showing only where *that* unit can
  go. Clicking it again backs out.
- **Abilities locked for the turn say so.** Under Superior Mastery a spent ability's button
  greys out with "Already used this turn", so a cooldown reading 0 never looks like a bug.
  Joker's effects panel also lists what he has spent this turn, and what he is currently
  wielding a copy of.

### Fixed

- **Gyroscope's bonus range appeared to apply to movement.** The board drew a two-tile
  move range while the game still only allowed one, because Move was picking up the
  cast-range bonus it should never have had. Movement and basic attacks now ignore cast
  range entirely — attack range is still raised by Gyroscope, through its own stat.
- **Chronos could send an opponent's cooldowns to absurd numbers.** Timeless Strike chains
  up to nine attacks from a single click, and Wei's Energy Break was charging for every one
  of them. Both Energy Break and Valor's Counterstrike now answer a chain **once**, the way
  they answer any other attack.
- **Lanaya's Psychic Projection was misspelled** ("Pyschic") everywhere it appeared.
- **Sanity's Eclipse went off before the opponent could react.** Harbinger's orb detonated
  the instant he ended the turn he cast it on, so the delay it advertised gave nobody a
  chance to walk clear. It now lands at the start of his *next* turn, leaving the
  opponent a full turn to move — which is what the delay was always for.
- **The fallen no longer clutter the battlefield.** Dead units used to sit on the tile
  they died on, faintly drawn and still clickable. They now move to a column at the edge
  of the board and stack up as they fall, like captured chess pieces — still clickable to
  inspect, but out of the way of the tile underneath them.

### Notes

- The computer opponent **won't draft Maxwell or Joker** for now. It plays both legally,
  but choosing a gadget to build, or an ability worth stealing for later, is a long-term
  investment decision it can't yet reason about, so it takes the other option in those
  draft rounds.
- **Shawl** is designed but not implemented, and is not in the draft pool.

## [0.1.0] — 2026-08-28

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
