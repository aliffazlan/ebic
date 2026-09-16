import { api, ApiError } from "../net/api";
import type { UnitDefinitionSnapshot } from "../types/contract";
import type { Screen } from "./Screen";
import { Tooltip } from "./Tooltip";
import { renderUnitCard } from "./UnitCard";

type TypeFilter = "ALL" | "CHAMPION" | "ELITE";

const FILTERS: ReadonlyArray<{ value: TypeFilter; label: string }> = [
  { value: "ALL", label: "All" },
  { value: "CHAMPION", label: "Champions" },
  { value: "ELITE", label: "Elites" },
];

/**
 * The roster, readable outside a match. Every hero's stats and full ability text were
 * previously only visible during a draft, at the moment you had seconds to choose between
 * two of them - so this renders exactly the same card, from the same definitions, with
 * nothing to pick and no clock running.
 *
 * It owns its own Tooltip: this screen is never mounted at the same time as the HUD, and a
 * tooltip is only ever attached to elements belonging to one live screen.
 */
export class CodexScreen implements Screen {
  private root: HTMLElement;
  private onBack: () => void;
  private el: HTMLElement | null = null;
  private tooltip = new Tooltip();
  private units: UnitDefinitionSnapshot[] = [];
  private filter: TypeFilter = "ALL";
  private search = "";
  private grid: HTMLElement | null = null;

  constructor(root: HTMLElement, onBack: () => void) {
    this.root = root;
    this.onBack = onBack;
  }

  mount(): void {
    // The sliding root is full-bleed and absolutely positioned (so it can
    // overlap the lobby during a transition); the bordered window inside it
    // is what the player perceives as the screen.
    const wrap = document.createElement("div");
    wrap.className = "codex-screen";
    this.el = wrap;
    this.root.appendChild(wrap);

    const windowEl = document.createElement("div");
    windowEl.className = "codex-window";
    wrap.appendChild(windowEl);

    const toolbar = document.createElement("div");
    toolbar.className = "codex-toolbar";
    windowEl.appendChild(toolbar);

    const backBtn = document.createElement("button");
    backBtn.textContent = "Back";
    backBtn.addEventListener("click", () => this.onBack());
    toolbar.appendChild(backBtn);

    const title = document.createElement("h1");
    title.textContent = "Unit info";
    toolbar.appendChild(title);

    const filters = document.createElement("div");
    filters.className = "codex-filters";
    for (const option of FILTERS) {
      const btn = document.createElement("button");
      btn.textContent = option.label;
      if (option.value === this.filter) btn.classList.add("primary");
      btn.addEventListener("click", () => {
        this.filter = option.value;
        for (const sibling of Array.from(filters.children)) {
          sibling.classList.remove("primary");
        }
        btn.classList.add("primary");
        this.renderGrid();
      });
      filters.appendChild(btn);
    }
    toolbar.appendChild(filters);

    const searchInput = document.createElement("input");
    searchInput.placeholder = "Search by name";
    searchInput.addEventListener("input", () => {
      this.search = searchInput.value.trim().toLowerCase();
      this.renderGrid();
    });
    toolbar.appendChild(searchInput);

    this.grid = document.createElement("div");
    this.grid.className = "codex-grid";
    windowEl.appendChild(this.grid);

    this.grid.appendChild(hint("Loading units..."));
    void this.load();
  }

  unmount(): void {
    this.tooltip.destroy();
    this.el?.remove();
    this.el = null;
  }

  getElement(): HTMLElement | null {
    return this.el;
  }

  private async load(): Promise<void> {
    try {
      this.units = (await api.getUnits()).units;
      this.renderGrid();
    } catch (err) {
      if (!this.grid) return;
      this.grid.innerHTML = "";
      const message = err instanceof ApiError ? err.message : "Could not load the roster.";
      const error = document.createElement("div");
      error.className = "error-text";
      error.textContent = message;
      this.grid.appendChild(error);
    }
  }

  private renderGrid(): void {
    if (!this.grid) return;
    this.tooltip.hide();
    this.grid.innerHTML = "";

    const matching = this.units.filter((unit) =>
      (this.filter === "ALL" || unit.type === this.filter)
      && (this.search === "" || unit.name.toLowerCase().includes(this.search)));

    if (matching.length === 0) {
      this.grid.appendChild(hint("No units match that."));
      return;
    }
    for (const unit of matching) {
      this.grid.appendChild(renderUnitCard(unit, this.tooltip));
    }
  }
}

function hint(text: string): HTMLElement {
  const el = document.createElement("div");
  el.className = "hint";
  el.textContent = text;
  return el;
}
