package com.david.gundi.hud;

import com.david.gundi.Gundi;
import com.david.gundi.util.CrispText;
import com.david.gundi.util.ErrorReporter;
import com.david.gundi.util.ThemeLink;
import com.david.gundi.util.GlassStyle;
import com.david.gundi.util.HudShapes;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.math.Vec3d;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Live speedometer: current speed in blocks/second plus a scrolling graph of the last few seconds.
 *
 * Look:
 * - smooth line (optional 2px thick) that fades from slow-color to fast-color
 * - filled area under the line that fades out towards the bottom
 * - faint grid, gradient background, thin border and an accent bar in the current speed color
 * - the graph's scale glides to new values instead of jumping
 */
public class SpeedGraphHud extends GundiHud {
    public static final HudElementInfo<SpeedGraphHud> INFO = new HudElementInfo<>(Gundi.HUD_GROUP, "speed-graph", "Shows your speed with a live graph.", SpeedGraphHud::new);

    private static final String NAME = "speed-graph";
    private static final int MAX_SECONDS = 30;
    private static final int MAX_SAMPLES = MAX_SECONDS * 20; // HUD ticks 20 times per second
    private static final double PAD = 4;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    // Follow the selected GUI theme (first setting in General). See util/ThemeLink.
    private final ThemeLink link = new ThemeLink(sgGeneral);
    private final SettingGroup sgSize = settings.createGroup("Size");
    private final SettingGroup sgHeader = settings.createGroup("Header");
    private final SettingGroup sgGraph = settings.createGroup("Graph");
    private final SettingGroup sgColors = settings.createGroup("Colors", false);

    // General

    private final Setting<Integer> seconds = sgGeneral.add(new IntSetting.Builder()
        .name("history")
        .description("How many seconds the graph shows.")
        .defaultValue(10)
        .range(2, MAX_SECONDS)
        .build()
    );

    private final Setting<Boolean> horizontalOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("horizontal-only")
        .description("Ignore falling and jumping, only count sideways movement.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> smoothing = sgGeneral.add(new DoubleSetting.Builder()
        .name("smoothing")
        .description("Removes jitter from the line. 0 = raw, higher = smoother but slower to react.")
        .defaultValue(0.5)
        .range(0, 0.9)
        .build()
    );

    private final Setting<Boolean> hideWhenStill = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-when-still")
        .description("Fades the graph out when you stand still, and back in when you move. Always visible in the HUD editor.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> hideDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("hide-delay")
        .description("Seconds of standing still before it fades out.")
        .defaultValue(3)
        .range(0, 30)
        .visible(hideWhenStill::get)
        .build()
    );

    // Look

