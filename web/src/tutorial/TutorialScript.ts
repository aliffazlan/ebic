// The tutorial's full step sequence, paraphrased from temp/tutorial.txt (paraphrasing
// dialogue is explicitly fine per the request that produced this feature). Each step
// is dialogue + a gate; see types.ts for what a gate means and TutorialRunner.ts for
// how it's enforced. Kept as one flat array (rather than nested per-phase files) since
// the whole point of a script is that its ORDER carries meaning - splitting it up would
// just require re-assembling it here anyway.

import { buildActionPrompt } from "./legalTargets";
import { battleSnapshotFromPlacement, battleStartSnapshot, enemyAdvancedPositions } from "./data/battle";
import { DRAFT_ROUNDS } from "./data/draft";
import { midBattleSnapshot } from "./data/midBattle";
import { computePlacementLegalTiles, FRONT_ROW_Q, initialPlacement } from "./data/placement";
import type { TutorialStep } from "./types";

export const TUTORIAL_SCRIPT: TutorialStep[] = [
  {
    id: "opening-1",
    devCheckpoint: 0,
    dialogue: [
      { speaker: "valor", text: "..." },
      { speaker: "valor", text: "On your feet, warrior - the enemy approaches." },
    ],
    gate: { kind: "none" },
  },
  {
    id: "opening-2",
    dialogue: [{ speaker: "harbinger", text: "You and your army shall fall before me..." }],
    gate: { kind: "none" },
  },
  {
    id: "opening-3",
    dialogue: [{ speaker: "valor", text: "!!!" }],
    gate: { kind: "none" },
  },

  // Every devCheckpoint step's onEnter is self-sufficient (pushes whatever
  // draftRound/placementState/snapshot it needs itself) rather than relying on the
  // PREVIOUS step's onAdvance - that's what lets `?tutorialStep=N` jump straight to
  // any of them and still render correctly instead of landing on an empty board.
  {
    id: "draft-champion",
    devCheckpoint: 1,
    dialogue: [
      { speaker: "valor", text: "Come, warrior - we must ready our army against the Harbinger's invaders." },
      { speaker: "valor", text: "This is the Drafting menu. Here you choose the army that will fight at your side." },
      {
        speaker: "valor",
        text: "Your first choice is your CHAMPION - the one we must protect above all. Defeat the enemy's champion, and the battle is won.",
      },
      {
        speaker: "valor",
        text: "Each round gives you two choices, and you must pick one. You'll see the enemy's two options on the other side, too.",
      },
      { speaker: "valor", text: "You can trust me to be your champion. Click on me to draft me." },
    ],
    objective: "Draft Valor as your champion",
    gate: { kind: "exact-pick", definitionId: "valor" },
    onEnter: async ({ host }) => {
      await host.fadeOut();
      host.store.setState({ draftRound: DRAFT_ROUNDS[0] });
      await host.fadeIn();
    },
  },
  {
    id: "draft-elite-auroth",
    devCheckpoint: 2,
    dialogue: [
      { speaker: "valor", text: "Nice work. Now we need to recruit three ELITE units - stronger fighters with abilities of their own." },
      { speaker: "auroth", text: "Come, my child. Let me stand at your side in this battle." },
    ],
    objective: "Draft Auroth as an elite",
    gate: { kind: "exact-pick", definitionId: "auroth" },
    onEnter: ({ host }) => host.store.setState({ draftRound: DRAFT_ROUNDS[1] }),
  },
  {
    id: "draft-elite-evayne",
    devCheckpoint: 3,
    dialogue: [{ speaker: "evayne", text: "This city is my home too. Let me help defend it." }],
    objective: "Draft Evayne as an elite",
    gate: { kind: "exact-pick", definitionId: "evayne" },
    onEnter: ({ host }) => host.store.setState({ draftRound: DRAFT_ROUNDS[2] }),
  },
  {
    id: "draft-elite-thaddeus",
    devCheckpoint: 4,
    dialogue: [{ speaker: "thaddeus", text: "Victory is won through strong will. I'll lend you mine." }],
    objective: "Draft Thaddeus as an elite",
    gate: { kind: "exact-pick", definitionId: "thaddeus" },
    onEnter: ({ host }) => host.store.setState({ draftRound: DRAFT_ROUNDS[3] }),
  },

  {
    id: "placement-intro",
    devCheckpoint: 5,
    dialogue: [
      { speaker: "valor", text: "A solid lineup. Time to face Harbinger's army!" },
      { speaker: "valor", text: "This is the Placement screen - where our army begins the battle. Arrange us into a strong formation." },
      { speaker: "valor", text: "Click a unit to select it, then click a tile to place it there." },
      { speaker: "valor", text: "Start by putting Thaddeus on the frontmost tile you can. He can tank for us." },
    ],
    objective: "Place Thaddeus on the frontmost column",
    gate: { kind: "exact-placement", unitId: "u-thaddeus", targetQ: FRONT_ROW_Q },
    onEnter: ({ host }) => host.store.setState({ draftRound: null, placementState: initialPlacement() }),
  },
  {
    id: "placement-free",
    devCheckpoint: 6,
    dialogue: [
      {
        speaker: "valor",
        text:
          "Excellent! Now arrange the rest of our army freely. I'd put Auroth at the back to support us, Evayne on a flank to slip around the enemy, and myself somewhere in the middle - impactful, but exposed. Then form up the rest of our basics however you see fit.",
      },
    ],
    objective: "Arrange the rest of your army, then confirm",
    gate: { kind: "confirm-only" },
    // Only a fallback for a direct dev-jump to this checkpoint - normal flow already
    // has placementState (with Thaddeus correctly placed) from placement-intro.
    onEnter: ({ host }) => {
      if (!host.store.getState().placementState) {
        const placement = initialPlacement();
        const thaddeus = placement.units.find((u) => u.unitId === "u-thaddeus");
        if (thaddeus) thaddeus.q = FRONT_ROW_Q;
        placement.legalTiles = computePlacementLegalTiles(placement.units);
        host.store.setState({ placementState: placement });
      }
    },
  },

  {
    id: "battle-basics",
    devCheckpoint: 7,
    dialogue: [
      { speaker: "valor", text: "A solid formation, warrior. Let's head to battle!" },
      { speaker: "harbinger", text: "Your time has come, mortals. I bring your doom..." },
      {
        speaker: "valor",
        text: "We're in battle now. Each turn you get 3 ACTIONS to spend on your CHAMPION or ELITE units. BASIC units don't cost any actions at all.",
      },
      { speaker: "valor", text: "Advance a bunch of our basic units forward!" },
    ],
    objective: "Move 5 of your basic units forward",
    gate: { kind: "count-basics-moved", atLeast: 5 },
    onEnter: ({ host }) => {
      const placement = host.store.getState().placementState;
      const snapshot = placement ? battleSnapshotFromPlacement(placement.units) : battleStartSnapshot();
      host.store.setState({ placementState: null, snapshot, prompt: buildActionPrompt(snapshot) });
    },
  },
  {
    id: "battle-elites",
    devCheckpoint: 8,
    dialogue: [{ speaker: "valor", text: "Now move me, Thaddeus, and Evayne forward too." }],
    objective: "Move Valor, Thaddeus, and Evayne",
    gate: { kind: "exact-unit-actions", unitIds: ["u-valor", "u-thaddeus", "u-evayne"] },
    // Dev-jump fallback only: normal flow already has a snapshot with 5 basics moved
    // from battle-basics; re-pushing a fresh battleStartSnapshot here just means a
    // direct jump to this checkpoint skips straight to testing the elites-move gate.
    onEnter: ({ host }) => {
      if (!host.store.getState().snapshot) {
        const snapshot = battleStartSnapshot();
        host.store.setState({ snapshot, prompt: buildActionPrompt(snapshot) });
      }
    },
  },
  {
    id: "battle-endturn",
    dialogue: [
      {
        speaker: "valor",
        text:
          "Nicely done - that's all three of our actions spent. We can still move our BASIC units freely, but let's end our turn here and see what Harbinger is plotting.",
      },
    ],
    objective: "End your turn",
    gate: { kind: "end-turn" },
  },
  {
    id: "enemy-turn",
    gate: { kind: "none" },
    onEnter: async ({ host }) => {
      const current = host.store.getState().snapshot;
      if (!current) return;
      const advanced = enemyAdvancedPositions();
      let advancing = structuredClone(current);
      advancing.currentTeam = "PLAYER_TWO";

      // Move one enemy unit at a time with a short gap between each, rather than
      // teleporting all 14 units to their advanced tiles simultaneously. Sorted by
      // ascending current q (not roster order): every advance is a q-1 step toward the
      // player, so whichever unit is already furthest forward (smallest q) must clear
      // its tile before a unit behind it (larger q) can move into that space - this
      // order guarantees a unit's destination is always already vacated by the time it
      // gets there, instead of momentarily overlapping a still-stationary unit ahead of it.
      const moverIds = advancing.units
        .filter((u) => advanced[u.id])
        .sort((a, b) => a.q - b.q)
        .map((u) => u.id);
      for (const unitId of moverIds) {
        const next = structuredClone(advancing);
        const unit = next.units.find((u) => u.id === unitId)!;
        Object.assign(unit, advanced[unitId]);
        advancing = next;
        host.store.setState({ snapshot: advancing, prompt: { kind: "action", team: "PLAYER_TWO", legalTargets: {} } });
        await host.delay(200);
      }

      const backToPlayer = structuredClone(advancing);
      backToPlayer.currentTeam = "PLAYER_ONE";
      backToPlayer.remainingMoves = 3;
      for (const unit of backToPlayer.units) {
        if (unit.team === "PLAYER_ONE") {
          unit.hasMovedThisTurn = false;
          unit.hasAttackedThisTurn = false;
        }
      }
      host.store.setState({ snapshot: backToPlayer, prompt: buildActionPrompt(backToPlayer) });
    },
    dialogue: [{ speaker: "valor", text: "...This is going to get rough." }],
  },

  {
    id: "midbattle-evayne-attacks",
    devCheckpoint: 9,
    objective: "Attack Grivath with Evayne",
    gate: { kind: "rigged-attribute", outcome: "miss", attackerId: "u-evayne", targetId: "e-grivath" },
    onEnter: async ({ host }) => {
      await host.fadeOut();
      const snapshot = midBattleSnapshot();
      host.store.setState({ snapshot, prompt: buildActionPrompt(snapshot) });
      await host.fadeIn();
    },
    dialogue: [
      { speaker: "valor", text: "Warrior - we need your help." },
      { speaker: "valor", text: "Let's practice attacking. Every unit has three attributes: STRENGTH, AGILITY, and INTELLIGENCE." },
      {
        speaker: "valor",
        text: "When you attack, both sides pick an attribute in secret. Strength beats Intelligence, Intelligence beats Agility, and Agility beats Strength.",
      },
      { speaker: "valor", text: "Win the exchange and you deal damage with that attribute. Lose, and your attack misses entirely." },
      { speaker: "evayne", text: "I think I can take Grivath! Let me at him!" },
    ],
  },
  {
    id: "evayne-missed",
    dialogue: [
      { speaker: "evayne", text: "Ugh... I missed!" },
      { speaker: "valor", text: "Grivath read your attack and dodged clean out of the way!" },
      { speaker: "valor", text: "Evayne's badly hurt - let's heal her up." },
      { speaker: "auroth", text: "Come, child. Let the frost soothe your wounds." },
    ],
    objective: "Heal Evayne with Auroth's Cold Embrace",
    gate: { kind: "exact-ability", unitId: "u-auroth", abilityId: "cold_embrace", targetId: "u-evayne" },
  },
  {
    id: "evayne-healed",
    dialogue: [
      { speaker: "valor", text: "Nice call - Evayne should be on the mend now." },
      { speaker: "harbinger", text: "You're only delaying the inevitable..." },
      { speaker: "valor", text: "Foul creature! I'll finish you myself." },
      { speaker: "valor", text: "Choose wisely, warrior - this will decide the fate of the battle. Try to guess what the enemy will pick!" },
    ],
    objective: "Attack Harbinger with Valor",
    gate: { kind: "rigged-attribute", outcome: "kill", attackerId: "u-valor", targetId: "e-harbinger" },
  },
  {
    id: "victory",
    dialogue: [
      { speaker: "harbinger", text: "No... impossible! Aaarghhh!" },
      {
        speaker: "valor",
        text: "Well fought, warrior! We've beaten back Harbinger and saved our home. You're ready to command an army of your own.",
      },
    ],
    gate: { kind: "none" },
    onAdvance: ({ host }) => {
      const state = host.store.getState();
      host.store.setState({ gameOver: { winnerTeam: "PLAYER_ONE", winnerName: state.playerOneName } });
    },
  },
];
