package com.david.gundi.util;

import com.david.gundi.gui.ThemeStyles.CornerStyle;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

/**
 * Rounded, Apple-style shapes for HUD elements (the HUD version of gui/RoundRender).
 * Meteor's HUD renderer only draws rectangles, lines and triangles, so rounded shapes are built from triangles.
 *
 * glassPanel() is the one most HUD elements use: soft shadow + top-to-bottom tint + thin border + rim light.
 */
public final class HudShapes {
    private static final int CORNER_STEPS = 8;

    /**
     * Corner style for rounded shapes. Each HUD element sets it at the start of its render (GlassStyle.begin),
     * to the selected theme's corner style when it follows the theme, otherwise Round.
     */
    public static CornerStyle corners = CornerStyle.Round;

    private HudShapes() {}

    /** The standard Apple glass panel. alpha (0-1) fades the whole thing, e.g. for show/hide animations. */
    public static void glassPanel(HudRenderer r, double x, double y, double w, double h, double radius,
                                  Color tint, boolean shadow, double alpha) {
        if (alpha <= 0.003 || w <= 0 || h <= 0) return;

        if (shadow) shadow(r, x, y, w, h, radius, Math.max(4, radius * 1.2), (int) (90 * alpha));

        Color top = scaleAlpha(lighten(tint, 16), 0.8 * alpha);
        Color bottom = scaleAlpha(tint, 0.95 * alpha);
        gradientRect(r, x, y, w, h, radius, top, bottom);

        border(r, x, y, w, h, radius, 1, new Color(255, 255, 255, (int) (24 * alpha)));
        rimLight(r, x + radius, y + 1, w - radius * 2, 1, new Color(255, 255, 255, (int) (70 * alpha)));
    }

