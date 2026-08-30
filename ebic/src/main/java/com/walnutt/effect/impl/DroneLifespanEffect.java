package com.walnutt.effect.impl;

/**
 * How long one of Maxwell's Killer Drones lasts.
 *
 * All the machinery is in SummonLifespanEffect, whose class comment explains the timing - the
 * drone is where that subtlety was first found: without it a drone was dismantled a moment
 * before DroneAutoAttack would have fired, costing it its final strike and a whole turn of life
 * on the turn it was deployed.
 */
public class DroneLifespanEffect extends SummonLifespanEffect {

    public DroneLifespanEffect(int duration) {
        super("Power Cell",
            "This drone shuts down and is dismantled when its power cell runs out.",
            duration);
    }
}
