# Unit icons

Drop real unit art in this folder to have it picked up automatically — no
code changes needed. `UnitIconFactory` (`web/src/units/UnitIconFactory.ts`)
checks `/icons/<definitionId>.png` first and falls back to a procedurally
drawn placeholder badge (team-colored circle + a type-colored ring + a
letter glyph) when the file is missing.

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

- Format: PNG, square, suggest 256x256.
- Transparent background.
- Subject centered with a bit of margin — the client draws a team-colored
  ring around the art, so don't let the artwork bleed to the edge or it'll
  collide with the ring.

This folder is intentionally empty otherwise; the fallback badge handles
every unit until real art shows up.