    private final Setting<Double> scale = sgSize.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Makes the whole thing bigger or smaller (graph, text, lines, everything).")
        .defaultValue(1)
        .range(0.5, 3)
        .build()
    );

    private final Setting<Integer> width = sgSize.add(new IntSetting.Builder()
        .name("width")
        .description("Total width (before scale).")
        .defaultValue(180)
        .range(60, 400)
        .build()
    );

    private final Setting<Integer> height = sgSize.add(new IntSetting.Builder()
        .name("height")
        .description("Graph height (before scale).")
        .defaultValue(60)
        .range(15, 200)
        .build()
    );

    private final Setting<Double> textSize = sgHeader.add(new DoubleSetting.Builder()
        .name("text-size")
        .description("Size of the speed number. 1 = normal Meteor HUD text.")
        .defaultValue(2)
        .range(0.5, 5)
        .build()
    );

    private final Setting<Boolean> thickLine = sgGraph.add(new BoolSetting.Builder()
        .name("thick-line")
        .description("Draws the line 2 pixels thick.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fluidCurve = sgGraph.add(new BoolSetting.Builder()
        .name("fluid-curve")
        .description("Rounds off the line into a smooth flowing curve instead of sharp zig-zags.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fill = sgGraph.add(new BoolSetting.Builder()
        .name("fill")
        .description("Fills the area under the line.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> fillOpacity = sgGraph.add(new IntSetting.Builder()
        .name("fill-opacity")
        .description("How see-through the fill is (0 = invisible, 255 = solid).")
        .defaultValue(90)
        .range(0, 255)
        .visible(fill::get)
        .build()
    );

    private final Setting<Boolean> grid = sgGraph.add(new BoolSetting.Builder()
        .name("grid")
        .description("Faint horizontal guide lines.")
        .defaultValue(true)
        .build()
    );

    public enum Unit { BlocksPerSecond, KmPerHour, Mph }

    private final Setting<Unit> unit = sgGeneral.add(new EnumSetting.Builder<Unit>()
        .name("unit")
        .description("Unit for the speed number.")
        .defaultValue(Unit.BlocksPerSecond)
        .build()
    );

    private final Setting<Boolean> trendArrow = sgHeader.add(new BoolSetting.Builder()
        .name("trend-arrow")
        .description("Green arrow up when speeding up, red arrow down when slowing down.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> liveDot = sgHeader.add(new BoolSetting.Builder()
        .name("live-dot")
        .description("Small pulsing dot in the corner, in the current speed color.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> lineGlow = sgGraph.add(new BoolSetting.Builder()
        .name("line-glow")
        .description("Soft glow along the graph line.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> inset = sgGraph.add(new BoolSetting.Builder()
        .name("graph-inset")
        .description("Draw the graph in a slightly darker rounded area.")
        .defaultValue(true)
        .build()
    );

    // Colors

    private final Setting<SettingColor> slowColor = sgColors.add(new ColorSetting.Builder()
        .name("slow-color")
        .description("Line color when slow.")
        .defaultValue(new SettingColor(70, 230, 160)).visible(link.own())
        .build()
    );

    private final Setting<SettingColor> fastColor = sgColors.add(new ColorSetting.Builder()
        .name("fast-color")
        .description("Line color at the fastest point on the graph.")
        .defaultValue(new SettingColor(255, 80, 110)).visible(link.own())
        .build()
    );

    private final Setting<SettingColor> textColor = sgColors.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Text color.")
        .defaultValue(new SettingColor(255, 255, 255)).visible(link.own())
        .build()
    );

    // Panel: shared glass settings (color, border, glow...). The glow follows the current speed color.
    private final GlassStyle panel = new GlassStyle(settings, link, new GlassStyle.Defaults(
        new SettingColor(16, 17, 22, 185), 12, true, new SettingColor(70, 230, 160, 70)));

    // Data

    // Ring buffer of smoothed speeds, the newest sample sits at head - 1.
    private final double[] samples = new double[MAX_SAMPLES];
    private int head, count;
    private double smoothed;
    // Graph scale that glides towards the real peak so the graph doesn't jump.
    private double displayTop;
    // Highest speed on the graph, worked out each tick. displayTop glides towards it in render().
    private double peakTarget;
    // When the newest sample arrived, for smooth scrolling between samples.
    private long lastSampleNanos = System.nanoTime();
    // The number shown in the header; glides towards the real speed every frame.
    private double displayNumber;

    // Hide-when-still: ticks in a row we've been still, whether we should be hidden,
    // and how visible the graph is right now (0 = hidden, 1 = fully shown).
    private int stillTicks;
    private boolean hiding;
    private double visibility = 1;
    // Visibility used for the frame being drawn (forced to 1 in the HUD editor).
    private double drawAlpha = 1;

    public SpeedGraphHud() {
        super(INFO);
    }

    @Override
    public void tick(HudRenderer renderer) {
        try {
            if (mc.player == null) return;

            Vec3d v = Utils.getPlayerSpeed();
            double raw = horizontalOnly.get() ? Math.hypot(v.x, v.z) : v.length();
            smoothed = smoothed * smoothing.get() + raw * (1 - smoothing.get());

            samples[head] = smoothed;
            head = (head + 1) % MAX_SAMPLES;
            if (count < MAX_SAMPLES) count++;

            lastSampleNanos = System.nanoTime();

            // Only decide the targets here; render() glides towards them every frame.
            double target = 0;
            for (int i = 0; i < visibleCount(); i++) target = Math.max(target, sample(i));
            peakTarget = target;

            // Hide when still. 0.1 b/s threshold so tiny jitter while standing doesn't count as moving.
            if (hideWhenStill.get()) {
                stillTicks = raw < 0.1 ? stillTicks + 1 : 0;
                hiding = stillTicks > hideDelay.get() * 20;
            } else {
                stillTicks = 0;
                hiding = false;
            }
        } catch (Exception e) {
            ErrorReporter.report(NAME, "tick", e);
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        try {
            link.begin();
            // Per-frame animation, based on real time so it looks the same at 30 or 300 FPS.
            // Capped at 0.1s so a lag spike doesn't make things jump.
            double dt = link.dt(renderer);
            displayTop += (peakTarget - displayTop) * (1 - Math.exp(-4.5 * dt));
            if (!hideWhenStill.get()) visibility = 1;
            // Fade out over ~0.5s, fade back in faster (~0.2s) so it pops up as soon as you move.
            else visibility = hiding ? Math.max(0, visibility - 2 * dt) : Math.min(1, visibility + 5 * dt);

            double s = scale.get();
            double w = Math.round(width.get() * s), h = Math.round(height.get() * s);
            double pad = Math.round(PAD * s);
            double numberSize = 18 * textSize.get() * s; // 18px = Meteor's normal HUD text height
            double unitSize = numberSize * 0.6;

            int n = visibleCount();
            double current = n > 0 ? sample(0) : 0;
            // Animated number: covers ~90% of the gap in about 0.2s, so it rolls instead of flickering.
            displayNumber += (current - displayNumber) * (1 - Math.exp(-10 * dt));
            // 15% headroom above the peak, never below 1 so we can't divide by zero.
            double graphMax = Math.max(displayTop * 1.15, 1);

            double textH = CrispText.height(renderer, numberSize);
            double headerH = textH + pad * 2;
            double totalH = headerH + h + pad;

            // Keep the size even while hidden, so the HUD editor box and other elements' positions stay put.
            setSize(w, totalH);

            drawAlpha = isInEditor() ? 1 : visibility;
            if (drawAlpha <= 0) return;

            Color currentColor = speedColor(current / graphMax, 255);

            // Glass panel; its glow takes on the current speed color
            Color glowNow = speedColor(current / graphMax, panel.glowColor.get().a);
            panel.draw(renderer, x, y, w, totalH, s, drawAlpha, glowNow);

            // Header: big number + small dimmer unit (bottoms lined up) + trend arrow
            Color text = fade(link.text(textColor));
            double textX = x + pad + Math.round(3 * s);
            double numEnd = CrispText.draw(renderer, String.format("%.1f", toUnit(displayNumber)), textX, y + pad, text, true, numberSize);
            double unitH = CrispText.height(renderer, unitSize);
            double unitY = y + pad + textH - unitH;
            double unitEnd = CrispText.draw(renderer, " " + unitLabel(), numEnd, unitY, new Color(text.r, text.g, text.b, text.a * 3 / 5), true, unitSize);

            if (trendArrow.get() && n > 20) {
                double change = current - sample(20); // vs one second ago
                double ax = unitEnd + 5 * s, ay = unitY + unitH / 2, a = 3.5 * s;
                if (change > 0.3) HudShapes.tri(renderer, ax, ay - a, ax + a, ay + a * 0.6, ax - a, ay + a * 0.6, fade(link.gain(new Color(48, 209, 88))));
                else if (change < -0.3) HudShapes.tri(renderer, ax, ay + a, ax - a, ay - a * 0.6, ax + a, ay - a * 0.6, fade(link.loss(new Color(255, 69, 58))));
            }

            // Live dot: gently pulsing, top-right
            if (liveDot.get()) {
                double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() / 1e9 * Math.PI * 1.6);
                double dx = x + w - pad - 4 * s, dy0 = y + pad + textH / 2;
                HudShapes.circle(renderer, dx, dy0, 3 * s + 3 * s * pulse, HudShapes.scaleAlpha(currentColor, 0.25 * (1 - pulse)));
                HudShapes.circle(renderer, dx, dy0, 2.6 * s, currentColor);
            }

            // Graph area
            double left = textX, right = x + w - pad;
            double top = y + headerH, bottom = top + h;
            double gw = right - left;

            if (inset.get()) {
                double ip = 3 * s;
                HudShapes.rect(renderer, left - ip, top - ip, gw + ip * 2, h + ip * 2, 7 * s, fade(new Color(0, 0, 0, 55)));
            } else {
                renderer.line(left, top - pad / 2, right, top - pad / 2, fade(link.tile(new Color(255, 255, 255, 20)))); // header separator
            }

            if (grid.get()) {
                Color gridColor = fade(link.tile(new Color(255, 255, 255, 18)));
                for (int k = 1; k <= 3; k++) {
                    double gy = top + h * k / 4;
                    renderer.line(left, gy, right, gy, gridColor);
                }
            }

            // Line thickness grows with scale: 1px thin / 2px thick at scale 1
            int thickness = Math.max(1, (int) Math.round((thickLine.get() ? 2 : 1) * s));

            double step = gw / (seconds.get() * 20 - 1);

            // Smooth scrolling: samples only arrive 20 times a second, so between them we slide every point
            // left by how far we are towards the next sample (0 = just arrived, 1 = next one is due).
            double p = Math.min(1, (System.nanoTime() - lastSampleNanos) / 50_000_000.0);

            if (n > 0) {
                // Short flat piece from the right edge to the newest point, so the line always reaches the edge.
                double v0 = curve(0, n);
                double y0 = yFor(v0, graphMax, top, h);
                segment(renderer, right, y0, right - p * step, y0, v0 / graphMax, bottom, thickness);
            }

            // Fluid curve: split each piece into small sub-pieces about 2px wide (max 8) and bend them
            // along a smooth curve. With 1 sub-piece it's just the plain straight line between two points.
            int subs = fluidCurve.get() ? Math.max(1, Math.min(8, (int) Math.ceil(step / 2))) : 1;

            for (int i = 0; i < n - 1; i++) {
                double xa = right - (i + p) * step;
                if (xa <= left) break; // scrolled out of the box

                // The point before, the two ends of this piece, and the point after: the curve needs all four.
                double c0 = curve(i - 1, n), c1 = curve(i, n), c2 = curve(i + 1, n), c3 = curve(i + 2, n);

                for (int j = 0; j < subs; j++) {
                    double u1 = (double) j / subs, u2 = (double) (j + 1) / subs;
                    double x1 = xa - u1 * step, x2 = xa - u2 * step;
                    if (x1 <= left) break;

                    double v1 = catmullRom(c0, c1, c2, c3, u1), v2 = catmullRom(c0, c1, c2, c3, u2);
                    double y1 = yFor(v1, graphMax, top, h), y2 = yFor(v2, graphMax, top, h);
                    if (x2 < left) {
                        // Oldest piece is half outside the box: cut it at the left edge.
                        y2 = y1 + (y2 - y1) * (x1 - left) / (x1 - x2);
                        x2 = left;
                    }
                    segment(renderer, x1, y1, x2, y2, v1 / graphMax, bottom, thickness);
                }
            }

            // Dot on the newest point, which sits on the right edge
            if (n > 0) {
                double dy = yFor(curve(0, n), graphMax, top, h);
                double dot = 3.5 * s;
                renderer.quad(right - dot / 2, dy - dot / 2, dot, dot, currentColor);
            }
        } catch (Exception e) {
            ErrorReporter.report(NAME, "render", e);
        }
    }

    /** One piece of the graph: the line from (xa, ya) to (xb, yb) plus the filled area under it. t = 0 slow, 1 fast. */
    private void segment(HudRenderer renderer, double xa, double ya, double xb, double yb, double t, double bottom, int thickness) {
        if (xa - xb <= 0) return;

        if (fill.get()) {
            Color fillTop = speedColor(t, fillOpacity.get());
            Color fillBottom = speedColor(t, 0);
            double lower = Math.max(ya, yb); // the point closer to the bottom of the screen

            // Slanted part between the two points
            if (ya > yb) HudShapes.tri(renderer, xa, ya, xb, yb, xb, ya, fillTop);
            else if (yb > ya) HudShapes.tri(renderer, xa, ya, xb, yb, xa, yb, fillTop);
            // Straight part underneath, fading out towards the bottom
            renderer.quad(xb, lower, xa - xb, bottom - lower, fillTop, fillTop, fillBottom, fillBottom);
        }

        Color lineColor = speedColor(t, 255);
        if (lineGlow.get()) {
            Color soft = speedColor(t, 70);
            renderer.line(xa, ya - 1, xb, yb - 1, soft);
            renderer.line(xa, ya + thickness, xb, yb + thickness, soft);
        }
        for (int k = 0; k < thickness; k++) renderer.line(xa, ya + k, xb, yb + k, lineColor);
    }

    /** Speed in blocks per second -> the chosen unit. */
    private double toUnit(double bps) {
        return switch (unit.get()) {
            case BlocksPerSecond -> bps;
            case KmPerHour -> bps * 3.6;
            case Mph -> bps * 2.23694;
        };
    }

    private String unitLabel() {
        return switch (unit.get()) {
            case BlocksPerSecond -> "b/s";
            case KmPerHour -> "km/h";
            case Mph -> "mph";
        };
    }

    /**
     * The value the graph draws at point i. With fluid-curve on it's a soft blend of the point and its
     * 2 neighbours on each side (weights 1-4-6-4-1), which removes zig-zag without shifting the shape.
     * Points past either end reuse the end value.
     */
    private double curve(int i, int n) {
        if (!fluidCurve.get()) return sample(clamp(i, 0, n - 1));

        double sum = 0;
        int[] weights = {1, 4, 6, 4, 1};
        for (int k = -2; k <= 2; k++) sum += weights[k + 2] * sample(clamp(i + k, 0, n - 1));
        return sum / 16;
    }

    /**
     * Smooth curve through p1 -> p2 (u = 0 gives p1, u = 1 gives p2); p0 and p3 are the neighbours that
     * decide how it bends. Standard Catmull-Rom formula.
     */
    private static double catmullRom(double p0, double p1, double p2, double p3, double u) {
        double u2 = u * u, u3 = u2 * u;
        return 0.5 * (2 * p1
            + (-p0 + p2) * u
            + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2
            + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private int visibleCount() {
        return Math.min(count, seconds.get() * 20);
    }

    /** i = 0 is the newest sample, i = 1 the one before, ... */
    private double sample(int i) {
        return samples[Math.floorMod(head - 1 - i, MAX_SAMPLES)];
    }

    /**
     * Screen Y for a speed. Clamped to the box: a sudden new peak can't draw above it while the scale catches up,
     * and the curve's slight overshoot can't dip below the bottom.
     */
    private double yFor(double speed, double scale, double top, double h) {
        double t = Math.max(0, Math.min(speed / scale, 1));
        return top + h - t * h;
    }

    /** Color between slow-color and fast-color. t = 0 is slow, 1 is fast. */
    private Color speedColor(double t, int alpha) {
        t = Math.max(0, Math.min(1, t));
        Color s = link.accent(slowColor), f = link.accent2(fastColor);
        return new Color(
            (int) (s.r + (f.r - s.r) * t),
            (int) (s.g + (f.g - s.g) * t),
            (int) (s.b + (f.b - s.b) * t),
            (int) (Math.max(0, Math.min(255, alpha)) * drawAlpha)
        );
    }

    /** Copy of a color with its alpha multiplied by the current hide-when-still fade. */
    private Color fade(Color c) {
        return new Color(c.r, c.g, c.b, (int) (c.a * drawAlpha));
    }

}
