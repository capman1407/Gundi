package com.david.gundi.gui;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import com.david.gundi.util.Colors;
import com.david.gundi.util.Corners;
import meteordevelopment.meteorclient.utils.render.color.Color;

/**
 * Rounded shapes for the GUI. Meteor's GUI renderer only has plain rectangles, triangles and textures,
 * so rounded rectangles are built from triangles: the outline (4 straight sides + 4 curved corners)
 * is filled like a pizza from the center.
 */
public final class RoundRender {
    private static final int CORNER_STEPS = 8; // line pieces per rounded corner

    /**
     * Corner shape used by every shape below. The active theme sets this each frame (GundiTheme.beforeRender),
     * so buttons, modules, windows and switches all follow the theme's corner-style without passing it around.
     */
    public static ThemeStyles.CornerStyle corners = ThemeStyles.CornerStyle.Round;

    private RoundRender() {}

    /** Filled rounded rectangle, same radius on all corners. */
    public static void rect(GuiRenderer r, double x, double y, double w, double h, double radius, Color c) {
        rect(r, x, y, w, h, radius, radius, radius, radius, c);
    }

    /** Filled rounded rectangle. Radii go top-left, top-right, bottom-right, bottom-left. */
    public static void rect(GuiRenderer r, double x, double y, double w, double h,
                            double tl, double tr, double br, double bl, Color c) {
        if (w <= 0 || h <= 0 || c.a <= 0) return;

        double[] p = outline(x, y, w, h, tl, tr, br, bl);
        double cx = x + w / 2, cy = y + h / 2;
        int n = p.length / 2;

        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            triangle(r, cx, cy, p[i * 2], p[i * 2 + 1], p[j * 2], p[j * 2 + 1], c);
        }
    }

    /**
     * Rounded rectangle that fades from one color at the top to another at the bottom (used for the glass look).
     * Built from thin horizontal strips in the rounded parts and one smooth gradient rectangle in the middle.
     */
    public static void gradientRect(GuiRenderer r, double x, double y, double w, double h,
                                    double tl, double tr, double br, double bl, Color top, Color bottom) {
        if (w <= 0 || h <= 0) return;

        double max = Math.min(w, h) / 2;
        tl = clamp(tl, max);
        tr = clamp(tr, max);
        br = clamp(br, max);
        bl = clamp(bl, max);

        double topBand = Math.max(tl, tr), bottomBand = Math.max(bl, br);
        int steps = CORNER_STEPS;

        // Rounded top part: strips get wider as they go down
        for (int i = 0; i < steps && topBand > 0; i++) {
            double ya = y + topBand * i / steps, yb = y + topBand * (i + 1) / steps;
            strip(r, x, w, ya, yb,
                inset(tl, ya - y), inset(tr, ya - y), inset(tl, yb - y), inset(tr, yb - y),
                colorAt(top, bottom, y, h, (ya + yb) / 2));
        }

        // Straight middle part: one smooth gradient
        double midTop = y + topBand, midH = h - topBand - bottomBand;
        if (midH > 0) {
            Color a = colorAt(top, bottom, y, h, midTop), b = colorAt(top, bottom, y, h, midTop + midH);
            r.quad(x, midTop, w, midH, a, a, b, b);
        }

        // Rounded bottom part: strips get narrower as they go down
        for (int i = 0; i < steps && bottomBand > 0; i++) {
            double ya = y + h - bottomBand + bottomBand * i / steps, yb = y + h - bottomBand + bottomBand * (i + 1) / steps;
            strip(r, x, w, ya, yb,
                inset(bl, y + h - ya), inset(br, y + h - ya), inset(bl, y + h - yb), inset(br, y + h - yb),
                colorAt(top, bottom, y, h, (ya + yb) / 2));
        }
    }

    /** How far a corner pulls the edge inwards, at distance d from the top/bottom edge. */
    private static double inset(double radius, double d) {
        return Corners.inset(radius, d, corners);
    }

    /** One horizontal slice between ya and yb, pulled in on the left/right by the given amounts. */
    private static void strip(GuiRenderer r, double x, double w, double ya, double yb,
                              double leftA, double rightA, double leftB, double rightB, Color c) {
        double x1 = x + leftA, x2 = x + w - rightA, x3 = x + w - rightB, x4 = x + leftB;
        triangle(r, x1, ya, x2, ya, x3, yb, c);
        triangle(r, x1, ya, x3, yb, x4, yb, c);
    }

    private static Color colorAt(Color top, Color bottom, double y, double h, double atY) {
        double t = Math.max(0, Math.min(1, (atY - y) / h));
        return new Color(
            (int) (top.r + (bottom.r - top.r) * t),
            (int) (top.g + (bottom.g - top.g) * t),
            (int) (top.b + (bottom.b - top.b) * t),
            (int) (top.a + (bottom.a - top.a) * t)
        );
    }

    /**
     * Thin bright line along a top edge that fades out towards both ends, like light catching the rim of glass.
     */
    public static void rimLight(GuiRenderer r, double x, double y, double w, double thickness, Color c) {
        if (w <= 0) return;
        Color clear = new Color(c.r, c.g, c.b, 0);
        double half = w / 2;
        r.quad(x, y, half, thickness, clear, c, c, clear);
        r.quad(x + half, y, w - half, thickness, c, clear, clear, c);
    }

    /**
     * Small floating glass surface (tooltips, dropdown lists): gradient tint + rim light, or a plain fill when
     * frosted-glass is off. Radii go top-left, top-right, bottom-right, bottom-left.
     */
    public static void glassSurface(GuiRenderer r, GundiTheme t, double x, double y, double w, double h,
                                    double tl, double tr, double br, double bl, Color base) {
        if (!t.frosted()) {
            rect(r, x, y, w, h, tl, tr, br, bl, base);
            return;
        }

        Color top = new Color(Math.min(255, base.r + 16), Math.min(255, base.g + 16), Math.min(255, base.b + 16), (int) (base.a * 0.85));
        gradientRect(r, x, y, w, h, tl, tr, br, bl, top, base);

        double line = Math.max(1, t.scale(1));
        double inset = Math.max(tl, tr);
        rimLight(r, x + inset, y, w - inset * 2, line, new Color(255, 255, 255, 60));
    }

    /** Rounded border line of the given thickness, drawn inside the rectangle's edge. */
    public static void border(GuiRenderer r, double x, double y, double w, double h, double radius, double thickness, Color c) {
        if (w <= 0 || h <= 0 || c.a <= 0 || thickness <= 0) return;

        double t = Math.min(thickness, Math.min(w, h) / 2);
        double[] outer = outline(x, y, w, h, radius, radius, radius, radius);
        double ri = Math.max(radius - t, 0);
        double[] inner = outline(x + t, y + t, w - 2 * t, h - 2 * t, ri, ri, ri, ri);
        int n = outer.length / 2;

        // Both outlines have the same number of points, so we can stitch them together with 2 triangles per piece.
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            triangle(r, outer[i * 2], outer[i * 2 + 1], outer[j * 2], outer[j * 2 + 1], inner[j * 2], inner[j * 2 + 1], c);
            triangle(r, outer[i * 2], outer[i * 2 + 1], inner[j * 2], inner[j * 2 + 1], inner[i * 2], inner[i * 2 + 1], c);
        }
    }

    /**
     * Soft shadow around a rounded rectangle: a few rings that get fainter further out.
     * Rings don't overlap and nothing is drawn under the rectangle itself, so a see-through panel stays clean.
     */
    public static void shadow(GuiRenderer r, double x, double y, double w, double h, double radius, double spread, int alpha) {
        int layers = 5;
        double step = spread / layers;
        double drop = spread * 0.25; // shadow sits a bit lower, like light from above

        for (int i = 0; i < layers; i++) {
            double e = step * (i + 1);
            int a = (int) (alpha * Math.pow(1 - (double) i / layers, 2) / 2.5);
            if (a <= 0) continue;
            border(r, x - e, y - e + drop, w + 2 * e, h + 2 * e, radius + e, step, new Color(0, 0, 0, a));
        }
    }

    /**
     * Smooth (anti-aliased) pill or circle using Meteor's circle texture for the round ends.
     * Only use with fully solid colors: the ends overlap the middle, which would show with see-through colors.
     */
    public static void pill(GuiRenderer r, double x, double y, double w, double h, Color c) {
        if (w <= h + 0.5) {
            r.quad(x, y, h, h, GuiRenderer.CIRCLE, c);
            return;
        }
        r.quad(x, y, h, h, GuiRenderer.CIRCLE, c);
        r.quad(x + w - h, y, h, h, GuiRenderer.CIRCLE, c);
        r.quad(x + h / 2, y, w - h, h, c);
    }

    /** Rounded rectangle that fades from one color on the left to another on the right. */
    public static void hGradientRect(GuiRenderer r, double x, double y, double w, double h,
                                     double tl, double tr, double br, double bl, Color left, Color right) {
        if (w <= 0 || h <= 0) return;
        double max = Math.min(w, h) / 2;
        tl = clamp(tl, max);
        tr = clamp(tr, max);
        br = clamp(br, max);
        bl = clamp(bl, max);

        double leftBand = Math.max(tl, bl), rightBand = Math.max(tr, br);
        int steps = CORNER_STEPS;

        // Rounded left part: vertical strips, taller further right
        for (int i = 0; i < steps && leftBand > 0; i++) {
            double xa = x + leftBand * i / steps, xb = x + leftBand * (i + 1) / steps;
            vStrip(r, xa, xb, y, h, inset(tl, xa - x), inset(bl, xa - x), inset(tl, xb - x), inset(bl, xb - x),
                mix(left, right, ((xa + xb) / 2 - x) / w));
        }

        double midX = x + leftBand, midW = w - leftBand - rightBand;
        if (midW > 0) r.quad(midX, y, midW, h, mix(left, right, (midX - x) / w), mix(left, right, (midX + midW - x) / w));

        for (int i = 0; i < steps && rightBand > 0; i++) {
            double xa = x + w - rightBand + rightBand * i / steps, xb = x + w - rightBand + rightBand * (i + 1) / steps;
            vStrip(r, xa, xb, y, h, inset(tr, x + w - xa), inset(br, x + w - xa), inset(tr, x + w - xb), inset(br, x + w - xb),
                mix(left, right, ((xa + xb) / 2 - x) / w));
        }
    }

    /** One vertical slice between xa and xb, pulled in at the top/bottom by the given amounts. */
    private static void vStrip(GuiRenderer r, double xa, double xb, double y, double h,
                               double topA, double bottomA, double topB, double bottomB, Color c) {
        triangle(r, xa, y + topA, xb, y + topB, xb, y + h - bottomB, c);
        triangle(r, xa, y + topA, xb, y + h - bottomB, xa, y + h - bottomA, c);
    }

    public static Color mix(Color a, Color b, double t) {
        return Colors.mix(a, b, t);
    }

    public static Color alpha(Color c, double f) {
        return Colors.alpha(c, f);
    }

    /**
     * Border whose color flows around the shape between a and b. phase (radians) rotates the colors,
     * so increasing it over time makes the light run around the edge.
     */
    public static void gradientBorder(GuiRenderer r, double x, double y, double w, double h, double radius,
                                      double thickness, Color a, Color b, double phase) {
        if (w <= 0 || h <= 0 || thickness <= 0) return;
        double t = Math.min(thickness, Math.min(w, h) / 2);
        double[] outer = outline(x, y, w, h, radius, radius, radius, radius);
        double ri = Math.max(radius - t, 0);
        double[] inner = outline(x + t, y + t, w - 2 * t, h - 2 * t, ri, ri, ri, ri);
        double cx = x + w / 2, cy = y + h / 2;
        int n = outer.length / 2;

        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double ox1 = outer[i * 2], oy1 = outer[i * 2 + 1], ox2 = outer[j * 2], oy2 = outer[j * 2 + 1];
            double ix1 = inner[i * 2], iy1 = inner[i * 2 + 1], ix2 = inner[j * 2], iy2 = inner[j * 2 + 1];
            double len = Math.hypot(ox2 - ox1, oy2 - oy1);
            if (len < 1e-6) continue;

            // Long straight edges are cut into pieces so the color can change along them
            int pieces = Math.max(1, (int) Math.ceil(len / 6));
            for (int k = 0; k < pieces; k++) {
                double f1 = (double) k / pieces, f2 = (double) (k + 1) / pieces;
                double ax = ox1 + (ox2 - ox1) * f1, ay = oy1 + (oy2 - oy1) * f1;
                double bx = ox1 + (ox2 - ox1) * f2, by = oy1 + (oy2 - oy1) * f2;
                double qx = ix1 + (ix2 - ix1) * f2, qy = iy1 + (iy2 - iy1) * f2;
                double dx = ix1 + (ix2 - ix1) * f1, dy = iy1 + (iy2 - iy1) * f1;

                // Angle around the center, squashed so wide windows still get an even sweep
                double ang = Math.atan2(((ay + by) / 2 - cy) * w / Math.max(h, 1), (ax + bx) / 2 - cx);
                Color c = mix(a, b, 0.5 + 0.5 * Math.sin(ang + phase));
                triangle(r, ax, ay, bx, by, qx, qy, c);
                triangle(r, ax, ay, qx, qy, dx, dy, c);
            }
        }
    }

    /** Colored light around a shape: rings that fade out, nothing drawn under the shape itself. */
    public static void glow(GuiRenderer r, double x, double y, double w, double h, double radius, double size, Color c) {
        if (c.a <= 0 || size <= 0) return;
        int layers = 6;
        double step = size / layers;
        for (int i = 0; i < layers; i++) {
            double e = step * (i + 1);
            double f = Math.pow(1 - (double) i / layers, 2);
            border(r, x - e, y - e, w + 2 * e, h + 2 * e, radius + e, step, alpha(c, f * 0.55));
        }
    }

    /** Straight line of the given thickness between two points. */
    public static void line(GuiRenderer r, double x1, double y1, double x2, double y2, double thickness, Color c) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) return;
        double nx = -dy / len * thickness / 2, ny = dx / len * thickness / 2;
        triangle(r, x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, c);
        triangle(r, x1 + nx, y1 + ny, x2 - nx, y2 - ny, x1 - nx, y1 - ny, c);
    }

    /** Sci-fi corner brackets: an L shape in each corner. */
    public static void brackets(GuiRenderer r, double x, double y, double w, double h, double len, double t, Color c) {
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
    /** Points around the shape in the current corner style (see util/Corners). */
    private static double[] outline(double x, double y, double w, double h, double tl, double tr, double br, double bl) {
        return Corners.outline(x, y, w, h, tl, tr, br, bl, corners);
    }

    private static double clamp(double radius, double max) {
        return Corners.clamp(radius, max);
    }

    /**
     * Meteor's GUI hides triangles whose corners are in the "wrong" order (back-face culling).
     * This puts them in the same order Meteor's own rectangles use, so they always show up.
     */
    public static void triangle(GuiRenderer r, double x1, double y1, double x2, double y2, double x3, double y3, Color c) {
        double area = (x2 - x1) * (y3 - y1) - (y2 - y1) * (x3 - x1);
        if (Math.abs(area) < 1e-9) return; // flat triangle, nothing to draw
        if (area > 0) r.triangle(x1, y1, x3, y3, x2, y2, c);
        else r.triangle(x1, y1, x2, y2, x3, y3, c);
    }
}
