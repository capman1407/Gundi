package com.david.gundi.hud;

import com.david.gundi.Gundi;
import com.david.gundi.gui.Spring;
import com.david.gundi.util.CrispText;
import com.david.gundi.util.ErrorReporter;
import com.david.gundi.util.ThemeLink;
import com.david.gundi.util.HudShapes;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * iPhone "Dynamic Island" for module toggles: a black pill that smoothly expands to show
 * "● Wings   On" when you turn a module on/off, then shrinks back.
 *
 * Place it at the top-center of your screen in the HUD editor. The island is always drawn centered inside
 * the element's box, so it grows outwards from the middle like the real one.
 */
public class DynamicIslandHud extends GundiHud {
    public static final HudElementInfo<DynamicIslandHud> INFO = new HudElementInfo<>(Gundi.HUD_GROUP, "dynamic-island", "iPhone-style pill that shows module toggles.", DynamicIslandHud::new);

    private static final String NAME = "dynamic-island";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    // Follow the selected GUI theme (first setting in General). See util/ThemeLink.
    private final ThemeLink link = new ThemeLink(sgGeneral);
    private final SettingGroup sgLook = settings.createGroup("Style");
    private final SettingGroup sgColors = settings.createGroup("Colors", false);

    // General

    private final Setting<Boolean> showEnable = sgGeneral.add(new BoolSetting.Builder()
        .name("show-enable")
        .description("Show when a module is turned on.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("show-disable")
        .description("Show when a module is turned off.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> duration = sgGeneral.add(new DoubleSetting.Builder()
        .name("duration")
        .description("How many seconds a notification stays open.")
        .defaultValue(2)
        .range(0.5, 8)
        .build()
    );

    private final Setting<Boolean> idlePill = sgGeneral.add(new BoolSetting.Builder()
        .name("idle-pill")
        .description("Keep a small pill visible when there's nothing to show (like the real one). Off = fully hidden.")
        .defaultValue(false)
        .build()
    );

    // Look

    private final Setting<Double> scale = sgLook.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Size of the island.")
        .defaultValue(1)
        .range(0.5, 3)
        .build()
    );

    private final Setting<SettingColor> islandColor = sgColors.add(new ColorSetting.Builder()
        .name("island-color")
        .description("The pill's color. The real one is pure black.")
        .defaultValue(new SettingColor(0, 0, 0, 235)).visible(link.own())
        .build()
    );

    private final Setting<SettingColor> onColor = sgColors.add(new ColorSetting.Builder()
        .name("on-color")
        .description("Dot color when a module turns on.")
        .defaultValue(new SettingColor(48, 209, 88)).visible(link.own())
        .build()
    );

    private final Setting<SettingColor> offColor = sgColors.add(new ColorSetting.Builder()
        .name("off-color")
        .description("Dot color when a module turns off.")
        .defaultValue(new SettingColor(255, 69, 58)).visible(link.own())
        .build()
    );

    private final Setting<SettingColor> textColor = sgColors.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Module name color.")
        .defaultValue(new SettingColor(255, 255, 255)).visible(link.own())
        .build()
    );

