# Unit icons

Drop real unit art in this folder to have it picked up automatically — no
code changes needed. One file per unit feeds three different display sizes:
the small circular sprite on the hex board (`UnitIconFactory`, ~39px), the
medium circular portrait in the pre-encounter attribute modal (`UnitPortrait`,
72px), and the large square portrait on each draft card (also `UnitPortrait`,
140px — see `Hud.ts`'s `renderUnitCard`/`DRAFT_ICON_SIZE`). All three check
`/icons/<definitionId>.png` first and fall back to a procedurally drawn
placeholder badge (team-colored background + a type-colored ring/border + a
large letter glyph) when the file is missing — draft cards are the one
exception, since a not-yet-picked unit has no owning team yet, so that badge
uses a neutral dark background instead of guessing one. Nothing needs real
art to look reasonable today, and nothing needs code changes once art shows
up.

## Naming convention

Filename must be exactly `<definitionId>.png`, where `definitionId` is the
lowercase-underscore id matching the unit's JSON filename under
`design_ideas/units/**/*.json` (minus the `.json` extension).

**Champions:**
- `valor.png`
- `harbinger.png`
- `chronos.png`
- `zenith.png`

**Elites:**
- `dirge.png`
- `evayne.png`
- `spitter.png`
- `auroth.png`
- `lanaya.png`
- `wei.png`
- `mercurial.png`
- `thaddeus.png`
- `yuki.png`
- `grivath.png`
- `discharge.png`
- `lucifer.png`

Basics have no `definitionId`-backed JSON and always use the procedural
badge — no art needed for them.

## Art guidelines

- Format: PNG, square, suggest 256x256 — comfortably larger than the biggest
  current display size (140px, the draft card), so nothing ever upscales.
- Transparent background.
- Subject centered with a bit of margin — the client draws a team-colored
  ring around the art, so don't let the artwork bleed to the edge or it'll
  collide with the ring.

This folder is intentionally empty otherwise; the fallback badge handles
every unit until real art shows up.
