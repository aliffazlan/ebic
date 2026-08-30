package com.walnutt.effect.impl;

import com.walnutt.status.StatusFlag;

/**
 * How long Mercurial's shadow stands, and the fact that it cannot walk.
 *
 * ROOTED rather than simply a missing Move ability: the shadow is built by hand and given no
 * Move, but the flag is what tells the client - and any future forced-movement rule - that
 * standing still is deliberate rather than an oversight.
 */
public class ShadowLifespanEffect extends SummonLifespanEffect {

    public ShadowLifespanEffect(int duration) {
        super("Manifestation Shadow",
            "A shadow left behind by Manifestation. It cannot move, and fades when this runs out. "
                + "Its Recall pulls Mercurial back to wherever it is standing.",
            duration);
        this.flags.add(StatusFlag.ROOTED);
    }
}
