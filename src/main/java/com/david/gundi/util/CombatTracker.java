package com.david.gundi.util;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Remembers your recent hits so HUD elements (like the hit marker) can react to them.
 * Subscribed once to Meteor's event bus in Gundi.
 */
public final class CombatTracker {
    public static final CombatTracker INSTANCE = new CombatTracker();

    /** System.nanoTime() of your last hit, -1 = none yet. */
    public long lastHitNanos = -1;
    /** Whether the last hit was a critical (falling, not on ground). */
    public boolean lastHitCrit;
    /** System.nanoTime() when something you hit died shortly after, -1 = none yet. */
    public long lastKillNanos = -1;

    private Entity lastTarget;
    private int ticksSinceHit;

    private CombatTracker() {}

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        try {
            if (mc.player == null) return;
            lastHitNanos = System.nanoTime();
            lastHitCrit = mc.player.fallDistance > 0 && !mc.player.isOnGround() && !mc.player.isClimbing() && !mc.player.isTouchingWater();
            lastTarget = event.entity;
            ticksSinceHit = 0;
        } catch (Exception e) {
            ErrorReporter.report("combat-tracker", "attack", e);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        try {
            if (lastTarget == null) return;

            // Counts as a kill if what you hit dies within 1 second (20 ticks) of your hit.
            if (lastTarget instanceof LivingEntity living && (living.isDead() || living.getHealth() <= 0)) {
                lastKillNanos = System.nanoTime();
                lastTarget = null;
                return;
            }

            if (++ticksSinceHit > 20) lastTarget = null;
        } catch (Exception e) {
            ErrorReporter.report("combat-tracker", "tick", e);
        }
    }
}
