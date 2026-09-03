// Tracks each unit's *displayed* HP/death separately from the authoritative
// truth the server sends, so the board can advance a unit's HP bar and its
// death/graveyard transition in lockstep with each damage indicator as it
// plays, rather than snapping instantly the moment a "state" message lands.
//
// "state" always arrives right after "vfx" with no artificial delay, but the
// animation/indicator playback it describes is deliberately spread out over
// real time afterward - so without this, a lethal hit's HP bar drop and
// graveyard placement would happen before (sometimes long before) the hit's
// own animation and number have even played. Pure - no PixiJS, no DOM - so
// it's unit-testable and kept separate from Board's rendering.

export class DisplayedUnitState {
  private hp = new Map<string, number>();
  private dead = new Set<string>();
  private pending = new Map<string, number>();

  /**
   * Registers one more event still to be visually applied to this unit.
   * Call eagerly, synchronously, before the batch containing that event's
   * "state" message can possibly be processed - see ScheduleVfxBatch.
   */
  beginPendingChange(unitId: string): void {
    this.pending.set(unitId, (this.pending.get(unitId) ?? 0) + 1);
  }

  /**
   * Snaps displayed hp/dead to the given truth, but only if nothing is
   * still pending for this unit - the common case, since most damage is a
   * single instance with nothing to catch up on. If something is still
   * pending, truth is ignored for now; the matching applyChange() calls
   * will catch displayed state up to it on their own schedule.
   */
  syncToTruth(unitId: string, currentHp: number, isDead: boolean): void {
    if ((this.pending.get(unitId) ?? 0) > 0) return;
    this.hp.set(unitId, currentHp);
    if (isDead) this.dead.add(unitId);
  }

  /**
   * Applies one event's signed HP delta (negative for damage, positive for
   * heal) and clears one pending registration. Dead is one-way and sticky -
   * an already-dead unit doesn't take further visible damage, so a later
   * call is a no-op. Reports whether this specific change is what just now
   * crossed the unit into death, for a caller that wants to react to that
   * exact moment.
   */
  applyChange(unitId: string, hpDelta: number): { justDied: boolean } {
    const remaining = Math.max(0, (this.pending.get(unitId) ?? 0) - 1);
    this.pending.set(unitId, remaining);

    if (this.dead.has(unitId)) return { justDied: false };

    const next = (this.hp.get(unitId) ?? 0) + hpDelta;
    this.hp.set(unitId, next);
    if (next <= 0) {
      this.dead.add(unitId);
      return { justDied: true };
    }
    return { justDied: false };
  }

  hpFor(unitId: string, fallback: number): number {
    return this.hp.get(unitId) ?? fallback;
  }

  isDead(unitId: string): boolean {
    return this.dead.has(unitId);
  }

  /** Drops all bookkeeping for a unit that's left the roster entirely (e.g. a dead summon). */
  forget(unitId: string): void {
    this.hp.delete(unitId);
    this.dead.delete(unitId);
    this.pending.delete(unitId);
  }
}
