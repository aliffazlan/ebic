package com.walnutt.unit;

public final class HealthPool {
    private int current;
    private int max;

    public HealthPool(int max) {
        this.max = max;
        this.current = max;
    }

    public int getCurrent() {
        return current;
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = max;
        this.current = Math.min(this.current, this.max);
    }

    public void setCurrent(int amount) {
        this.current = Math.max(0, Math.min(amount, max));
    }

    public void applyDamage(int amount) {
        setCurrent(current - Math.max(0, amount));
    }

    public void heal(int amount) {
        setCurrent(current + Math.max(0, amount));
    }

    public boolean isDepleted() {
        return current <= 0;
    }
}
