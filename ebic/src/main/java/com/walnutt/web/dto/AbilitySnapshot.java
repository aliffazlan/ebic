package com.walnutt.web.dto;

public record AbilitySnapshot(
    String id,
    String name,
    String description,
    boolean passive,
    boolean ready,
    int currentCooldown,
    int maxCooldown
) {
}
