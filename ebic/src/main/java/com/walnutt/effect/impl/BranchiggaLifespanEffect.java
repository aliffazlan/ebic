package com.walnutt.effect.impl;

/**
 * How long a Branchigga stands.
 *
 * Its own clock, started when it sprouts rather than when the Overgrowth that seeded it was
 * cast - so a Branchigga grown from a Branchling killed on the last turn of an Overgrowth
 * outlives that Overgrowth by its full duration, and OvergrowthEffect's own expiry (which
 * withers its Branchlings) never touches it.
 */
public class BranchiggaLifespanEffect extends SummonLifespanEffect {

    public BranchiggaLifespanEffect(int duration) {
        super("Branchigga",
            "Grown from a fallen Branchling. It moves and attacks freely, and withers when this "
                + "runs out.",
            duration);
    }
}
