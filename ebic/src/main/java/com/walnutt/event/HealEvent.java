package com.walnutt.event;

import com.walnutt.unit.Unit;

public final class HealEvent implements GameEvent, Cancellable {
    private final Unit source;
    private final Unit target;
    private int amount;
    private boolean cancelled;

    public HealEvent(Unit source, Unit target, int amount) {
        this.source = source;
        this.target = target;
        this.amount = amount;
    }

    public Unit getSource() {
        return source;
    }

    public Unit getTarget() {
        return target;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public void modifyAmount(int delta) {
        this.amount += delta;
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
