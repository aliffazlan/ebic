# Asset manifest

Everything the client can use art/audio for, beyond unit portraits (those have their own folder
and doc: **`web/public/icons/README.md`** — champion/elite portraits, already wired up and used
both on the hex board and, at a larger size, in the encounter modal). This folder is for
everything else. Nothing here is wired into code yet except where noted — drop a correctly-named
file in and it'll just work, same fallback philosophy as `icons/`: missing art degrades to a
placeholder, it never breaks anything.

Priority order if you're wondering where to start: **unit portraits first** (see `icons/README.md`
— biggest visual impact, only 16 files), then attribute icons (3 files, used everywhere), then
whatever else you feel like.

## `abilities/` — ability icons (39 files, not yet wired up)

One icon per ability, shown on the ability buttons in the match sidebar (currently plain text
buttons — see `web/src/ui/Hud.ts`'s `renderAbilityButton`). Naming: `<abilityId>.png`, where
`abilityId` is the lowercase-underscore id matching the ability's own JSON filename under
`design_ideas/abilities/<unit>/*.json` (minus `.json` — same normalization the engine already
uses, see `Identifiers.normalize` server-side). Full list, grouped by unit:

| Unit | Ability ids |
|---|---|
| Valor | `counterstrike`, `duel`, `overwhelming_odds` |
| Harbinger | `objurgation`, `oblivion_confinement`, `sanity_eclipse` |
| Chronos | `backtrack`, `dilation`, `timeless_strike` |
| Zenith | `orbital_beam`, `pylon`, `dislocation`, `pylon_beam` |
| Dirge | `soul_rip`, `decay` |
| Evayne | `backstab`, `cloak_and_dagger` |
| Spitter | `poison_sting`, `poison_bloom` |
| Auroth | `cold_embrace`, `frostbite` |
| Lanaya | `psychic_projection`, `refraction` |
| Wei | `energy_break`, `implosion` |
| Mercurial | `dispersion`, `manifestation` |
| Thaddeus | `holy_shield`, `selfless` |
| Yuki | `blizzard`, `snow_golem` |
| Yuki's Golem | `blizzard_fist`, `snow_blast` |
| Grivath | `feast`, `cripple` |
| Discharge | `static_link`, `eye_of_the_storm` |
| Lucifer | `doom`, `infernal_blade` |

Spec: PNG, square, suggest 128×128 (these render small, on a button), transparent background,
simple/high-contrast enough to read at ~24px on screen.

## `attributes/` — Strength / Agility / Intelligence icons (3 files, not yet wired up)

Small icons for the rock-paper-scissors attributes, shown next to the STR/AGI/INT numbers in the
unit sidebar panel and (once built) the encounter modal — currently just the text labels `STR`/
`AGI`/`INT`. Naming: `strength.png`, `agility.png`, `intelligence.png`.

Spec: PNG, square, suggest 32×32–64×64, transparent background. A common convention if you want
one: a sword for Strength, a boot/wind swirl for Agility, a brain/eye for Intelligence — but any
consistent, distinct set works, these are purely decorative next to the existing numbers.

## `status/` — status effect icons (13 files, not yet wired up)

One per `StatusFlag` (see `ebic/src/main/java/com/walnutt/status/StatusFlag.java`), shown on a
unit's status chips (currently plain text chips, e.g. "STUNNED" — see the `.status-chip` elements
in `web/src/ui/Hud.ts`/`web/src/board/Board.ts`). Naming: lowercase the enum value exactly,
`<flag>.png`:

`stunned`, `silenced`, `disarmed`, `rooted`, `invulnerable`, `hidden`, `frozen`,
`immune_to_healing`, `untargetable`, `taunted`, `cooldowns_paused`, `dueling`, `time_dilated`

Spec: PNG, square, suggest 32×32, transparent background, simple enough to read at chip size
(~16-20px).

## `effects/` — board-level status-effect art (1 file, wired up)

`banner.webp` is Valor's Duel banner, planted beside each duelist by
`web/src/vfx/StatusEffectPlayer.ts`'s `spawnDuelBanners`. Unlike `status/`
above (future sidebar chip icons, one per `StatusFlag`), this folder is for
board-level status visuals keyed by effect *name* (`EffectSnapshot.name`,
see `web/src/vfx/StatusEffects.ts`) - most of those effects render with
generated shapes/particles rather than art, so this folder only grows when
one specifically needs an image the way Duel's banner does.

## `ui/` — general UI/branding (not yet wired up, no fixed file list)

Nothing here is required by name yet — this is a catch-all for whatever visual polish comes up:
a proper app logo/wordmark (currently just the text "EBIC"), a nicer favicon (currently the
generic Vite scaffold one at `web/public/favicon.svg`), team emblems (currently plain colored
circles), victory/defeat splash art, board/tile background texture (currently a flat color hex),
menu background art. Add what you have; ask before spending time on anything that needs the exact
filename/size wired into code first.

## `sfx/` — sound effects (optional, nothing implemented yet — no audio playback exists in the client at all)

Not a small addition (needs a whole audio-triggering layer added to the client first), but listed
here so it's not forgotten. If pursued, natural trigger points already exist server-side as
`VfxEvent`s (`web/src/vfx/ParticleBurst.ts`'s `playVfx` is the existing hook) or client actions:

- Attack landing / taking damage
- Unit death
- Ability cast (could start generic, one sound for "an ability fired," refine per-ability later)
- Draft pick confirmed
- Placement confirmed
- Turn start ("your turn") / turn end
- Victory / defeat stingers
- UI clicks (buttons, modal open/close)

Spec if pursued: short (<2s for one-shots), `.mp3` or `.ogg`, normalized volume across the set so
nothing's jarringly louder than the rest.

## `music/` — background music (1 file, not yet wired up)

Same story as `sfx/` above (no audio-playback layer exists in the client yet) — `main_menu.mp3`
is kept here so it isn't lost, awaiting that same future audio feature. Intended for the main
menu screen once one exists; not attached to anything today.

## Formats, sizing, and how fallbacks work

Every category above already has a working fallback (procedural badge for portraits, plain text
for abilities/attributes/status) — nothing breaks by leaving any of this empty. PNG is preferred
throughout for consistency and guaranteed transparency support; SVG is fine too if you're
producing vector art (the client can render either, though the code checks for `.png` by name
today — flag it if you want SVGs and the lookup will need a one-line change to check both
extensions).
