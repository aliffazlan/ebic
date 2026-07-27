package com.walnutt.web.dto;

import java.util.List;

/**
 * This player's own working placement arrangement - fog of war: never
 * includes the opponent's units, regardless of how far along they are. Pushed
 * once when placement starts (pre-filled with DefaultArrangement's layout)
 * and again after every accepted swap/move edit; confirmed flips to true only
 * on the echo right after this player sends "confirm", at which point no
 * further edits are accepted. See WebInputHandler.arrangePlacement.
 *
 * legalTiles is the full set of tiles a "move" edit is allowed to target (see
 * WebInputHandler's PLACEMENT_ZONE_RADIUS) - constant for the whole placement
 * phase (doesn't depend on which unit is selected), included on every push so
 * the client can highlight it without a separate request.
 */
public record PlacementStateSnapshot(
    List<PlacementUnitSnapshot> units,
    boolean confirmed,
    List<TilePosition> legalTiles
) {
}
