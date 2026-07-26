package com.walnutt.event;

import com.walnutt.map.Position;
import com.walnutt.unit.Unit;

public final class PreMoveEvent implements GameEvent, Cancellable {
    private final Unit unit;
    private final Position from;
    private final Position to;
    private boolean cancelled;

    public PreMoveEvent(Unit unit, Position from, Position to) {
        this.unit = unit;
        this.from = from;
        this.to = to;
    }

    public Unit getUnit() {
        return unit;
    }

    public Position getFrom() {
        return from;
    }

    public Position getTo() {
        return to;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void cancel() {
        this.cancelled = true;
    }
}
