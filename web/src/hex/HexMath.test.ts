import { describe, expect, it } from "vitest";
import {
  AXIAL_DIRECTIONS,
  axialToPixel,
  hexDistance,
  mapTiles,
  pixelToAxial,
  roundAxial,
} from "./HexMath";

describe("HexMath", () => {
  it("round-trips axial -> pixel -> axial for a grid of coordinates", () => {
    const size = 32;
    for (let q = -8; q <= 8; q++) {
      for (let r = -8; r <= 8; r++) {
        const pixel = axialToPixel({ q, r }, size);
        const back = pixelToAxial(pixel, size);
        expect(back).toEqual({ q, r });
      }
    }
  });

  it("places the origin at pixel (0,0)", () => {
    expect(axialToPixel({ q: 0, r: 0 }, 32)).toEqual({ x: 0, y: 0 });
  });

  it("rounds fractional coordinates to the nearest hex", () => {
    expect(roundAxial({ q: 0.1, r: 0.1 })).toEqual({ q: 0, r: 0 });
    expect(roundAxial({ q: 0.6, r: 0.1 })).toEqual({ q: 1, r: 0 });
  });

  it("computes hex distance via cube coordinates", () => {
    expect(hexDistance({ q: 0, r: 0 }, { q: 0, r: 0 })).toBe(0);
    expect(hexDistance({ q: 0, r: 0 }, { q: 1, r: 0 })).toBe(1);
    // matches the CLAUDE.md-documented gotcha: (4,4) is hex-distance 8 from origin
    expect(hexDistance({ q: 0, r: 0 }, { q: 4, r: 4 })).toBe(8);
  });

  it("has exactly 6 unit-distance neighbor directions", () => {
    expect(AXIAL_DIRECTIONS).toHaveLength(6);
    for (const dir of AXIAL_DIRECTIONS) {
      expect(hexDistance({ q: 0, r: 0 }, dir)).toBe(1);
    }
  });

  // mapTiles is the client's only definition of which tiles exist, and it has to agree
  // with the server's GameMap constructor - these numbers come from the Java side.
  describe("mapTiles", () => {
    it("builds a regular hexagon when nothing is trimmed", () => {
      for (let radius = 0; radius <= 6; radius++) {
        expect(mapTiles(radius, radius)).toHaveLength(3 * radius * radius + 3 * radius + 1);
      }
    });

    it("matches the server's elongated full-match board", () => {
      const tiles = mapTiles(7, 5);

      expect(tiles).toHaveLength(135);
      expect(new Set(tiles.map((t) => t.r)).size).toBe(11);
      expect(tiles.every((t) => Math.abs(t.r) <= 5)).toBe(true);
    });

    it("drops rows past the limit but keeps the full width", () => {
      const tiles = mapTiles(7, 5);
      const has = (q: number, r: number) => tiles.some((t) => t.q === q && t.r === r);

      expect(has(0, 5)).toBe(true);
      expect(has(0, 6)).toBe(false); // inside the radius, but a trimmed row
      expect(has(7, 0)).toBe(true); // the wide axis is untouched
      expect(has(-7, 0)).toBe(true);
    });

    it("clamps a row limit larger than the radius", () => {
      expect(mapTiles(3, 99)).toHaveLength(3 * 9 + 3 * 3 + 1);
    });
  });
});
