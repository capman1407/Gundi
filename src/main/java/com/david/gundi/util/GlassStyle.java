package com.david.gundi.util;

import com.david.gundi.gui.GundiTheme;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * The glass panel behind Gundi's HUD elements.
 *
 * Following the theme (the element's use-theme, see ThemeLink): drawn exactly like the theme's GUI windows —
 * same surface (frosted / solid / tinted / scanlines), border style (hairline, double, brackets, glow, gradient),
 * corner style, shadow and glow, using the theme's colors and its HUD opacity. The panel's own look settings
 * are hidden then, so nothing is set in two places.
 *
 * Not following: the element's own Panel settings (tint, highlight, radius, border, shadow, glow), as before.
 */
public class GlassStyle {
    public final ThemeLink link;

    public final Setting<Boolean> panel;
    public final Setting<SettingColor> tint;
    public final Setting<Double> highlight;
    public final Setting<Integer> radius;
    public final Setting<Boolean> border;
    public final Setting<SettingColor> borderColor;
    public final Setting<Double> borderWidth;
    public final Setting<Boolean> shadow;
    public final Setting<Boolean> glow;
    public final Setting<SettingColor> glowColor;
    public final Setting<Double> glowStrength;

    /** Defaults for one HUD element's own panel look. radius is in pixels at scale 1. */
    public record Defaults(SettingColor tint, int radius, boolean glow, SettingColor glowColor) {}

    public GlassStyle(Settings settings, ThemeLink link, Defaults d) {
        this.link = link;
        SettingGroup g = settings.createGroup("Panel", false);
        IVisible own = link.own();

        panel = g.add(new BoolSetting.Builder().name("panel").description("Draw the glass background.").defaultValue(true).build());
        IVisible ownPanel = () -> panel.get() && own.isVisible();
        tint = g.add(new ColorSetting.Builder().name("panel-color").description("Glass color; alpha = opacity.").defaultValue(d.tint).visible(ownPanel).build());
        highlight = g.add(new DoubleSetting.Builder().name("panel-highlight").description("Light at the top of the glass and the rim shine.").defaultValue(1).range(0, 3).visible(ownPanel).build());
        radius = g.add(new IntSetting.Builder().name("corner-radius").description("How rounded the corners are.").defaultValue(d.radius).range(0, 40).sliderRange(0, 30).visible(ownPanel).build());
        border = g.add(new BoolSetting.Builder().name("border").description("Thin border around the panel.").defaultValue(true).visible(ownPanel).build());
        borderColor = g.add(new ColorSetting.Builder().name("border-color").description("Border color; alpha = opacity.").defaultValue(new SettingColor(255, 255, 255, 26)).visible(() -> ownPanel.isVisible() && border.get()).build());
        borderWidth = g.add(new DoubleSetting.Builder().name("border-width").description("Border thickness.").defaultValue(1).range(0.5, 6).visible(() -> ownPanel.isVisible() && border.get()).build());
        shadow = g.add(new BoolSetting.Builder().name("shadow").description("Soft drop shadow.").defaultValue(true).visible(ownPanel).build());
        glow = g.add(new BoolSetting.Builder().name("glow").description("Soft colored glow around the panel.").defaultValue(d.glow).visible(ownPanel).build());
        glowColor = g.add(new ColorSetting.Builder().name("glow-color").description("Glow color; alpha = strength.").defaultValue(d.glowColor).visible(() -> ownPanel.isVisible() && glow.get()).build());
        glowStrength = g.add(new DoubleSetting.Builder().name("glow-size").description("How far the glow reaches.").defaultValue(1).range(0.2, 3).visible(() -> ownPanel.isVisible() && glow.get()).build());
    }

    /** Draws the panel. s = the element's scale, alpha = fade (0-1). */
    public void draw(HudRenderer r, double x, double y, double w, double h, double s, double alpha) {
        draw(r, x, y, w, h, s, alpha, null);
    }

    /**
     * Draws the panel. glowOverride (if not null) replaces the glow color, e.g. to glow in a "live" color
     * (it's still only drawn when glow is on).
     */
    public void draw(HudRenderer r, double x, double y, double w, double h, double s, double alpha, Color glowOverride) {
        if (!panel.get() || alpha <= 0.003 || w <= 0 || h <= 0) return;
        GundiTheme t = link.theme();
        if (t != null) drawThemed(r, t, x, y, w, h, s, alpha, glowOverride);
        else drawOwn(r, x, y, w, h, s, alpha, glowOverride);
    }

    /** Corner radius the panel uses right now, so elements can round their inner parts to match. */
    public double radius(double s, double w, double h) {
        GundiTheme t = link.theme();
        double rad = t != null ? t.cornerRadius.get() * 1.5 : radius.get();
        return Math.min(rad * s, Math.min(w, h) / 2);
    }

    private void drawOwn(HudRenderer r, double x, double y, double w, double h, double s, double alpha, Color glowOverride) {
        double rad = radius(s, w, h);
        double hl = highlight.get();

        if (shadow.get()) HudShapes.shadow(r, x, y, w, h, rad, Math.max(4, 10 * s), (int) (100 * alpha));
        if (glow.get()) {
            Color gc = glowOverride != null ? glowOverride : glowColor.get();
            HudShapes.outerGlow(r, x, y, w, h, rad, 9 * s * glowStrength.get(), Colors.alpha(gc, alpha));
        }

        Color base = tint.get();
        HudShapes.gradientRect(r, x, y, w, h, rad, Colors.alpha(Colors.lighten(base, (int) (16 * hl)), 0.82 * alpha), Colors.alpha(base, alpha));

        if (border.get()) HudShapes.border(r, x, y, w, h, rad, borderWidth.get() * Math.max(1, s * 0.75), Colors.alpha(borderColor.get(), alpha));
        if (hl > 0) HudShapes.rimLight(r, x + rad, y + 1, w - rad * 2, Math.max(1, s), new Color(255, 255, 255, (int) Math.min(255, 70 * hl * alpha)));
    }

    /** Same recipe as the theme's GUI windows (gui/widgets/AppleWindow), on the HUD renderer. */
    private void drawThemed(HudRenderer r, GundiTheme t, double x, double y, double w, double h, double s, double alpha, Color glowOverride) {
        double rad = radius(s, w, h);
        double line = Math.max(1, t.borderWidth.get() * Math.max(1, s * 0.75));
        double hair = Math.max(1, s);
        Color base = link.tint(tint);
        Color accent = t.accent.get(), accent2 = t.accent2.get();

        if (t.shadows.get() && t.shadowStrength.get() > 0) {
            HudShapes.shadow(r, x, y, w, h, rad, Math.max(4, 10 * s), (int) (100 * alpha * t.shadowStrength.get()));
        }
        boolean glowing = t.hudGlow.get() && (t.glow.get() || t.border.get() == com.david.gundi.gui.ThemeStyles.BorderStyle.Glow);
        if (glowing) {
            Color gc = glowOverride != null ? glowOverride : Colors.alpha(t.glowColor.get(), t.glowAlpha());
            HudShapes.outerGlow(r, x, y, w, h, rad, 0.9 * s * t.glowSize.get(), Colors.alpha(gc, alpha));
        }

        // Surface
        switch (t.surface.get()) {
            case Frosted -> {
                HudShapes.gradientRect(r, x, y, w, h, rad, Colors.alpha(Colors.lighten(base, 16), 0.82 * alpha), Colors.alpha(base, alpha));
                HudShapes.rimLight(r, x + rad, y + 1, w - rad * 2, hair, new Color(255, 255, 255, (int) (70 * alpha)));
            }
            case Solid -> HudShapes.rect(r, x, y, w, h, rad, Colors.alpha(base, alpha));
            case Tinted -> HudShapes.gradientRect(r, x, y, w, h, rad,
                Colors.alpha(Colors.mix(base, Colors.withAlpha(accent, base.a), 0.16), alpha),
                Colors.alpha(Colors.mix(base, Colors.withAlpha(accent2, base.a), 0.10), alpha));
            case Scanlines -> {
                HudShapes.rect(r, x, y, w, h, rad, Colors.alpha(base, alpha));
                double step = Math.max(2, 3 * s), lh = Math.max(1, step / 3);
                Color lc = Colors.alpha(t.text.get(), 0.035 * alpha);
                for (double yy = y + rad; yy + lh < y + h - rad; yy += step) r.quad(x + 1, yy, w - 2, lh, lc);
            }
        }

        // Border
        switch (t.border.get()) {
            case Hairline -> HudShapes.border(r, x, y, w, h, rad, line, Colors.alpha(t.outline.get(), alpha));
            case Double -> {
                double d = 3 * s;
                HudShapes.border(r, x, y, w, h, rad, line, Colors.alpha(t.outline.get(), alpha));
                HudShapes.border(r, x + d, y + d, w - d * 2, h - d * 2, Math.max(rad - d, 0), hair, Colors.alpha(t.outline.get(), 0.5 * alpha));
            }
            case Brackets -> {
                HudShapes.border(r, x, y, w, h, rad, hair, Colors.alpha(t.outline.get(), 0.6 * alpha));
                HudShapes.brackets(r, x, y, w, h, 10 * s, Math.max(line, 2 * s), Colors.alpha(accent, alpha));
            }
            case Glow -> HudShapes.border(r, x, y, w, h, rad, line, Colors.alpha(accent, 0.7 * alpha));
            case Gradient -> HudShapes.gradientBorder(r, x, y, w, h, rad, line, Colors.alpha(accent, alpha), Colors.alpha(accent2, alpha),
                GundiTheme.time() * 1.6 * t.animationSpeed.get());
            default -> {}
        }
    }
}
