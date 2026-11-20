package com.david.gundi.modules;

import com.david.gundi.Gundi;
import com.david.gundi.util.Colors;
import com.david.gundi.util.ErrorReporter;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Cosmetic angel wings on your own player (client-side, only you see them).
 *
 * How it's built:
 * - Each wing is a fan of feathers. A feather goes from the shoulder outwards and is cut into a few pieces along
 *   its length, so colors (and the shimmer highlight) can change smoothly from root to tip.
 * - Feathers are laid out flat in "wing space" (s = out along the wing, u = up), the wing is swung backwards by the
 *   open angle, and then placed on your body using 3 directions: right, back and up.
 * - Hidden while elytra flying or swimming, because your body lies flat then.
 * - Animations (flap, air spread, sweep back, unfold/fold) all run per frame on real time.
 */
public class Wings extends GundiModule {
    private static final String NAME = "wings";

    // Angel feather layout. f goes 0 (top feather) to 1 (bottom feather).
    private static final double ANGLE_TOP = 75, ANGLE_BOTTOM = -35;   // degrees in the flat wing: 90 = up, 0 = out
    private static final double LENGTH_TOP = 1.25, LENGTH_BOTTOM = 0.70;
    private static final double ROOT_WIDTH = 0.10, TIP_WIDTH = 0.22;
    private static final int PIECES = 6; // pieces per feather along its length

    public enum Particle {
        Sparkle(ParticleTypes.END_ROD),
        Glow(ParticleTypes.GLOW),
        Spark(ParticleTypes.ELECTRIC_SPARK),
        Firework(ParticleTypes.FIREWORK),
        Snow(ParticleTypes.SNOWFLAKE),
        Cherry(ParticleTypes.CHERRY_LEAVES);

        final SimpleParticleType type;

        Particle(SimpleParticleType type) {
            this.type = type;
        }
    }

    private final SettingGroup sgShape = settings.getDefaultGroup();
    private final SettingGroup sgPosition = settings.createGroup("Position", false);
    private final SettingGroup sgMotion = settings.createGroup("Motion");
    private final SettingGroup sgEffects = settings.createGroup("Effects");
    private final SettingGroup sgColors = settings.createGroup("Colors", false);

    // Shape

    private final Setting<Double> size = sgShape.add(new DoubleSetting.Builder()
        .name("size")
        .description("How big the wings are.")
        .defaultValue(1)
        .range(0.3, 3)
        .build()
    );

    private final Setting<Integer> feathers = sgShape.add(new IntSetting.Builder()
        .name("feathers")
        .description("How many feathers each wing has.")
        .defaultValue(8)
        .range(3, 16)
        .build()
    );

    private final Setting<Double> openAngle = sgShape.add(new DoubleSetting.Builder()
        .name("open-angle")
        .description("How far the wings are spread. 0 = folded flat behind you, 90 = straight out to the sides.")
        .defaultValue(45)
        .range(0, 90)
        .build()
    );

