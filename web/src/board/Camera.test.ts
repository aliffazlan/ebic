import { describe, expect, it } from "vitest";
import {
  type Camera,
  clampCamera,
  clampZoom,
  contentBounds,
  EDGE_MARGIN,
  MAX_ZOOM,
  MIN_ZOOM,
  panBy,
  screenToWorld,
  wheelZoomFactor,
  worldToScreen,
  zoomAt,
} from "./Camera";

const VIEWPORT = { width: 1000, height: 700 };
const BOUNDS = contentBounds(7, 5, 34);

describe("Camera transforms", () => {
  it("round-trips world -> screen -> world", () => {
    const camera: Camera = { x: 480, y: 310, zoom: 1.7 };
    for (const world of [{ x: 0, y: 0 }, { x: 123, y: -456 }, { x: -559, y: 255 }]) {
      const back = screenToWorld(camera, worldToScreen(camera, world));
      expect(back.x).toBeCloseTo(world.x, 10);
      expect(back.y).toBeCloseTo(world.y, 10);
    }
  });

  it("pans by a screen-space delta regardless of zoom", () => {
    expect(panBy({ x: 100, y: 50, zoom: 2.5 }, 20, -30)).toEqual({ x: 120, y: 20, zoom: 2.5 });
  });
});

describe("clampZoom", () => {
  it("holds the configured limits", () => {
    expect(clampZoom(0.001)).toBe(MIN_ZOOM);
    expect(clampZoom(99)).toBe(MAX_ZOOM);
    expect(clampZoom(1.3)).toBe(1.3);
  });
});

describe("wheelZoomFactor", () => {
  it("zooms in on scroll up and out on scroll down", () => {
    expect(wheelZoomFactor(-100, 0)).toBeGreaterThan(1);
    expect(wheelZoomFactor(100, 0)).toBeLessThan(1);
    expect(wheelZoomFactor(0, 0)).toBe(1);
  });

  it("is monotonic in delta and symmetric about zero", () => {
    expect(wheelZoomFactor(-200, 0)).toBeGreaterThan(wheelZoomFactor(-50, 0));
    expect(wheelZoomFactor(-50, 0) * wheelZoomFactor(50, 0)).toBeCloseTo(1, 10);
  });

  it("normalises line and page deltas to be stronger than pixel deltas", () => {
    // One wheel notch is ~3 lines on a mouse but ~100px on a trackpad; without
    // normalisation a line-mode wheel would barely move the zoom at all.
    expect(wheelZoomFactor(-3, 1)).toBeGreaterThan(wheelZoomFactor(-3, 0));
    expect(wheelZoomFactor(-1, 2)).toBeGreaterThan(wheelZoomFactor(-1, 1));
  });

  it("clamps a single violent event so one flick can't cross the whole range", () => {
    expect(wheelZoomFactor(-100000, 0)).toBeLessThanOrEqual(MAX_ZOOM / MIN_ZOOM);
    expect(wheelZoomFactor(-100000, 0)).toBeCloseTo(1 / wheelZoomFactor(100000, 0), 10);
  });
});

describe("zoomAt", () => {
  it("keeps the world point under the anchor exactly where it was", () => {
    const camera: Camera = { x: 500, y: 350, zoom: 1 };
    const anchor = { x: 620, y: 210 };
    const before = screenToWorld(camera, anchor);
    const after = zoomAt(camera, anchor, 1.4);
    expect(after.zoom).toBeCloseTo(1.4, 10);
    const stillThere = worldToScreen(after, before);
    expect(stillThere.x).toBeCloseTo(anchor.x, 10);
    expect(stillThere.y).toBeCloseTo(anchor.y, 10);
  });

  it("keeps the anchor fixed even when the zoom runs into a limit", () => {
    // The requested factor is only partly applied here, which is exactly the
    // case where recomputing the position off the *unclamped* zoom would drift.
    for (const camera of [
      { x: 500, y: 350, zoom: MAX_ZOOM } as Camera,
      { x: 500, y: 350, zoom: MIN_ZOOM } as Camera,
    ]) {
      const anchor = { x: 300, y: 500 };
      const before = screenToWorld(camera, anchor);
      for (const factor of [4, 0.25]) {
        const after = zoomAt(camera, anchor, factor);
        expect(after.zoom).toBeGreaterThanOrEqual(MIN_ZOOM);
        expect(after.zoom).toBeLessThanOrEqual(MAX_ZOOM);
        const stillThere = worldToScreen(after, before);
        expect(stillThere.x).toBeCloseTo(anchor.x, 10);
        expect(stillThere.y).toBeCloseTo(anchor.y, 10);
      }
    }
  });
});

