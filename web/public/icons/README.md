# Unit art

Two folders, one naming convention. Drop a file in and it is picked up
automatically - no code changes needed.

| Folder | Contents | Format | Shown by |
|---|---|---|---|
| `web/public/icons/` | Face close-up | WebP, 256x256 | The hex board's unit tokens, and the stack picker |
| `web/public/portraits/` | Full body | WebP, 512px wide, height by ratio | The pre-encounter attribute modal, draft cards, the codex |

Both are keyed by the same **art id**: `icons/<artId>.webp` and
`portraits/<artId>.webp`. Anything missing falls back to a procedurally drawn
badge (team-coloured background, type-coloured ring, the unit's first two
letters), so nothing ever renders empty and no unit *needs* art to be playable.

## Art id

The art id is normally the unit's `definitionId` - the lowercase-underscore id
matching its JSON filename under `design_ideas/units/**/*.json`, minus the
`.json`. `valor.webp`, `harbinger.webp`, `chronos.webp`, and so on for all six
champions and all sixteen elites.

The exception is BASIC units. The server maps that whole type to the single
string `"basic"` (`GameStateSnapshotMapper.definitionId`), so the ten generic
basics per side *and* every summon arrive under one id. `web/src/units/UnitArt.ts`
recovers the real id from the unit's display name; see `artId()` there. That
means:

**Generic basics are per-team colour variants**, picked by owning team:

| File | Colour | Used for |
|---|---|---|
| `basic0` | neutral | No owning team yet (a draft card) |
| `basic1` | blue | `PLAYER_ONE` |
| `basic2` | red | `PLAYER_TWO` |
| `basic3` | green | reserved - unused until there are more than two players |
| `basic4` | yellow | reserved - portrait only, no face shipped |

**Summons** map by display name:

| In-game name | Art id |
|---|---|
| Drone (Maxwell's) | `maxwell_drone` |
| Snow Golem (Yuki's) | `yuki_golem` |
| Lanaya (Clone) | `lanaya_clone` |
| Mercurial Shadow | `mercurial` - no art of its own; it is his shadow |
| Branchling | `branchling` |
| Branchigga | `branchigga` |
| Pylon (Zenith's) | `zenith_pylon` - **not shipped**, see below |

## The one gap

`zenith_pylon` has no art in either folder. It maps to that id anyway rather than
to a team basic, because a Pylon drawn as a knight would read as a bug - so it
shows the procedural badge instead. Dropping `zenith_pylon.webp` into both
folders lights it up with no code change.

## Adding or replacing art

- **Everything is drawn facing right.** The client flips one side so the two
  armies confront each other rather than marching the same way: on the board
  `PLAYER_TWO` turns, and in the encounter modal and draft columns the flip
  follows the *slot* instead (left faces right, right faces left), because which
  team sits on the left there depends on who is looking. Keep new art
  right-facing and this is automatic.
- **Faces** are composited onto `#0f172a` and shipped fully opaque. The board
  masks them to a circle and draws a team-coloured ring plus a type-coloured one
  (gold champion, silver elite, grey basic) around the outside, so keep the
  subject centred and don't put anything you care about in the corners - the
  circular crop eats them.
- **Portraits** keep their alpha; the transparent background is what lets a hero
  stand cleanly on the modal. Portrait orientation, subject standing on the
  bottom edge. Ratios do not need to match each other - the client fixes the
  width and lets height follow, and bottom-aligns fighters so they share a ground
  line.
- Both are re-encoded from the full-resolution masters, which are deliberately
  **not** in this repo (they run ~63MB). Keep them archived elsewhere.
