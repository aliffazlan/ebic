import { describe, expect, it } from "vitest";
import { artId, faceUrl, normalizeId, portraitUrl } from "./UnitArt";

describe("normalizeId", () => {
  it("matches the server's Identifiers.normalize on the names summons actually use", () => {
    expect(normalizeId("Lanaya (Clone)")).toBe("lanaya_clone");
    expect(normalizeId("Snow Golem")).toBe("snow_golem");
    expect(normalizeId("Mercurial Shadow")).toBe("mercurial_shadow");
    expect(normalizeId("Drone")).toBe("drone");
  });

  it("collapses runs of punctuation and drops trailing separators", () => {
    expect(normalizeId("Cloak and Dagger")).toBe("cloak_and_dagger");
    expect(normalizeId("  Valor!!  ")).toBe("valor");
    expect(normalizeId("Player One Basic 10")).toBe("player_one_basic_10");
  });
});

describe("artId", () => {
  it("passes a real definitionId straight through", () => {
    expect(artId("valor", "Valor", "PLAYER_ONE")).toBe("valor");
    expect(artId("maxwell", "Maxwell", "PLAYER_TWO")).toBe("maxwell");
  });

  it("recovers each summon's own art from its display name", () => {
    expect(artId("basic", "Drone", "PLAYER_ONE")).toBe("maxwell_drone");
    expect(artId("basic", "Snow Golem", "PLAYER_ONE")).toBe("yuki_golem");
    expect(artId("basic", "Lanaya (Clone)", "PLAYER_TWO")).toBe("lanaya_clone");
    expect(artId("basic", "Branchling", "PLAYER_ONE")).toBe("branchling");
    expect(artId("basic", "Branchigga", "PLAYER_ONE")).toBe("branchigga");
  });

  it("borrows Mercurial's own art for his shadow", () => {
    expect(artId("basic", "Mercurial Shadow", "PLAYER_TWO")).toBe("mercurial");
  });

  it("gives generic basics the knight matching their team", () => {
    expect(artId("basic", "Alice Basic 1", "PLAYER_ONE")).toBe("basic1");
    expect(artId("basic", "Bob Basic 7", "PLAYER_TWO")).toBe("basic2");
  });

  it("falls back to the neutral basic when nobody owns the unit yet", () => {
    expect(artId("basic", "Alice Basic 1")).toBe("basic0");
  });

  it("keeps a summon with no shipped art off the generic basic", () => {
    // No pylon art yet, and a Pylon drawn as a team knight would read as a bug -
    // this id 404s and lands on the procedural badge instead.
    expect(artId("basic", "Pylon", "PLAYER_ONE")).toBe("zenith_pylon");
  });
});

describe("art urls", () => {
  it("points at the two art folders", () => {
    expect(faceUrl("valor")).toBe("/icons/valor.webp");
    expect(portraitUrl("valor")).toBe("/portraits/valor.webp");
  });
});
