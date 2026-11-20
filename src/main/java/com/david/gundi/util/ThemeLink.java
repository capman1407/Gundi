package com.david.gundi.util;

import com.david.gundi.gui.GundiTheme;
import com.david.gundi.gui.ThemeStyles.CornerStyle;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.function.Function;

/**
 * Connects a HUD element (or module) to the selected Gundi GUI theme, so the whole client looks like one thing.
 *
 * One setting, "use-theme" (on by default). While it's on and a Gundi theme is selected:
 * - colors come from the theme by role: text, dim text, accent, accent-2, panel tint, outline, glow, on/off...
 *   (the element's own color setting still decides how transparent that color is)
 * - rounded shapes use the theme's corner style (Round / Chamfer / Square / Pixel)
 * - animations run at the theme's animation speed
 * Colors that mean something specific (durability green/red, X/Y/Z axes, enemy red) stay the element's own.
 *
 * Everything is read live every frame, so switching themes updates every HUD at once.
 * With use-theme off (or a non-Gundi theme selected), the element uses its own settings as before.
 */
public class ThemeLink {
    public final Setting<Boolean> useTheme;

    public ThemeLink(SettingGroup group) {
        useTheme = group.add(new BoolSetting.Builder()
            .name("use-theme")
            .description("Follow the selected Gundi GUI theme (colors, corners, borders, glow, animation speed). Off = use this element's own look settings.")
            .defaultValue(true)
            .build());
    }

    /** The theme to follow right now, or null (use-theme off, or the selected GUI theme isn't a Gundi theme). */
    public GundiTheme theme() {
        if (!useTheme.get()) return null;
        return GuiThemes.get() instanceof GundiTheme g ? g : null;
    }

    public boolean active() {
        return theme() != null;
    }

    /** For settings that only matter when the theme isn't followed: hides them while use-theme is on. */
    public IVisible own() {
        return () -> !useTheme.get();
    }

    /** Call at the start of a HUD element's render: sets the corner style for everything it draws. */
    public void begin() {
        GundiTheme t = theme();
        HudShapes.corners = t != null ? t.corners.get() : CornerStyle.Round;
    }

    /** Frame time for animations (seconds, capped for lag spikes), scaled by the theme's animation speed. */
    public double dt(HudRenderer r) {
        double dt = Math.min(Math.max(r.delta, 0), 0.1);
        GundiTheme t = theme();
        return t != null ? dt * t.animationSpeed.get() : dt;
    }

    // ----- Colors by role. "own" is the element's own setting (used as-is when not following the theme). -----

    public Color text(Setting<SettingColor> own) { return pick(own.get(), t -> t.text.get()); }
    public Color dim(Setting<SettingColor> own) { return pick(own.get(), t -> t.textSecondary.get()); }
    public Color accent(Setting<SettingColor> own) { return pick(own.get(), t -> t.accent.get()); }
    public Color accent2(Setting<SettingColor> own) { return pick(own.get(), t -> t.accent2.get()); }
    public Color outline(Setting<SettingColor> own) { return pick(own.get(), t -> t.outline.get()); }
    public Color glow(Setting<SettingColor> own) { return pick(own.get(), t -> Colors.alpha(t.glowColor.get(), t.glowAlpha())); }
    public Color on(Setting<SettingColor> own) { return pick(own.get(), t -> t.switchOn.get()); }
    public Color off(Setting<SettingColor> own) { return pick(own.get(), t -> t.switchOff.get()); }
    /** Faint tiles/chips on a panel: the theme's text color at the element's own (low) opacity. */
    public Color tile(Setting<SettingColor> own) { return pick(own.get(), t -> t.text.get()); }

    public Color text(Color own) { return pick(own, t -> t.text.get()); }
    public Color dim(Color own) { return pick(own, t -> t.textSecondary.get()); }
    public Color accent(Color own) { return pick(own, t -> t.accent.get()); }
    public Color gain(Color own) { return pick(own, t -> t.plus.get()); }
    public Color loss(Color own) { return pick(own, t -> t.minus.get()); }
    public Color tile(Color own) { return pick(own, t -> t.text.get()); }
    public Color off(Color own) { return pick(own, t -> t.switchOff.get()); }
    public Color outline(Color own) { return pick(own, t -> t.outline.get()); }

    /** Panel background: the theme's window color, at the theme's HUD opacity. */
    public Color tint(Setting<SettingColor> own) {
        GundiTheme t = theme();
        if (t == null) return own.get();
        Color w = t.window.get();
        return Colors.withAlpha(w, w.a * t.hudOpacity.get());
    }

    /** Theme color with the transparency of the element's own color, so faint things stay faint. */
    private Color pick(Color own, Function<GundiTheme, Color> fromTheme) {
        GundiTheme t = theme();
        if (t == null) return own;
        Color c = fromTheme.apply(t);
        return Colors.withAlpha(c, c.a * own.a / 255.0);
    }
}
