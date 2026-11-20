package com.david.gundi.util;

import com.david.gundi.gui.ThemeStyles.CornerStyle;

/**
 * Corner geometry shared by the GUI shapes (gui/RoundRender) and the HUD shapes (util/HudShapes), so a theme's
 * corner style (Round, Chamfer, Square, Pixel) looks exactly the same on windows and on HUD panels.
 */
public final class Corners {
    /** Points per corner. Every style writes the same number, so outer and inner outlines always line up. */
    public static final int STEPS = 8;

    private Corners() {}

    /** Points around a rectangle, clockwise from the top-left corner, as x, y, x, y, ... Radii: tl, tr, br, bl. */
    public static double[] outline(double x, double y, double w, double h, double tl, double tr, double br, double bl, CornerStyle style) {
        double max = Math.min(w, h) / 2;
        tl = clamp(tl, max);
        tr = clamp(tr, max);
        br = clamp(br, max);
        bl = clamp(bl, max);

        double[] p = new double[4 * (STEPS + 1) * 2];
        int i = 0;
        // Screen y points down, so these angles go clockwise on screen: left (180) -> up (270) -> right (0/360) -> down (90).
        i = corner(p, i, x + tl, y + tl, tl, 180, style);
        i = corner(p, i, x + w - tr, y + tr, tr, 270, style);
        i = corner(p, i, x + w - br, y + h - br, br, 0, style);
        corner(p, i, x + bl, y + h - bl, bl, 90, style);
        return p;
    }

    /** How far a corner pulls the edge inwards, at distance d from the top/bottom (or left/right) edge. */
    public static double inset(double radius, double d, CornerStyle style) {
        if (radius <= 0 || d >= radius) return 0;
        switch (style) {
            case Square: return 0;
            case Chamfer: return radius - d;
            case Pixel: {
                // Stepped: the inset stays flat inside each of the 4 steps
                double step = radius / 4;
                double band = Math.min(3, Math.floor(d / step));
                return roundInset(radius, (band + 1) * step);
            }
            default: return roundInset(radius, d);
        }
    }

    public static double clamp(double radius, double max) {
        return Math.max(0, Math.min(radius, max));
    }

    private static double roundInset(double radius, double d) {
        if (d >= radius) return 0;
        double k = radius - d;
        return radius - Math.sqrt(Math.max(0, radius * radius - k * k));
    }

    /** Always writes CORNER_STEPS + 1 points, whatever the style, so outer and inner outlines line up. */
    private static int corner(double[] p, int i, double cx, double cy, double radius, double startDeg, CornerStyle style) {
        int n = STEPS;
        double a0 = Math.toRadians(startDeg), a1 = Math.toRadians(startDeg + 90);
        double sx = cx + Math.cos(a0) * radius, sy = cy + Math.sin(a0) * radius;
        double ex = cx + Math.cos(a1) * radius, ey = cy + Math.sin(a1) * radius;

        switch (style) {
            case Square -> {
                // The sharp corner point (where the two edges meet), repeated
                double kx = cx + (Math.cos(a0) + Math.cos(a1)) * radius, ky = cy + (Math.sin(a0) + Math.sin(a1)) * radius;
                for (int s = 0; s <= n; s++) {
                    p[i++] = kx;
                    p[i++] = ky;
                }
            }
            case Chamfer -> {
                // Straight cut from start to end
                for (int s = 0; s <= n; s++) {
                    double t = (double) s / n;
                    p[i++] = sx + (ex - sx) * t;
                    p[i++] = sy + (ey - sy) * t;
                }
            }
            case Pixel -> {
                // Staircase: 4 arc points with an outer "step" point between each pair (2 * 4 + 1 = 9 points)
                int steps = n / 2;
                double px = sx, py = sy;
                p[i++] = px;
                p[i++] = py;
                for (int s = 1; s <= steps; s++) {
                    double a = a0 + (a1 - a0) * s / steps;
                    double qx = cx + Math.cos(a) * radius, qy = cy + Math.sin(a) * radius;
                    // Of the two possible step corners, take the one further from the center (outside the curve)
                    double d1 = (px - cx) * (px - cx) + (qy - cy) * (qy - cy);
                    double d2 = (qx - cx) * (qx - cx) + (py - cy) * (py - cy);
                    p[i++] = d1 >= d2 ? px : qx;
                    p[i++] = d1 >= d2 ? qy : py;
                    p[i++] = qx;
                    p[i++] = qy;
                    px = qx;
                    py = qy;
                }
            }
            default -> {
                for (int s = 0; s <= n; s++) {
                    double a = a0 + (a1 - a0) * s / n;
                    p[i++] = cx + Math.cos(a) * radius;
                    p[i++] = cy + Math.sin(a) * radius;
                }
            }
        }
        return i;
    }
}