    /** Filled rounded rectangle. */
    public static void rect(HudRenderer r, double x, double y, double w, double h, double radius, Color c) {
        if (w <= 0 || h <= 0 || c.a <= 0) return;
        double[] p = outline(x, y, w, h, radius);
        double cx = x + w / 2, cy = y + h / 2;
        int n = p.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            triangle(r, cx, cy, p[i * 2], p[i * 2 + 1], p[j * 2], p[j * 2 + 1], c);
        }
    }

    /** Rounded rectangle fading from `top` color to `bottom` color. */
    public static void gradientRect(HudRenderer r, double x, double y, double w, double h, double radius, Color top, Color bottom) {
        if (w <= 0 || h <= 0) return;
        radius = clamp(radius, Math.min(w, h) / 2);

        for (int i = 0; i < CORNER_STEPS && radius > 0; i++) {
            double ya = y + radius * i / CORNER_STEPS, yb = y + radius * (i + 1) / CORNER_STEPS;
            strip(r, x, w, ya, yb, inset(radius, ya - y), inset(radius, yb - y), colorAt(top, bottom, y, h, (ya + yb) / 2));
        }

        double midTop = y + radius, midH = h - radius * 2;
        if (midH > 0) {
            Color a = colorAt(top, bottom, y, h, midTop), b = colorAt(top, bottom, y, h, midTop + midH);
            r.quad(x, midTop, w, midH, a, a, b, b);
        }

        for (int i = 0; i < CORNER_STEPS && radius > 0; i++) {
            double ya = y + h - radius + radius * i / CORNER_STEPS, yb = y + h - radius + radius * (i + 1) / CORNER_STEPS;
            strip(r, x, w, ya, yb, inset(radius, y + h - ya), inset(radius, y + h - yb), colorAt(top, bottom, y, h, (ya + yb) / 2));
        }
    }

    /** Rounded border line, drawn inside the rectangle's edge. */
    public static void border(HudRenderer r, double x, double y, double w, double h, double radius, double thickness, Color c) {
        if (w <= 0 || h <= 0 || c.a <= 0 || thickness <= 0) return;
        double t = Math.min(thickness, Math.min(w, h) / 2);
        double[] outer = outline(x, y, w, h, radius);
        double[] inner = outline(x + t, y + t, w - 2 * t, h - 2 * t, Math.max(radius - t, 0));
        int n = outer.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            triangle(r, outer[i * 2], outer[i * 2 + 1], outer[j * 2], outer[j * 2 + 1], inner[j * 2], inner[j * 2 + 1], c);
            triangle(r, outer[i * 2], outer[i * 2 + 1], inner[j * 2], inner[j * 2 + 1], inner[i * 2], inner[i * 2 + 1], c);
        }
    }

    /** Soft shadow made of rings that get fainter outwards. Nothing is drawn under the panel itself. */
    public static void shadow(HudRenderer r, double x, double y, double w, double h, double radius, double spread, int alpha) {
        int layers = 5;
        double step = spread / layers, drop = spread * 0.25;
        for (int i = 0; i < layers; i++) {
            double e = step * (i + 1);
            int a = (int) (alpha * Math.pow(1 - (double) i / layers, 2) / 2.5);
            if (a <= 0) continue;
            border(r, x - e, y - e + drop, w + 2 * e, h + 2 * e, radius + e, step, new Color(0, 0, 0, a));
        }
    }

    /**
     * Soft shadow for a CIRCLE: rings that follow the circle and fade outwards, a little lower (light from above).
     * Use this for round things. shadow() is for rectangles: it follows the corner style, so with Square or
     * Chamfer corners a "round" rectangle shadow turns square, which showed as a dark box around the radar.
     */
    public static void circleShadow(HudRenderer r, double cx, double cy, double radius, double spread, int alpha) {
        int layers = 5;
        double step = spread / layers, drop = spread * 0.25;
        for (int i = 0; i < layers; i++) {
            int a = (int) (alpha * Math.pow(1 - (double) i / layers, 2) / 2.5);
            if (a <= 0) continue;
            double outer = radius + step * (i + 1);
            // The first ring reaches under the disc edge, so the downward offset leaves no gap
            double thickness = i == 0 ? step + drop : step;
            ring(r, cx, cy + drop, outer, thickness, new Color(0, 0, 0, a));
        }
    }

    /** Colored soft glow around a rounded rectangle (rings that fade outwards, nothing under the panel). */
    public static void outerGlow(HudRenderer r, double x, double y, double w, double h, double radius, double spread, Color c) {
        if (c.a <= 0 || spread <= 0) return;
        int layers = 6;
        double step = spread / layers;
        for (int i = 0; i < layers; i++) {
            double e = step * (i + 1);
            double f = 1 - (double) i / layers;
            border(r, x - e, y - e, w + 2 * e, h + 2 * e, radius + e, step, scaleAlpha(c, f * f * 0.55));
        }
    }

    /** Thin bright line that fades out towards both ends, like light on the rim of glass. */
    public static void rimLight(HudRenderer r, double x, double y, double w, double thickness, Color c) {
        if (w <= 0 || c.a <= 0) return;
        Color clear = new Color(c.r, c.g, c.b, 0);
        double half = w / 2;
        r.quad(x, y, half, thickness, clear, c, c, clear);
        r.quad(x + half, y, w - half, thickness, c, clear, clear, c);
    }

    /** Filled circle (many-sided polygon). */
    public static void circle(HudRenderer r, double cx, double cy, double radius, Color c) {
        if (radius <= 0 || c.a <= 0) return;
        int steps = Math.max(12, (int) (radius * 2));
        for (int i = 0; i < steps; i++) {
            double a1 = Math.PI * 2 * i / steps, a2 = Math.PI * 2 * (i + 1) / steps;
            triangle(r, cx, cy, cx + Math.cos(a1) * radius, cy + Math.sin(a1) * radius,
                cx + Math.cos(a2) * radius, cy + Math.sin(a2) * radius, c);
        }
    }

    /** Ring (circle outline) of the given thickness. */
    public static void ring(HudRenderer r, double cx, double cy, double radius, double thickness, Color c) {
        arc(r, cx, cy, radius, thickness, 0, Math.PI * 2, c);
    }

    /** Part of a ring from angle `start` to `end` (radians, 0 = right, clockwise on screen). */
    public static void arc(HudRenderer r, double cx, double cy, double radius, double thickness, double start, double end, Color c) {
        if (radius <= 0 || c.a <= 0 || end <= start) return;
        double inner = Math.max(0, radius - thickness);
        int steps = Math.max(6, (int) ((end - start) / (Math.PI * 2) * Math.max(24, radius * 3)));
        for (int i = 0; i < steps; i++) {
            double a1 = start + (end - start) * i / steps, a2 = start + (end - start) * (i + 1) / steps;
            double ox1 = cx + Math.cos(a1) * radius, oy1 = cy + Math.sin(a1) * radius;
            double ox2 = cx + Math.cos(a2) * radius, oy2 = cy + Math.sin(a2) * radius;
            double ix1 = cx + Math.cos(a1) * inner, iy1 = cy + Math.sin(a1) * inner;
            double ix2 = cx + Math.cos(a2) * inner, iy2 = cy + Math.sin(a2) * inner;
            triangle(r, ox1, oy1, ox2, oy2, ix2, iy2, c);
            triangle(r, ox1, oy1, ix2, iy2, ix1, iy1, c);
        }
    }

    /** Filled triangle that always shows up, whatever order the corners are given in. */
    public static void tri(HudRenderer r, double x1, double y1, double x2, double y2, double x3, double y3, Color c) {
        triangle(r, x1, y1, x2, y2, x3, y3, c);
    }

    /** Border whose color flows around the shape between a and b; increase phase over time to animate it. */
    public static void gradientBorder(HudRenderer r, double x, double y, double w, double h, double radius,
                                      double thickness, Color a, Color b, double phase) {
        if (w <= 0 || h <= 0 || thickness <= 0) return;
        double t = Math.min(thickness, Math.min(w, h) / 2);
        double[] outer = outline(x, y, w, h, radius);
        double[] inner = outline(x + t, y + t, w - 2 * t, h - 2 * t, Math.max(radius - t, 0));
        double cx = x + w / 2, cy = y + h / 2;
        int n = outer.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double ox1 = outer[i * 2], oy1 = outer[i * 2 + 1], ox2 = outer[j * 2], oy2 = outer[j * 2 + 1];
            double ix1 = inner[i * 2], iy1 = inner[i * 2 + 1], ix2 = inner[j * 2], iy2 = inner[j * 2 + 1];
            double len = Math.hypot(ox2 - ox1, oy2 - oy1);
            if (len < 1e-6) continue;
            int pieces = Math.max(1, (int) Math.ceil(len / 6));
            for (int k = 0; k < pieces; k++) {
                double f1 = (double) k / pieces, f2 = (double) (k + 1) / pieces;
                double ax = ox1 + (ox2 - ox1) * f1, ay = oy1 + (oy2 - oy1) * f1;
                double bx = ox1 + (ox2 - ox1) * f2, by = oy1 + (oy2 - oy1) * f2;
                double qx = ix1 + (ix2 - ix1) * f2, qy = iy1 + (iy2 - iy1) * f2;
                double dx = ix1 + (ix2 - ix1) * f1, dy = iy1 + (iy2 - iy1) * f1;
                double ang = Math.atan2(((ay + by) / 2 - cy) * w / Math.max(h, 1), (ax + bx) / 2 - cx);
                Color c = mix(a, b, 0.5 + 0.5 * Math.sin(ang + phase));
                triangle(r, ax, ay, bx, by, qx, qy, c);
                triangle(r, ax, ay, qx, qy, dx, dy, c);
            }
        }
    }

    /** Sci-fi corner brackets: an L shape in each corner. */
    public static void brackets(HudRenderer r, double x, double y, double w, double h, double len, double t, Color c) {
        len = Math.min(len, Math.min(w, h) / 2);
        r.quad(x, y, len, t, c);
        r.quad(x, y + t, t, len - t, c);
        r.quad(x + w - len, y, len, t, c);
        r.quad(x + w - t, y + t, t, len - t, c);
        r.quad(x, y + h - t, len, t, c);
        r.quad(x, y + h - len, t, len - t, c);
        r.quad(x + w - len, y + h - t, len, t, c);
        r.quad(x + w - t, y + h - len, t, len - t, c);
    }

    // Colors

    public static Color lighten(Color c, int amount) {
        return Colors.lighten(c, amount);
    }

    public static Color scaleAlpha(Color c, double f) {
        return Colors.alpha(c, f);
    }

    public static Color mix(Color a, Color b, double t) {
        return Colors.mix(a, b, t);
    }

    // Internals

    private static double[] outline(double x, double y, double w, double h, double radius) {
        return Corners.outline(x, y, w, h, radius, radius, radius, radius, corners);
    }

    private static double inset(double radius, double d) {
        return Corners.inset(radius, d, corners);
    }

    private static void strip(HudRenderer r, double x, double w, double ya, double yb, double insetA, double insetB, Color c) {
        triangle(r, x + insetA, ya, x + w - insetA, ya, x + w - insetB, yb, c);
        triangle(r, x + insetA, ya, x + w - insetB, yb, x + insetB, yb, c);
    }

    private static Color colorAt(Color top, Color bottom, double y, double h, double atY) {
        return mix(top, bottom, (atY - y) / h);
    }

    private static double clamp(double radius, double max) {
        return Math.max(0, Math.min(radius, max));
    }

    /** Meteor's HUD hides triangles in the "wrong" corner order (back-face culling); this always uses the right one. */
    private static void triangle(HudRenderer r, double x1, double y1, double x2, double y2, double x3, double y3, Color c) {
        double area = (x2 - x1) * (y3 - y1) - (y2 - y1) * (x3 - x1);
        if (Math.abs(area) < 1e-9) return;
        if (area > 0) r.triangle(x1, y1, x3, y3, x2, y2, c);
        else r.triangle(x1, y1, x2, y2, x3, y3, c);
    }
}