describe("clampCamera", () => {
  it("leaves a camera that is already within bounds alone", () => {
    const camera: Camera = { x: 500, y: 350, zoom: 1 };
    expect(clampCamera(camera, BOUNDS, VIEWPORT)).toEqual(camera);
  });

  it("never lets the board be pushed entirely off any edge", () => {
    for (const camera of [
      { x: -100000, y: 350, zoom: 1 } as Camera,
      { x: 100000, y: 350, zoom: 1 } as Camera,
      { x: 500, y: -100000, zoom: 1 } as Camera,
      { x: 500, y: 100000, zoom: 1 } as Camera,
    ]) {
      const clamped = clampCamera(camera, BOUNDS, VIEWPORT);
      const topLeft = worldToScreen(clamped, { x: BOUNDS.minX, y: BOUNDS.minY });
      const bottomRight = worldToScreen(clamped, { x: BOUNDS.maxX, y: BOUNDS.maxY });
      expect(bottomRight.x).toBeGreaterThanOrEqual(EDGE_MARGIN - 1e-9);
      expect(topLeft.x).toBeLessThanOrEqual(VIEWPORT.width - EDGE_MARGIN + 1e-9);
      expect(bottomRight.y).toBeGreaterThanOrEqual(EDGE_MARGIN - 1e-9);
      expect(topLeft.y).toBeLessThanOrEqual(VIEWPORT.height - EDGE_MARGIN + 1e-9);
    }
  });

  it("preserves zoom", () => {
    expect(clampCamera({ x: 1e6, y: 1e6, zoom: 2.5 }, BOUNDS, VIEWPORT).zoom).toBe(2.5);
  });

  it("keeps a big board covering a viewport smaller than the margins", () => {
    // The margins exceed the viewport here, so "EDGE_MARGIN of content is
    // visible" over-constrains - but the board is far bigger than the viewport,
    // so every allowed position still covers it completely.
    const viewport = { width: 100, height: 80 };
    for (const camera of [{ x: 5000, y: -5000, zoom: 1 }, { x: -5000, y: 5000, zoom: 1 }]) {
      const clamped = clampCamera(camera as Camera, BOUNDS, viewport);
      const topLeft = worldToScreen(clamped, { x: BOUNDS.minX, y: BOUNDS.minY });
      const bottomRight = worldToScreen(clamped, { x: BOUNDS.maxX, y: BOUNDS.maxY });
      expect(topLeft.x).toBeLessThanOrEqual(0);
      expect(topLeft.y).toBeLessThanOrEqual(0);
      expect(bottomRight.x).toBeGreaterThanOrEqual(viewport.width);
      expect(bottomRight.y).toBeGreaterThanOrEqual(viewport.height);
    }
  });

  it("centres rather than dividing by nothing when no position satisfies both margins", () => {
    // Only reachable defensively - it needs content smaller than the leftover
    // room, e.g. the degenerate 0x0 viewport the renderer reports before its
    // first resize. Assert it centres instead of returning something wild.
    const tiny = { minX: -10, maxX: 10, minY: -10, maxY: 10 };
    const clamped = clampCamera({ x: 5000, y: -5000, zoom: 1 }, tiny, { width: 0, height: 0 });
    expect(clamped.x).toBe(0);
    expect(clamped.y).toBe(0);
  });
});

describe("contentBounds", () => {
  it("covers the full hex grid plus a hex of padding", () => {
    // The widest tile that actually exists is q=7,r=0: mapTiles also enforces
    // |q + r| <= radius, so q=7 only ever pairs with r <= 0. Picking the naive
    // q=7,r=5 corner here would assert a tile the board never draws.
    const extremeX = 34 * Math.sqrt(3) * 7;
    expect(BOUNDS.maxX).toBeGreaterThanOrEqual(extremeX + 34);
    expect(BOUNDS.minX).toBeLessThanOrEqual(-(extremeX + 34));
    // Tallest row is r = +/-5 (the row trim), at y = 34 * 1.5 * 5.
    const extremeY = 34 * 1.5 * 5;
    expect(BOUNDS.maxY).toBeGreaterThanOrEqual(extremeY + 34);
  });

  it("covers the graveyard columns", () => {
    const graveyardEdge = (7 + 1.6) * 34 * 1.5;
    expect(BOUNDS.maxX).toBeGreaterThanOrEqual(graveyardEdge);
    const graveyardTop = (5 + 0.5) * 34 * 1.732;
    expect(BOUNDS.minY).toBeLessThanOrEqual(-graveyardTop);
  });

  it("is symmetric and non-degenerate", () => {
    expect(BOUNDS.maxX).toBeCloseTo(-BOUNDS.minX, 10);
    expect(BOUNDS.maxY).toBeCloseTo(-BOUNDS.minY, 10);
    expect(BOUNDS.maxX).toBeGreaterThan(BOUNDS.minX);
  });

  it("stays usable for the empty board the renderer starts with", () => {
    // Board's mapRadius/mapRowLimit sentinel is -1 until a snapshot arrives.
    const empty = contentBounds(-1, -1, 34);
    expect(empty.maxX).toBeGreaterThan(empty.minX);
    expect(empty.maxY).toBeGreaterThan(empty.minY);
  });
});
