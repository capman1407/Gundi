package com.david.gundi.gui;

/**
 * Spring animation. By default critically damped (smooth, no bounce, Apple-like); with a lower damping it
 * overshoots and wobbles a little before settling, which themes like Arcade and Aurora use for a playful feel.
 *
 * Instead of "go from A to B in 0.3s", a spring is pulled towards its target. If the target changes halfway
 * (e.g. you stop hovering mid-animation), it simply turns around from where it is, at its current speed.
 * That's what makes the motion feel fluid and interruptible.
 *
 * response = roughly how many seconds it takes to settle. 0.3-0.4 is a typical Apple value.
 */
public class Spring {
    public double value;
    public double velocity;

    public Spring(double initial) {
        this.value = initial;
    }

    public double update(double target, double delta, double response) {
        return update(target, delta, response, 1);
    }

    /** damping: 1 = no overshoot, 0.3 = very bouncy. */
    public double update(double target, double delta, double response, double damping) {
        if (delta <= 0) return value;
        delta = Math.min(delta, 0.1); // a lag spike shouldn't fling the spring
        damping = Math.max(0.1, Math.min(1, damping));

        double omega = 2 * Math.PI / Math.max(response, 0.01);
        // Small fixed steps keep the maths stable at any frame rate.
        int steps = Math.max(1, (int) Math.ceil(delta * 240));
        double h = delta / steps;

        for (int i = 0; i < steps; i++) {
            double acceleration = -omega * omega * (value - target) - 2 * damping * omega * velocity;
            velocity += acceleration * h;
            value += velocity * h;
        }

        // Settle exactly on the target once it's close enough, so it doesn't creep forever.
        if (Math.abs(value - target) < 1e-4 && Math.abs(velocity) < 1e-3) {
            value = target;
            velocity = 0;
        }

        return value;
    }

    public void snap(double value) {
        this.value = value;
        this.velocity = 0;
    }

    /**
     * Where a spring released from 0 towards 1 is after `time` seconds (closed form, no state needed).
     * Used for open animations, where every window computes its own progress from the screen's open time.
     */
    public static double step(double time, double response, double damping) {
        if (time <= 0) return 0;
        double omega = 2 * Math.PI / Math.max(response, 0.01);
        damping = Math.max(0.1, Math.min(1, damping));
        if (damping >= 0.999) return 1 - (1 + omega * time) * Math.exp(-omega * time);

        double wd = omega * Math.sqrt(1 - damping * damping);
        return 1 - Math.exp(-damping * omega * time) * (Math.cos(wd * time) + damping / Math.sqrt(1 - damping * damping) * Math.sin(wd * time));
    }
}
