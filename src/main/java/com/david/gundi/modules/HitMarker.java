package com.david.gundi.modules;

import com.david.gundi.Gundi;
import com.david.gundi.util.CombatTracker;
import com.david.gundi.util.Colors;
import com.david.gundi.util.ErrorReporter;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

/**
 * Hit marker: four short strokes flash around your crosshair when you hit something.
 * - Normal hit: white. Critical hit: gold and a bit bigger. Kill: red, bigger, stays longer.
 * - Pops in slightly too big and settles, then fades out.
 *
 * Hits and kills come from util/CombatTracker. Drawn in Meteor's 2D render event, where coordinates are
 * window pixels, so the center of the screen is exactly where the crosshair is.
 */
public class HitMarker extends GundiModule {
    private static final String NAME = "hit-marker";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors", false);

    private final Setting<Double> size = sgGeneral.add(new DoubleSetting.Builder()
        .name("size")
        .description("Size of the marker.")
        .defaultValue(1)
        .range(0.3, 3)
        .build()
    );

    private final Setting<Double> duration = sgGeneral.add(new DoubleSetting.Builder()
        .name("duration")
        .description("Seconds a normal hit marker stays visible (kill markers stay twice as long).")
        .defaultValue(0.35)
        .range(0.1, 2)
        .build()
    );

    private final Setting<Boolean> crits = sgGeneral.add(new BoolSetting.Builder()
        .name("crit-marker")
        .description("Gold, slightly bigger marker for critical hits.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> kills = sgGeneral.add(new BoolSetting.Builder()
        .name("kill-marker")
        .description("Big red marker when something you hit dies.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> hitColor = sgColors.add(new ColorSetting.Builder()
        .name("hit-color")
        .description("Normal hit.")
        .defaultValue(new SettingColor(255, 255, 255, 230))
        .build()
    );

    private final Setting<SettingColor> critColor = sgColors.add(new ColorSetting.Builder()
        .name("crit-color")
        .description("Critical hit.")
        .defaultValue(new SettingColor(255, 214, 10, 240))
        .visible(crits::get)
        .build()
    );

    private final Setting<SettingColor> killColor = sgColors.add(new ColorSetting.Builder()
        .name("kill-color")
        .description("Kill.")
        .defaultValue(new SettingColor(255, 59, 48, 245))
        .visible(kills::get)
        .build()
    );

    public HitMarker() {
        super("hit-marker", "Shooter-style hit marker on your crosshair.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        try {
            if (mc.player == null || mc.options.hudHidden) return; // F1 hides the crosshair, so hide this too

            CombatTracker ct = CombatTracker.INSTANCE;
            long now = System.nanoTime();
            double s = size.get();
            double age, life;
            Color color;

            boolean kill = kills.get() && ct.lastKillNanos > 0 && (now - ct.lastKillNanos) / 1e9 < duration.get() * 2;
            if (kill) {
                age = (now - ct.lastKillNanos) / 1e9;
                life = duration.get() * 2;
                color = killColor.get();
                s *= 1.5;
            } else {
                if (ct.lastHitNanos < 0) return;
                age = (now - ct.lastHitNanos) / 1e9;
                life = duration.get();
                boolean crit = crits.get() && ct.lastHitCrit;
                color = crit ? critColor.get() : hitColor.get();
                if (crit) s *= 1.2;
            }

            double t = age / life;
            if (t >= 1) return;

            // Pop: starts 35% too big and settles quickly; fades out over the second half.
            double pop = 1 + 0.35 * Math.exp(-age * 18);
            double alpha = t < 0.5 ? 1 : 1 - (t - 0.5) / 0.5;

            Renderer2D.COLOR.begin();
            drawMarker(Utils.getWindowWidth() / 2.0, Utils.getWindowHeight() / 2.0, s * pop, alpha, color);
            Renderer2D.COLOR.render();
        } catch (Exception e) {
            ErrorReporter.report(NAME, "render", e);
        }
    }

    /** Four diagonal strokes around (cx, cy), leaving the middle free so the crosshair stays visible. */
    private static void drawMarker(double cx, double cy, double s, double alpha, Color color) {
        Color c = Colors.alpha(color, alpha);
        Color soft = Colors.alpha(color, alpha * 0.35);
        double inner = 5 * s, outer = 12 * s;

        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dy = -1; dy <= 1; dy += 2) {
                double x1 = cx + dx * inner, y1 = cy + dy * inner;
                double x2 = cx + dx * outer, y2 = cy + dy * outer;
                // 2px main stroke with a soft edge on each side (lines are 1px)
                Renderer2D.COLOR.line(x1, y1, x2, y2, c);
                Renderer2D.COLOR.line(x1 + 1, y1, x2 + 1, y2, c);
                Renderer2D.COLOR.line(x1 - 1, y1, x2 - 1, y2, soft);
                Renderer2D.COLOR.line(x1 + 2, y1, x2 + 2, y2, soft);
            }
        }
    }

}