    private final Setting<Boolean> shadow = sgLook.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Soft shadow under the island.")
        .defaultValue(true)
        .build()
    );

    public enum Icon { Switch, Dot }

    private final Setting<Icon> icon = sgLook.add(new EnumSetting.Builder<Icon>()
        .name("icon")
        .description("Switch = a mini iOS switch that flips on/off. Dot = a glowing dot.")
        .defaultValue(Icon.Switch)
        .build()
    );

    private final Setting<Boolean> stateGlow = sgLook.add(new BoolSetting.Builder()
        .name("state-glow")
        .description("Soft halo around the island in the on/off color.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> countdown = sgLook.add(new BoolSetting.Builder()
        .name("countdown")
        .description("Thin line along the bottom that shrinks until the island closes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rim = sgLook.add(new BoolSetting.Builder()
        .name("border")
        .description("Faint rim around the pill.")
        .defaultValue(true)
        .build()
    );

    // State

    private Set<Module> lastActive; // null until the first tick, so we don't announce everything at startup
    private String title = "";
    private boolean turnedOn;
    private long shownAt = -1;      // System.nanoTime() of the current notification, -1 = none yet

    private final Spring width = new Spring(0);
    private final Spring height = new Spring(0);
    private final Spring visibility = new Spring(0);
    private final Spring content = new Spring(0);
    private final Spring switchAnim = new Spring(0); // 0 = switch off, 1 = switch on

    public DynamicIslandHud() {
        super(INFO);
    }

    /** Runs 20 times a second: compares which modules are on now vs last tick to find toggles. */
    @Override
    public void tick(HudRenderer renderer) {
        try {
            Set<Module> now = new HashSet<>(Modules.get().getActive());

            if (lastActive != null) {
                List<Module> enabled = new ArrayList<>(), disabled = new ArrayList<>();
                for (Module m : now) if (!lastActive.contains(m)) enabled.add(m);
                for (Module m : lastActive) if (!now.contains(m)) disabled.add(m);

                if (!showEnable.get()) enabled.clear();
                if (!showDisable.get()) disabled.clear();

                int changes = enabled.size() + disabled.size();
                if (changes == 1) {
                    Module m = enabled.isEmpty() ? disabled.get(0) : enabled.get(0);
                    show(m.title, !enabled.isEmpty());
                } else if (changes > 1) {
                    // e.g. loading a profile: one summary instead of a flood
                    show(changes + " modules changed", !enabled.isEmpty());
                }
            }

            lastActive = now;
        } catch (Exception e) {
            ErrorReporter.report(NAME, "tick", e);
        }
    }

    private void show(String title, boolean on) {
        this.title = title;
        this.turnedOn = on;
        this.shownAt = System.nanoTime();
        content.snap(0); // new text fades in fresh
        switchAnim.snap(on ? 0 : 1); // start from the old position so you see the switch flip
    }

    @Override
    public void render(HudRenderer renderer) {
        try {
            link.begin();
            double s = scale.get();
            double dt = link.dt(renderer);

            // Fixed box so the HUD editor handle doesn't jump around; the island is centered inside it.
            double boxW = 260 * s, boxH = 44 * s;
            setSize(boxW, boxH);

            boolean preview = isInEditor() && shownAt < 0;
            boolean showing = preview || (shownAt >= 0 && (System.nanoTime() - shownAt) / 1e9 < duration.get());
            String text = preview ? "Wings" : title;
            boolean on = preview || turnedOn;

            // Sizes
            double textSize = 18 * s;
            double expandedH = 36 * s, idleH = 26 * s, idleW = 90 * s;
            double dot = 9 * s, gap = 10 * s, pad = 16 * s;
            double swW = 22 * s, swH = 13 * s; // mini switch
            double iconW = icon.get() == Icon.Switch ? swW : dot;
            String state = on ? "On" : "Off";
            double textW = CrispText.width(renderer, text, textSize);
            double stateW = CrispText.width(renderer, state, textSize * 0.85);
            double expandedW = Math.min(boxW, pad + iconW + gap + textW + gap * 2 + stateW + pad);

            // Springs: the shape morphs smoothly (interruptible if a new toggle arrives mid-animation),
            // and the content fades in a touch slower than the shape opens.
            width.update(showing ? expandedW : idleW, dt, 0.42);
            height.update(showing ? expandedH : idleH, dt, 0.42);
            visibility.update(showing || idlePill.get() || isInEditor() ? 1 : 0, dt, 0.3);
            content.update(showing ? 1 : 0, dt, 0.35);

            double alpha = clamp01(visibility.value);
            if (alpha <= 0.01) return;

            double w = Math.max(0, width.value), h = Math.max(0, height.value);
            double ix = x + (boxW - w) / 2, iy = y + (boxH - h) / 2;

            // Content only shows once the island is wide enough for it, so text never spills outside.
            double room = clamp01((w - idleW) / Math.max(1, expandedW - idleW));
            double c = clamp01(content.value) * room * alpha;
            Color dotColor = on ? link.on(onColor) : link.dim(offColor);

            if (stateGlow.get() && c > 0.02) HudShapes.outerGlow(renderer, ix, iy, w, h, h / 2, 10 * s, HudShapes.scaleAlpha(dotColor, 0.55 * c));
            if (shadow.get()) HudShapes.shadow(renderer, ix, iy, w, h, h / 2, 8 * s, (int) (110 * alpha));
            HudShapes.rect(renderer, ix, iy, w, h, h / 2, HudShapes.scaleAlpha(link.tint(islandColor), alpha));
            if (rim.get()) HudShapes.border(renderer, ix, iy, w, h, h / 2, Math.max(1, s * 0.8), link.outline(new Color(255, 255, 255, (int) (22 * alpha))));

            if (c <= 0.02 || text.isEmpty()) return; // keeps drawing while fading out after the notification ends

            double cx = ix + pad, cy = iy + h / 2;
            double age = preview ? 10 : (System.nanoTime() - shownAt) / 1e9;

            // Countdown: thin line along the bottom that shrinks until the island closes
            if (countdown.get() && !preview) {
                double left = clamp01(1 - age / duration.get());
                double lw = (w - h) * left;
                if (lw > 0.5) HudShapes.rect(renderer, ix + h / 2, iy + h - 3 * s, lw, Math.max(1, 1.5 * s), 0.75 * s, HudShapes.scaleAlpha(dotColor, 0.7 * c));
            }

            if (icon.get() == Icon.Switch) {
                // Mini iOS switch, flipping to its new position with a spring
                double v = clamp01(switchAnim.update(on ? 1 : 0, dt, 0.3));
                double sy = cy - swH / 2;
                Color track = HudShapes.mix(link.off(new Color(72, 72, 74)), link.on(onColor), v);
                HudShapes.rect(renderer, cx, sy, swW, swH, swH / 2, HudShapes.scaleAlpha(track, c));
                double kr = swH / 2 - 1.5 * s;
                double kx = cx + swH / 2 + (swW - swH) * v;
                HudShapes.circle(renderer, kx, cy + 0.6 * s, kr, new Color(0, 0, 0, (int) (70 * c)));
                HudShapes.circle(renderer, kx, cy, kr, new Color(255, 255, 255, (int) (255 * c)));
            } else {
                // Dot with a soft glow, plus a ring that ripples out when the notification first appears.
                if (age < 0.7) {
                    double t = age / 0.7;
                    HudShapes.ring(renderer, cx + dot / 2, cy, dot / 2 + t * 10 * s, 1.5 * s, HudShapes.scaleAlpha(dotColor, (1 - t) * c));
                }
                HudShapes.circle(renderer, cx + dot / 2, cy, dot, HudShapes.scaleAlpha(dotColor, 0.25 * c));
                HudShapes.circle(renderer, cx + dot / 2, cy, dot / 2, HudShapes.scaleAlpha(dotColor, c));
            }

            double tx = cx + iconW + gap;
            double th = CrispText.height(renderer, textSize);
            CrispText.draw(renderer, text, tx, cy - th / 2, HudShapes.scaleAlpha(link.text(textColor), c), false, textSize);

            double sh = CrispText.height(renderer, textSize * 0.85);
            CrispText.draw(renderer, state, ix + w - pad - stateW, cy - sh / 2, HudShapes.scaleAlpha(dotColor, c), false, textSize * 0.85);
        } catch (Exception e) {
            ErrorReporter.report(NAME, "render", e);
        }
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