    private final Setting<Boolean> outline = sgShape.add(new BoolSetting.Builder()
        .name("outline")
        .description("Draws a thin outline around each feather.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> throughWalls = sgShape.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("Draw the wings on top of everything, even your own body and blocks.")
        .defaultValue(false)
        .build()
    );

    // Position

    private final Setting<Double> height = sgPosition.add(new DoubleSetting.Builder()
        .name("height")
        .description("Moves the wings up or down your back, in blocks. 0 = shoulder blades.")
        .defaultValue(0)
        .range(-0.8, 0.8)
        .build()
    );

    private final Setting<Double> backDistance = sgPosition.add(new DoubleSetting.Builder()
        .name("back-distance")
        .description("Moves the wings further away from (or closer to) your back, in blocks.")
        .defaultValue(0)
        .range(-0.2, 0.6)
        .build()
    );

    private final Setting<Double> shoulderGap = sgPosition.add(new DoubleSetting.Builder()
        .name("shoulder-gap")
        .description("Space between the left and right wing at your back, in blocks.")
        .defaultValue(0.24)
        .range(0, 0.8)
        .build()
    );

    // Motion

    private final Setting<Double> flapSpeed = sgMotion.add(new DoubleSetting.Builder()
        .name("flap-speed")
        .description("How fast the wings flap while on the ground. 0 = still.")
        .defaultValue(1)
        .range(0, 5)
        .build()
    );

    private final Setting<Double> flapAmount = sgMotion.add(new DoubleSetting.Builder()
        .name("flap-amount")
        .description("How far the wings swing when flapping, in degrees.")
        .defaultValue(12)
        .range(0, 45)
        .build()
    );

    private final Setting<Boolean> airFlap = sgMotion.add(new BoolSetting.Builder()
        .name("air-flap")
        .description("Flap faster and wider while you're in the air.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> airSpread = sgMotion.add(new DoubleSetting.Builder()
        .name("air-spread")
        .description("How many extra degrees the wings open while you're in the air.")
        .defaultValue(20)
        .range(0, 45)
        .build()
    );

    private final Setting<Boolean> sweepBack = sgMotion.add(new BoolSetting.Builder()
        .name("sweep-back")
        .description("Wings fold back the faster you run, like they're catching the wind.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> jumpFlap = sgMotion.add(new BoolSetting.Builder()
        .name("jump-flap")
        .description("One big wing beat when you jump.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> unfold = sgMotion.add(new BoolSetting.Builder()
        .name("unfold-on-enable")
        .description("Wings unfold with an animation when you turn the module on.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> foldOnDisable = sgMotion.add(new BoolSetting.Builder()
        .name("fold-on-disable")
        .description("Wings fold away with an animation when you turn the module off.")
        .defaultValue(true)
        .build()
    );

    // Effects

    private final Setting<Boolean> glow = sgEffects.add(new BoolSetting.Builder()
        .name("glow")
        .description("Soft glow around every feather.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> glowStrength = sgEffects.add(new DoubleSetting.Builder()
        .name("glow-strength")
        .description("How bright the glow is.")
        .defaultValue(1)
        .range(0.1, 3)
        .visible(glow::get)
        .build()
    );

    private final Setting<Boolean> shimmer = sgEffects.add(new BoolSetting.Builder()
        .name("shimmer")
        .description("A bright highlight that runs along the feathers every few seconds.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> shimmerInterval = sgEffects.add(new DoubleSetting.Builder()
        .name("shimmer-interval")
        .description("Seconds between shimmers.")
        .defaultValue(3)
        .range(1, 15)
        .visible(shimmer::get)
        .build()
    );

    private final Setting<Double> shimmerStrength = sgEffects.add(new DoubleSetting.Builder()
        .name("shimmer-strength")
        .description("How bright the shimmer is.")
        .defaultValue(0.8)
        .range(0.1, 1)
        .visible(shimmer::get)
        .build()
    );

    private final Setting<Boolean> particles = sgEffects.add(new BoolSetting.Builder()
        .name("particles")
        .description("Small particles drifting off the wing tips (only while you can see the wings).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Particle> particleType = sgEffects.add(new EnumSetting.Builder<Particle>()
        .name("particle-type")
        .description("Which particle falls from the wings.")
        .defaultValue(Particle.Sparkle)
        .visible(particles::get)
        .build()
    );

    private final Setting<Double> particleRate = sgEffects.add(new DoubleSetting.Builder()
        .name("particle-rate")
        .description("Particles per second.")
        .defaultValue(4)
        .range(0.5, 30)
        .visible(particles::get)
        .build()
    );

    // Colors

    private final Setting<SettingColor> rootColor = sgColors.add(new ColorSetting.Builder()
        .name("root-color")
        .description("Color at the shoulder end of the feathers.")
        .defaultValue(new SettingColor(255, 255, 255, 200))
        .build()
    );

    private final Setting<SettingColor> tipColor = sgColors.add(new ColorSetting.Builder()
        .name("tip-color")
        .description("Color at the outer end of the feathers.")
        .defaultValue(new SettingColor(140, 200, 255, 110))
        .build()
    );

    private final Setting<SettingColor> outlineColor = sgColors.add(new ColorSetting.Builder()
        .name("outline-color")
        .description("Color of the feather outlines.")
        .defaultValue(new SettingColor(255, 255, 255, 160))
        .visible(outline::get)
        .build()
    );

    private final Setting<Boolean> rainbow = sgColors.add(new BoolSetting.Builder()
        .name("rainbow")
        .description("Animated rainbow colors instead of root/tip colors (keeps their transparency).")
        .defaultValue(false)
        .build()
    );

    // Animation state, updated every frame
    private double currentExtraOpen;   // air spread minus sweep back, glides towards its target
    private double unfoldProgress = 1; // 0 = folded away, 1 = fully out
    private boolean wasOnGround = true;
    private double jumpPhase = 1;      // 0 = jump flap just started, 1 = finished

    // Fold-away: Meteor stops sending this module events the moment it's turned off,
    // so a separate little listener keeps drawing the wings until they've folded away.
    private final FoldListener foldListener = new FoldListener();
    private boolean folding;

    // Particles: wing tip positions from the last frame, and when that frame was drawn.
    private final List<Vec3d> tips = new ArrayList<>();
    private long lastDrawnNanos;
    private double particleBudget;
    private final Random random = new Random();

    public Wings() {
        super("wings", "Cosmetic angel wings on your back. Only visible to you, in third person.");
    }

    @Override
    public void onActivate() {
        super.onActivate();
        stopFolding();
        // If it was halfway folding away, unfold from there instead of jumping.
        if (unfold.get()) {
            if (unfoldProgress >= 1) unfoldProgress = 0;
        } else {
            unfoldProgress = 1;
        }
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        if (foldOnDisable.get() && mc.player != null && unfoldProgress > 0) {
            folding = true;
            MeteorClient.EVENT_BUS.subscribe(foldListener);
        } else {
            unfoldProgress = 1;
        }
    }

    private void stopFolding() {
        if (folding) {
            MeteorClient.EVENT_BUS.unsubscribe(foldListener);
            folding = false;
        }
    }

    @EventHandler
    private void onRender3d(Render3DEvent event) {
        try {
            double dt = frameDt(event);
            unfoldProgress = Math.min(1, unfoldProgress + dt / 0.7);
            drawWings(event, dt);
        } catch (Exception e) {
            ErrorReporter.report(NAME, "render", e);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        try {
            spawnParticles();
        } catch (Exception e) {
            ErrorReporter.report(NAME, "particles", e);
        }
    }

    /** Only subscribed while folding away after the module was turned off. */
    private class FoldListener {
        @EventHandler
        private void onRender3d(Render3DEvent event) {
            try {
                if (mc.player == null || mc.world == null) {
                    finishFold();
                    return;
                }

                double dt = frameDt(event);
                unfoldProgress = Math.max(0, unfoldProgress - dt / 0.5);
                drawWings(event, dt);

                if (unfoldProgress <= 0) finishFold();
            } catch (Exception e) {
                ErrorReporter.report(NAME, "fold-away", e);
                finishFold();
            }
        }

        private void finishFold() {
            stopFolding();
            unfoldProgress = 1; // ready for next time
        }
    }

    private static double frameDt(Render3DEvent event) {
        return Math.min(Math.max(event.frameTime, 0), 0.1); // seconds since last frame, capped for lag spikes
    }

    /** Draws both wings with the current animation state. Shared by the module and the fold-away listener. */
    private void drawWings(Render3DEvent event, double dt) {
        tips.clear();
        if (mc.player == null || mc.world == null) return;

        float delta = event.tickDelta;

        // Extra opening: spread in the air, sweep back when running. Glides there instead of snapping.
        boolean onGround = mc.player.isOnGround();
        double target = 0;
        if (!onGround) target += airSpread.get();
        if (sweepBack.get() && onGround) {
            Vec3d v = Utils.getPlayerSpeed();
            double runSpeed = Math.hypot(v.x, v.z); // blocks per second, sprinting is ~5.6
            target -= Math.min(runSpeed / 8, 1) * 25;
        }
        currentExtraOpen += (target - currentExtraOpen) * (1 - Math.exp(-6 * dt));

        // Jump flap: the moment you leave the ground going up, play one big beat over ~0.45s.
        if (jumpFlap.get() && wasOnGround && !onGround && mc.player.getVelocity().y > 0.1) jumpPhase = 0;
        wasOnGround = onGround;
        jumpPhase = Math.min(1, jumpPhase + dt / 0.45);
        double jumpExtra = jumpFlap.get() ? 30 * Math.sin(Math.PI * jumpPhase) : 0; // opens wide, then settles

        // All animations above keep running even when nothing is drawn, so nothing jumps when it shows again.
        // You can't see your own back in first person, and the wings would only get in the way.
        if (mc.options.getPerspective().isFirstPerson()) return;
        // Elytra flying and swimming lay your body flat; these wings are built for an upright body, so hide them.
        if (mc.player.isGliding() || mc.player.isSwimming()) return;
        if (unfoldProgress <= 0) return;

        double unfoldEase = 1 - Math.pow(1 - unfoldProgress, 3); // eased so it slows down at the end

        // Smooth position + body direction between ticks, so the wings don't stutter when you move/turn.
        Vec3d feet = new Vec3d(
            lerp(mc.player.lastX, mc.player.getX(), delta),
            lerp(mc.player.lastY, mc.player.getY(), delta),
            lerp(mc.player.lastZ, mc.player.getZ(), delta)
        );
        double bodyYaw = Math.toRadians(mc.player.lastBodyYaw + MathHelper.wrapDegrees(mc.player.bodyYaw - mc.player.lastBodyYaw) * delta);

        // Directions relative to your body. Minecraft yaw 0 = facing +Z (south).
        Vec3d right = new Vec3d(-Math.cos(bodyYaw), 0, -Math.sin(bodyYaw));
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d back = new Vec3d(Math.sin(bodyYaw), 0, -Math.cos(bodyYaw));

        // Shoulder blades, measured along your body. Sneaking leans you forward: a bit lower and further back.
        boolean sneaking = mc.player.isSneaking();
        double alongBody = (sneaking ? 1.1 : 1.35) + height.get();
        double outOfBack = (sneaking ? 0.32 : 0.16) + backDistance.get();
        Vec3d shoulders = feet.add(up.multiply(alongBody)).add(back.multiply(outOfBack));

        // Flapping
        boolean fastFlap = !onGround && airFlap.get();
        double time = (mc.player.age + delta) / 20.0; // seconds
        double speed = flapSpeed.get() * (fastFlap ? 2.5 : 1);
        double amount = flapAmount.get() * (fastFlap ? 1.6 : 1);

        double sizeNow = size.get() * (0.25 + 0.75 * unfoldEase); // smaller while (un)folding
        Renderer3D r = throughWalls.get() ? event.renderer : event.depthRenderer;

        for (int side = -1; side <= 1; side += 2) { // -1 = left wing, +1 = right wing
            Frame frame = new Frame(side, shoulders.add(right.multiply(shoulderGap.get() / 2 * side)), right, back, up);

            int count = feathers.get();
            for (int k = 0; k < count; k++) {
                double f = count == 1 ? 0 : (double) k / (count - 1); // 0 = top feather, 1 = bottom feather

                // Lower feathers lag behind a little, so the flap ripples down the wing.
                double flap = amount * Math.sin(time * speed * Math.PI * 2 - f * 0.9);
                double openDeg = openAngle.get() + currentExtraOpen + flap + jumpExtra;
                double open = Math.toRadians(MathHelper.clamp(openDeg, -10, 120) * unfoldEase);

                Feather feather = new Feather(
                    Math.toRadians(ANGLE_TOP + f * (ANGLE_BOTTOM - ANGLE_TOP)),
                    0.05 * sizeNow, // feathers begin just off the shoulder, not all on one point
                    sizeNow * (LENGTH_TOP + (LENGTH_BOTTOM - LENGTH_TOP) * f),
                    ROOT_WIDTH * sizeNow,
                    TIP_WIDTH * sizeNow
                );

                Color root = color(rootColor.get(), f, time, 0);
                Color tip = color(tipColor.get(), f, time, 0.15);

                // Glow first, so the solid feather is drawn on top of it.
                if (glow.get()) {
                    double gs = glowStrength.get();
                    Feather big = new Feather(feather.angle, feather.start, feather.length * 1.08,
                        feather.rootW * 2.2, feather.tipW * 2.0 + 0.05 * sizeNow);
                    drawFeather(r, frame, open, big, Colors.withAlpha(root, root.a * 0.25 * gs), Colors.withAlpha(tip, tip.a * 0.45 * gs), null, f, time);
                }

                drawFeather(r, frame, open, feather, root, tip, outline.get() ? outlineColor.get() : null, f, time);

                // Remember the tip for particles
                double[] t = frame.toWorld(Math.cos(feather.angle) * feather.length, Math.sin(feather.angle) * feather.length, open);
                tips.add(new Vec3d(t[0], t[1], t[2]));
            }
        }

        lastDrawnNanos = System.nanoTime();
    }

    /** Draws one feather in PIECES pieces along its length, plus optionally its outline. */
    private void drawFeather(Renderer3D r, Frame frame, double open, Feather fe, Color root, Color tip,
                             Color outlineC, double f, double time) {
        double dirS = Math.cos(fe.angle), dirU = Math.sin(fe.angle);   // along the feather
        double normS = -Math.sin(fe.angle), normU = Math.cos(fe.angle); // across the feather

        double[] prevUpper = null, prevLower = null;
        Color prevColor = null;

        for (int i = 0; i <= PIECES; i++) {
            double t = (double) i / PIECES; // 0 = root, 1 = tip
            double along = fe.start + (fe.length - fe.start) * t;
            double w = fe.rootW + (fe.tipW - fe.rootW) * t;

            double[] upper = frame.toWorld(dirS * along + normS * w / 2, dirU * along + normU * w / 2, open);
            double[] lower = frame.toWorld(dirS * along - normS * w / 2, dirU * along - normU * w / 2, open);
            Color c = shimmer(Colors.mix(root, tip, t), t, f, time);

            if (prevUpper != null) {
                // Meteor's quad(...) colors, in its order: topLeft, topRight, bottomRight, bottomLeft.
                // It gives corner 1 bottomLeft, 2 topLeft, 3 topRight, 4 bottomRight,
                // so this piece's outer end = "top", its inner end = "bottom".
                r.quad(prevUpper[0], prevUpper[1], prevUpper[2], upper[0], upper[1], upper[2],
                    lower[0], lower[1], lower[2], prevLower[0], prevLower[1], prevLower[2],
                    c, c, prevColor, prevColor);

                if (outlineC != null) {
                    r.line(prevUpper[0], prevUpper[1], prevUpper[2], upper[0], upper[1], upper[2], outlineC);
                    r.line(prevLower[0], prevLower[1], prevLower[2], lower[0], lower[1], lower[2], outlineC);
                }
            }

            prevUpper = upper;
            prevLower = lower;
            prevColor = c;
        }

        if (outlineC != null) { // across the tip
            r.line(prevUpper[0], prevUpper[1], prevUpper[2], prevLower[0], prevLower[1], prevLower[2], outlineC);
        }
    }

    /** Brightens a color where the shimmer band currently is. t = 0 root .. 1 tip, f = which feather. */
    private Color shimmer(Color c, double t, double f, double time) {
        if (!shimmer.get()) return c;

        // The band sweeps from just before the root to just past the tip in 0.9s, then waits for the next one.
        // Top feathers go first, so it ripples down the wing.
        double sweep = (time % shimmerInterval.get()) / 0.9;
        double bandPos = sweep * 1.4 - 0.2 - f * 0.25;
        double d = (t - bandPos) / 0.15;
        double h = Math.exp(-d * d) * shimmerStrength.get(); // 0 = no highlight, 1 = full white

        return new Color(
            (int) (c.r + (255 - c.r) * h),
            (int) (c.g + (255 - c.g) * h),
            (int) (c.b + (255 - c.b) * h),
            (int) (c.a + (255 - c.a) * h * 0.5)
        );
    }

    private void spawnParticles() {
        if (!particles.get() || mc.world == null || mc.player == null) return;
        // Only while the wings are actually being drawn (not in first person, not hidden).
        if (System.nanoTime() - lastDrawnNanos > 150_000_000L || tips.isEmpty()) {
            particleBudget = 0;
            return;
        }

        particleBudget += particleRate.get() / 20.0; // 20 ticks per second
        while (particleBudget >= 1) {
            particleBudget--;
            Vec3d tip = tips.get(random.nextInt(tips.size()));
            mc.world.addParticleClient(particleType.get().type,
                tip.x + (random.nextDouble() - 0.5) * 0.1,
                tip.y + (random.nextDouble() - 0.5) * 0.1,
                tip.z + (random.nextDouble() - 0.5) * 0.1,
                (random.nextDouble() - 0.5) * 0.02,
                -0.02 - random.nextDouble() * 0.02, // drift down
                (random.nextDouble() - 0.5) * 0.02
            );
        }
    }

    /** One feather's measurements: direction in the flat wing, where it starts/ends, and its width at root/tip. */
    private record Feather(double angle, double start, double length, double rootW, double tipW) {}

    /**
     * Where one wing sits in the world: its shoulder point and the body's right/back/up directions.
     * toWorld() swings a flat wing-space point backwards by the open angle and places it on the body.
     */
    private record Frame(int side, Vec3d root, Vec3d right, Vec3d back, Vec3d up) {
        /** s = out along the wing, u = up along the body. open = 0 lies flat behind you, 90 deg points sideways. */
        double[] toWorld(double s, double u, double open) {
            double sideways = s * Math.sin(open) * side;
            double behind = s * Math.cos(open);
            return new double[] {
                root.x + right.x * sideways + back.x * behind + up.x * u,
                root.y + right.y * sideways + back.y * behind + up.y * u,
                root.z + right.z * sideways + back.z * behind + up.z * u
            };
        }
    }

    /** The setting's color, or (with rainbow on) a moving rainbow color that keeps the setting's transparency. */
    private Color color(Color base, double f, double time, double hueOffset) {
        if (!rainbow.get()) return base;

        float hue = (float) ((time * 0.15 + f * 0.35 + hueOffset) % 1.0);
        return Colors.hsv(hue, 0.65, 1, base.a);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
