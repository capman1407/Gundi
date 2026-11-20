package com.david.gundi.util;

import com.david.gundi.gui.ThemeStyles.CornerStyle;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.utils.render.color.Color;

/**
 * Rounded Apple-style shapes drawn straight with Meteor's Renderer2D.COLOR.
 * Used where there's no HudRenderer, e.g. nametags in the 2D render event.
 * Same shapes as HudShapes; call Renderer2D.COLOR.begin() before and .render() after.
 */
public final class Shapes2D {
    private static final int CORNER_STEPS = 6;
    private static final Renderer2D R = Renderer2D.COLOR;

    /** Corner style, set by the feature drawing (nametags follow the selected theme's corners). */
    public static CornerStyle corners = CornerStyle.Round;

    private Shapes2D() {}

    public static void rect(double x, double y, double w, double h, double radius, Color c) {
        if (w <= 0 || h <= 0 || c.a <= 0) return;
        double[] p = outline(x, y, w, h, radius);
        double cx = x + w / 2, cy = y + h / 2;
        int n = p.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            tri(cx, cy, p[i * 2], p[i * 2 + 1], p[j * 2], p[j * 2 + 1], c);
        }
    }

    public static void gradientRect(double x, double y, double w, double h, double radius, Color top, Color bottom) {
        if (w <= 0 || h <= 0) return;
        radius = Math.max(0, Math.min(radius, Math.min(w, h) / 2));

        for (int i = 0; i < CORNER_STEPS && radius > 0; i++) {
            double ya = y + radius * i / CORNER_STEPS, yb = y + radius * (i + 1) / CORNER_STEPS;
            strip(x, w, ya, yb, inset(radius, ya - y), inset(radius, yb - y), HudShapes.mix(top, bottom, ((ya + yb) / 2 - y) / h));
        }

        double midTop = y + radius, midH = h - radius * 2;
        if (midH > 0) {
            Color a = HudShapes.mix(top, bottom, (midTop - y) / h), b = HudShapes.mix(top, bottom, (midTop + midH - y) / h);
            R.quad(x, midTop, w, midH, a, a, b, b);
        }

        for (int i = 0; i < CORNER_STEPS && radius > 0; i++) {
            double ya = y + h - radius + radius * i / CORNER_STEPS, yb = y + h - radius + radius * (i + 1) / CORNER_STEPS;
            strip(x, w, ya, yb, inset(radius, y + h - ya), inset(radius, y + h - yb), HudShapes.mix(top, bottom, ((ya + yb) / 2 - y) / h));
        }
    }

    public static void border(double x, double y, double w, double h, double radius, double thickness, Color c) {
        if (w <= 0 || h <= 0 || c.a <= 0 || thickness <= 0) return;
        double t = Math.min(thickness, Math.min(w, h) / 2);
        double[] outer = outline(x, y, w, h, radius);
        double[] inner = outline(x + t, y + t, w - 2 * t, h - 2 * t, Math.max(radius - t, 0));
        int n = outer.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            tri(outer[i * 2], outer[i * 2 + 1], outer[j * 2], outer[j * 2 + 1], inner[j * 2], inner[j * 2 + 1], c);
            tri(outer[i * 2], outer[i * 2 + 1], inner[j * 2], inner[j * 2 + 1], inner[i * 2], inner[i * 2 + 1], c);
        }
    }

    /** Thin line that fades out towards both ends. */
    public static void rimLight(double x, double y, double w, double thickness, Color c) {
        if (w <= 0 || c.a <= 0) return;
        Color clear = new Color(c.r, c.g, c.b, 0);
        double half = w / 2;
        R.quad(x, y, half, thickness, clear, c, c, clear);
        R.quad(x + half, y, w - half, thickness, c, clear, clear, c);
    }

    public static void circle(double cx, double cy, double radius, Color c) {
        if (radius <= 0 || c.a <= 0) return;
        int steps = 16;
        for (int i = 0; i < steps; i++) {
            double a1 = Math.PI * 2 * i / steps, a2 = Math.PI * 2 * (i + 1) / steps;
            tri(cx, cy, cx + Math.cos(a1) * radius, cy + Math.sin(a1) * radius, cx + Math.cos(a2) * radius, cy + Math.sin(a2) * radius, c);
        }
    }

    /** Triangle that always shows up, whatever order the corners are given in (Meteor culls "backwards" ones). */
    public static void tri(double x1, double y1, double x2, double y2, double x3, double y3, Color c) {
        double area = (x2 - x1) * (y3 - y1) - (y2 - y1) * (x3 - x1);
        if (Math.abs(area) < 1e-9) return;
        if (area > 0) R.triangle(x1, y1, x3, y3, x2, y2, c);
        else R.triangle(x1, y1, x2, y2, x3, y3, c);
    }

    private static double[] outline(double x, double y, double w, double h, double radius) {
        return Corners.outline(x, y, w, h, radius, radius, radius, radius, corners);
    }

    private static double inset(double radius, double d) {
        return Corners.inset(radius, d, corners);
    }

    private static void strip(double x, double w, double ya, double yb, double insetA, double insetB, Color c) {
        tri(x + insetA, ya, x + w - insetA, ya, x + w - insetB, yb, c);
        tri(x + insetA, ya, x + w - insetB, yb, x + insetB, yb, c);
    }
}
