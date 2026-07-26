package com.walnutt.web.dto;

import java.util.List;

public record DraftRoundSnapshot(
    String roundLabel,
    List<UnitDefinitionSnapshot> yourOptions,
    List<UnitDefinitionSnapshot> opponentOptions
) {
}
