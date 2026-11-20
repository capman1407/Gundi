package com.david.gundi.util;

import meteordevelopment.meteorclient.utils.render.color.Color;

/**
 * The color helpers every part of Gundi uses (HUD, GUI theme, modules), in one place.
 * HudShapes, RoundRender and AppleWidget keep their short names for these, but they all call this.
 */
public final class Colors {
    private Colors() {}

    /** Color between a and b. t = 0 gives a, t = 1 gives b (clamped). */
    public static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
            (int) (a.r + (b.r - a.r) * t),
            (int) (a.g + (b.g - a.g) * t),
            (int) (a.b + (b.b - a.b) * t),
            (int) (a.a + (b.a - a.a) * t)
        );
    }

    /** Copy of a color with its transparency multiplied by f (0 = invisible, 1 = unchanged). */
    public static Color alpha(Color c, double f) {
        return new Color(c.r, c.g, c.b, clamp255(c.a * f));
    }

    /** Copy of a color with this exact alpha (0-255). */
    public static Color withAlpha(Color c, double a) {
        return new Color(c.r, c.g, c.b, clamp255(a));
    }

    /** Adds `amount` to red, green and blue (negative = darker). */
    public static Color lighten(Color c, int amount) {
        return new Color(clamp255(c.r + amount), clamp255(c.g + amount), clamp255(c.b + amount), c.a);
    }

    /** Hue 0-1 (wraps around), saturation and value 0-1, alpha 0-255. */
    public static Color hsv(double h, double s, double v, int a) {
        h = ((h % 1) + 1) % 1;
        s = Math.max(0, Math.min(1, s));
        v = Math.max(0, Math.min(1, v));
        int i = (int) (h * 6);
        double f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), w = v * (1 - (1 - f) * s);
        double r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = w; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = w; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = w; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return new Color((int) (r * 255), (int) (g * 255), (int) (b * 255), clamp255(a));
    }

    private static int clamp255(double v) {
        return (int) Math.max(0, Math.min(255, v));
    }
}
