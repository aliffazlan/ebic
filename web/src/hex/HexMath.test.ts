import { describe, expect, it } from "vitest";
import {
  AXIAL_DIRECTIONS,
  axialToPixel,
  hexDistance,
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
});
