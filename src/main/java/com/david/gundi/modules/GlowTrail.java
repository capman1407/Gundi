package com.david.gundi.modules;

import com.david.gundi.Gundi;
import com.david.gundi.util.Colors;
import com.david.gundi.util.ErrorReporter;
import com.david.gundi.util.ThemeLink;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * A soft glowing ribbon that follows behind you and fades out (client-side, only you see it).
 * It's a vertical band through your body that stretches along the path you moved, brightest in the middle
 * (a bright core line plus a transparent glow band), and it fades with age.
 * Looks best in third person (F5) or while flying with an elytra.
 */
public class GlowTrail extends GundiModule {
    private static final String NAME = "glow-trail";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    // Theme colors for the trail (accent -> accent-2) unless rainbow is on. See util/ThemeLink.
    private final ThemeLink link = new ThemeLink(sgColors);

    private final Setting<Double> length = sgGeneral.add(new DoubleSetting.Builder()
        .name("length")
        .description("How many seconds the trail lasts.")
        .defaultValue(1.2)
        .range(0.2, 5)
        .build()
    );

    private final Setting<Double> width = sgGeneral.add(new DoubleSetting.Builder()
        .name("width")
        .description("Height of the ribbon in blocks.")
        .defaultValue(0.6)
        .range(0.05, 2)
        .build()
    );

    private final Setting<Double> heightOffset = sgGeneral.add(new DoubleSetting.Builder()
        .name("height")
        .description("Where on your body the trail sits, in blocks above your feet.")
        .defaultValue(0.9)
        .range(0, 2)
        .build()
    );

    private final Setting<Boolean> onlyFlying = sgGeneral.add(new BoolSetting.Builder()
        .name("only-flying")
        .description("Only leave a trail while elytra flying.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> throughWalls = sgGeneral.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("Draw the trail on top of everything.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> rainbow = sgColors.add(new BoolSetting.Builder()
        .name("rainbow")
        .description("Colors flow along the trail.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> headColor = sgColors.add(new ColorSetting.Builder()
        .name("head-color")
        .description("Color right behind you.")
        .defaultValue(new SettingColor(140, 200, 255, 200))
        .visible(() -> !rainbow.get() && !link.useTheme.get())
        .build()
    );

    private final Setting<SettingColor> tailColor = sgColors.add(new ColorSetting.Builder()
        .name("tail-color")
        .description("Color at the end of the trail (it also fades out).")
        .defaultValue(new SettingColor(191, 90, 242, 200))
        .visible(() -> !rainbow.get() && !link.useTheme.get())
        .build()
    );

    private record Point(Vec3d pos, long nanos) {}

    private final Deque<Point> points = new ArrayDeque<>();

    public GlowTrail() {
        super("glow-trail", "A soft glowing ribbon that follows behind you. Only visible to you.");
    }

    @Override
    public void onActivate() {
        super.onActivate();
        points.clear();
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        points.clear();
    }

    @EventHandler
    private void onRender3d(Render3DEvent event) {
        try {
            if (mc.player == null || mc.world == null) {
                points.clear();
                return;
            }

            long now = System.nanoTime();
            double life = length.get();

            // Record where you are (smoothly between ticks) whenever you've moved a little
            boolean record = !onlyFlying.get() || mc.player.isGliding();
            if (record) {
                float d = event.tickDelta;
                Vec3d pos = new Vec3d(
                    mc.player.lastX + (mc.player.getX() - mc.player.lastX) * d,
                    mc.player.lastY + (mc.player.getY() - mc.player.lastY) * d + heightOffset.get(),
                    mc.player.lastZ + (mc.player.getZ() - mc.player.lastZ) * d
                );
                Point last = points.peekLast();
                if (last == null || last.pos.squaredDistanceTo(pos) > 0.0025) points.addLast(new Point(pos, now));
                // A big jump (teleport, respawn) would draw a long line across the world: start over instead
                if (last != null && last.pos.squaredDistanceTo(pos) > 100) {
                    points.clear();
                    points.addLast(new Point(pos, now));
                }
            }

            // Forget points older than the trail length
            while (!points.isEmpty() && (now - points.peekFirst().nanos) / 1e9 > life) points.removeFirst();
            if (points.size() < 2) return;

            Renderer3D r = throughWalls.get() ? event.renderer : event.depthRenderer;
            double half = width.get() / 2;
            double time = now / 1e9;

            Iterator<Point> it = points.iterator();
            Point prev = it.next();
            int i = 0, n = points.size();
            while (it.hasNext()) {
                Point cur = it.next();

                // Age 0 = newest (right behind you), 1 = about to disappear
                double ageA = Math.min(1, (now - prev.nanos) / 1e9 / life);
                double ageB = Math.min(1, (now - cur.nanos) / 1e9 / life);

                Color ca = color(ageA, (double) i / n, time), cb = color(ageB, (double) (i + 1) / n, time);
                Color glowA = Colors.alpha(ca, 0.35 * (1 - ageA)), glowB = Colors.alpha(cb, 0.35 * (1 - ageB));
                Color clearA = Colors.alpha(ca, 0), clearB = Colors.alpha(cb, 0);

                Vec3d a = prev.pos, b = cur.pos;

                // Glow band: brightest in the middle, fading to clear at the top and bottom edge.
                // Meteor's quad gives its corners colors in the order bottomLeft, topLeft, topRight, bottomRight,
                // so the arguments are (topLeft, topRight, bottomRight, bottomLeft) = (a-end, b-end, b-end, a-end).
                r.quad(a.x, a.y, a.z, a.x, a.y + half, a.z, b.x, b.y + half, b.z, b.x, b.y, b.z, clearA, clearB, glowB, glowA);
                r.quad(a.x, a.y - half, a.z, a.x, a.y, a.z, b.x, b.y, b.z, b.x, b.y - half, b.z, glowA, glowB, clearB, clearA);

                // Bright core line
                r.line(a.x, a.y, a.z, b.x, b.y, b.z, Colors.alpha(ca, 1 - ageA), Colors.alpha(cb, 1 - ageB));

                prev = cur;
                i++;
            }
        } catch (Exception e) {
            ErrorReporter.report(NAME, "render", e);
        }
    }

    /** Head-to-tail color (or rainbow), before fading. */
    private Color color(double age, double along, double time) {
        if (rainbow.get()) {
            float hue = (float) ((time * 0.2 + along * 0.6) % 1.0);
            return Colors.hsv(hue, 0.6, 1, 220);
        }
        Color h = link.accent(headColor), t = link.accent2(tailColor);
        return new Color(
            (int) (h.r + (t.r - h.r) * age),
            (int) (h.g + (t.g - h.g) * age),
            (int) (h.b + (t.b - h.b) * age),
            (int) (h.a + (t.a - h.a) * age)
        );
    }

}
