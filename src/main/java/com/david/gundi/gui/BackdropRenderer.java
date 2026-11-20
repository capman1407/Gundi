package com.david.gundi.gui;

import com.david.gundi.util.ErrorReporter;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;

import java.util.Random;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Animated full-screen backgrounds drawn behind the GUI windows (called from WidgetScreenMixin every frame,
 * after Meteor sets the screen's fade alpha, so the backdrop fades in and out with the GUI).
 *
 * Everything is plain quads and triangles on Meteor's GUI renderer, so it costs a few thousand shapes per frame.
 * Particle-type backdrops keep their particles between frames in the arrays below.
 */
public final class BackdropRenderer {
    private BackdropRenderer() {}

    private static final Random RANDOM = new Random(1337);
    private static long lastNanos;
    private static double phase; // animation time, advanced by speed so changing speed never jumps

    // Shared particle storage (x, y, z/size, velocity x, velocity y, extra) used by the particle backdrops
    private static double[][] parts = new double[0][];
    private static ThemeStyles.Backdrop partsFor;

    public static void render(GuiRenderer r, GundiTheme t) {
        try {
            long now = System.nanoTime();
            double dt = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1e9);
            lastNanos = now;
            double speed = t.backdropSpeed.get();
            phase += dt * speed;

            double W = Utils.getWindowWidth(), H = Utils.getWindowHeight();
            double o = t.backdropOpacity.get();
            Color c1 = t.backdrop1.get(), c2 = t.backdrop2.get();
            double density = t.backdropDensity.get();

            switch (t.backdrop.get()) {
                case Dim -> r.quad(0, 0, W, H, black(120 * o));
                case Gradient -> gradient(r, W, H, o, c1, c2);
                case Aurora -> aurora(r, W, H, o, c1, c2);
                case Grid -> grid(r, W, H, o, c1, c2);
                case Particles -> particles(r, t, W, H, o, c1, c2, density, dt * speed);
                case Constellation -> constellation(r, t, W, H, o, c1, c2, density, dt * speed);
                case Starfield -> starfield(r, W, H, o, c1, c2, density, dt * speed);
                case Rain -> rain(r, t, W, H, o, c1, c2, density, dt * speed);
                case Embers -> embers(r, W, H, o, c1, c2, density, dt * speed);
                case Waves -> waves(r, W, H, o, c1, c2);
                default -> {}
            }

            if (t.cursorGlow.get()) cursorLight(r, W, H, AppleWidget.alpha(t.glowColor.get(), 1), o);
            if (t.vignette.get()) vignette(r, W, H);
        } catch (Exception e) {
            ErrorReporter.report("gui-backdrop", "render", e);
        }
    }

    // Backdrops

    /** Four corner colors slowly trading places. */
    private static void gradient(GuiRenderer r, double W, double H, double o, Color c1, Color c2) {
        r.quad(0, 0, W, H, black(90 * o));
        double p = phase * 0.5;
        r.quad(0, 0, W, H,
            fade(mix(c1, c2, wave(p)), o), fade(mix(c1, c2, wave(p + 1.6)), o),
            fade(mix(c1, c2, wave(p + 3.1)), o), fade(mix(c1, c2, wave(p + 4.7)), o));
    }

    /** Northern lights: three wavy curtains of light, bright at the bottom edge and fading upwards. */
    private static void aurora(GuiRenderer r, double W, double H, double o, Color c1, Color c2) {
        r.quad(0, 0, W, H, black(70 * o));
        int cols = 160;
        double cw = W / cols;
        for (int b = 0; b < 3; b++) {
            for (int i = 0; i < cols; i++) {
                double x = i * cw;
                double yc = H * (0.26 + 0.11 * b)
                    + Math.sin(i * 0.045 + phase * 0.6 + b * 2) * H * 0.07
                    + Math.sin(i * 0.11 - phase * 0.35 + b) * H * 0.025;
                double thick = H * (0.12 + 0.05 * Math.sin(i * 0.07 + phase * 0.9 + b));
                double k = 0.45 + 0.55 * wave(i * 0.09 + phase * 0.8 + b * 1.3);
                Color c = fade(mix(c1, c2, wave(i * 0.025 + phase * 0.3 + b * 1.7)), o * k * (0.9 - b * 0.2));
                Color clear = fade(c, 0);
                r.quad(x, yc - thick, cw + 0.5, thick, clear, clear, c, c);
                r.quad(x, yc, cw + 0.5, thick * 0.25, c, c, clear, clear);
            }
        }
    }

    /** Synthwave: striped sun on the horizon and a neon floor grid rushing towards you. */
    private static void grid(GuiRenderer r, double W, double H, double o, Color c1, Color c2) {
        double horizon = H * 0.58;

        // Sky
        r.quad(0, 0, W, horizon, black(150 * o), black(150 * o), fade(c1, o * 0.35), fade(c1, o * 0.35));

        // Sun: horizontal slices of a circle, colored top to bottom, with gaps cut into the lower half
        double R = H * 0.17, sx = W / 2, sy = horizon - R * 0.45;
        double row = Math.max(2, H / 300);
        for (double yy = sy - R; yy < Math.min(sy + R, horizon); yy += row) {
            double dy = (yy + row / 2) - sy;
            double half = Math.sqrt(Math.max(0, R * R - dy * dy));
            double k = (yy - (sy - R)) / (2 * R);
            if (k > 0.5) {
                // stripes get thicker towards the bottom and drift down slowly
                double stripe = ((yy - sy) * 0.08 - phase * 1.2) % 1;
                if (stripe < 0) stripe += 1;
                if (stripe < (k - 0.5) * 1.1) continue;
            }
            r.quad(sx - half, yy, half * 2, row, fade(mix(c2, c1, k), o * 0.95));
        }

        // Floor
        r.quad(0, horizon, W, H - horizon, black(170 * o));
        double lw = Math.max(1, H / 600);

        // Lines towards the vanishing point
        int n = 14;
        for (int i = -n; i <= n; i++) {
            double bx = W / 2 + i * W / 9.0;
            RoundRender.line(r, W / 2 + i * W / 9.0 * 0.04, horizon, bx, H, lw, fade(c2, o * 0.55));
        }

        // Horizontal lines moving closer (perspective: distance on screen ~ 1 / depth)
        double f = (phase * 0.8) % 1;
        for (int k = 0; k < 16; k++) {
            double z = (k + 1 - f) * 0.55;
            double yy = horizon + (H - horizon) * 0.5 / z;
            if (yy > H || yy <= horizon) continue;
            double a = Math.min(1, (yy - horizon) / (H - horizon) * 4);
            r.quad(0, yy, W, lw, fade(c1, o * 0.6 * a));
        }

        // Glowing horizon line
        r.quad(0, horizon - lw * 6, W, lw * 6, fade(c1, 0), fade(c1, 0), fade(c1, o * 0.5), fade(c1, o * 0.5));
        r.quad(0, horizon, W, lw * 1.5, fade(mix(c1, new Color(255, 255, 255), 0.4), o));
    }

    /** Soft dots drifting upwards and twinkling. They move out of the way of your mouse. */
    private static void particles(GuiRenderer r, GundiTheme t, double W, double H, double o, Color c1, Color c2, double density, double dt) {
        r.quad(0, 0, W, H, black(60 * o));
        int count = (int) (80 * density);
        ensure(ThemeStyles.Backdrop.Particles, count, p -> {
            p[0] = RANDOM.nextDouble();
            p[1] = RANDOM.nextDouble();
            p[2] = 0.5 + RANDOM.nextDouble() * 1.5; // size
            p[3] = (RANDOM.nextDouble() - 0.5) * 0.01;
            p[4] = -0.01 - RANDOM.nextDouble() * 0.025;
            p[5] = RANDOM.nextDouble() * 10; // twinkle phase / color
        });

        double mx = mouseX(), my = mouseY();
        for (double[] p : parts) {
            p[0] += p[3] * dt + Math.sin(phase + p[5]) * 0.0006;
            p[1] += p[4] * dt;
            double px = p[0] * W, py = p[1] * H;

            // Push away from the mouse
            double ddx = px - mx, ddy = py - my, d = Math.hypot(ddx, ddy), push = H * 0.12;
            if (d < push && d > 1) {
                p[0] += ddx / d * (push - d) / push * 0.004;
                p[1] += ddy / d * (push - d) / push * 0.004;
            }
            wrap(p);

            double s = p[2] * H / 360;
            double tw = 0.5 + 0.5 * Math.sin(phase * 2 + p[5] * 3);
            Color c = fade(mix(c1, c2, (p[5] % 1)), o * (0.4 + 0.6 * tw));
            r.quad(px - s * 2.5, py - s * 2.5, s * 5, s * 5, GuiRenderer.CIRCLE, fade(c, 0.25));
            r.quad(px - s, py - s, s * 2, s * 2, GuiRenderer.CIRCLE, c);
        }
    }

    /** Points floating around, joined by lines when they come close (and to your mouse). */
    private static void constellation(GuiRenderer r, GundiTheme t, double W, double H, double o, Color c1, Color c2, double density, double dt) {
        r.quad(0, 0, W, H, black(90 * o));
        int count = (int) (55 * density);
        ensure(ThemeStyles.Backdrop.Constellation, count, p -> {
            p[0] = RANDOM.nextDouble();
            p[1] = RANDOM.nextDouble();
            p[2] = 0.8 + RANDOM.nextDouble();
            double a = RANDOM.nextDouble() * Math.PI * 2, v = 0.008 + RANDOM.nextDouble() * 0.012;
            p[3] = Math.cos(a) * v;
            p[4] = Math.sin(a) * v;
            p[5] = RANDOM.nextDouble();
        });

        double link = Math.min(W, H) * 0.16;
        double lw = Math.max(1, H / 900);
        double mx = mouseX(), my = mouseY();

        for (double[] p : parts) {
            p[0] += p[3] * dt;
            p[1] += p[4] * dt;
            if (p[0] < 0 || p[0] > 1) p[3] = -p[3];
            if (p[1] < 0 || p[1] > 1) p[4] = -p[4];
            p[0] = Math.max(0, Math.min(1, p[0]));
            p[1] = Math.max(0, Math.min(1, p[1]));
        }

        for (int i = 0; i < parts.length; i++) {
            double ax = parts[i][0] * W, ay = parts[i][1] * H;
            for (int j = i + 1; j < parts.length; j++) {
                double bx = parts[j][0] * W, by = parts[j][1] * H;
                double d = Math.hypot(ax - bx, ay - by);
                if (d < link) RoundRender.line(r, ax, ay, bx, by, lw, fade(mix(c1, c2, parts[i][5]), o * 0.6 * (1 - d / link)));
            }
            double dm = Math.hypot(ax - mx, ay - my);
            if (dm < link * 1.4) RoundRender.line(r, ax, ay, mx, my, lw, fade(c2, o * 0.7 * (1 - dm / (link * 1.4))));

            double s = parts[i][2] * H / 400;
            r.quad(ax - s, ay - s, s * 2, s * 2, GuiRenderer.CIRCLE, fade(mix(c1, c2, parts[i][5]), o));
        }
    }

    /** Warp speed: stars flying out of the middle of the screen, stretching into streaks. */
    private static void starfield(GuiRenderer r, double W, double H, double o, Color c1, Color c2, double density, double dt) {
        r.quad(0, 0, W, H, black(150 * o));
        int count = (int) (180 * density);
        ensure(ThemeStyles.Backdrop.Starfield, count, BackdropRenderer::newStar);

        double cx = W / 2, cy = H / 2, scale = Math.max(W, H) * 0.5;
        for (double[] p : parts) {
            double oldZ = p[2];
            p[2] -= dt * 0.45;
            if (p[2] <= 0.02) {
                newStar(p);
                p[2] = 1;
                continue;
            }

            double x1 = cx + p[0] / oldZ * scale, y1 = cy + p[1] / oldZ * scale;
            double x2 = cx + p[0] / p[2] * scale, y2 = cy + p[1] / p[2] * scale;
            if (x2 < -50 || x2 > W + 50 || y2 < -50 || y2 > H + 50) {
                newStar(p);
                p[2] = 1;
                continue;
            }

            double near = 1 - p[2];
            // Streak from where it was a moment ago, longer the faster it moves
            double sx = x2 - (x2 - x1) * 6, sy = y2 - (y2 - y1) * 6;
            Color c = fade(mix(c1, c2, p[5]), o * Math.min(1, near * 1.6));
            RoundRender.line(r, sx, sy, x2, y2, Math.max(1, near * H / 300), c);
        }
    }

    private static void newStar(double[] p) {
        p[0] = (RANDOM.nextDouble() - 0.5) * 2;
        p[1] = (RANDOM.nextDouble() - 0.5) * 2;
        p[2] = 0.1 + RANDOM.nextDouble() * 0.9;
        p[5] = RANDOM.nextDouble();
    }

    /** Digital rain: columns of falling blocks with bright heads and fading, flickering tails. */
    private static void rain(GuiRenderer r, GundiTheme t, double W, double H, double o, Color c1, Color c2, double density, double dt) {
        r.quad(0, 0, W, H, black(150 * o));
        double cell = Math.max(8, H / 70);
        int cols = (int) Math.ceil(W / cell * Math.min(1, 0.55 * density));
        double spacing = W / Math.max(1, cols);

        ensure(ThemeStyles.Backdrop.Rain, cols, p -> {
            p[0] = RANDOM.nextDouble() * 1.3 - 0.3; // head position 0..1 (starts above the screen)
            p[3] = 0.15 + RANDOM.nextDouble() * 0.35; // speed
            p[4] = 8 + RANDOM.nextInt(18); // trail length in cells
            p[5] = RANDOM.nextDouble() * 1000;
        });

        Color white = new Color(255, 255, 255);
        for (int i = 0; i < parts.length; i++) {
            double[] p = parts[i];
            p[0] += p[3] * dt;
            if (p[0] - p[4] * cell / H > 1) {
                p[0] = -RANDOM.nextDouble() * 0.3;
                p[3] = 0.15 + RANDOM.nextDouble() * 0.35;
            }

            double x = i * spacing + (spacing - cell * 0.7) / 2;
            int head = (int) (p[0] * H / cell);
            int len = (int) p[4];
            long tick = (long) (phase * 12);
            for (int k = 0; k < len; k++) {
                int row = head - k;
                double y = row * cell;
                if (y < -cell || y > H) continue;
                // Some cells blink off, which reads like changing glyphs
                double n = hash(i * 131 + row, tick / (2 + (row & 3)));
                if (k > 0 && n < 0.25) continue;
                double fadeK = 1 - (double) k / len;
                Color c = k == 0 ? mix(c1, white, 0.6) : mix(c2, c1, fadeK);
                double w = cell * (0.35 + 0.35 * n);
                r.quad(x + (cell * 0.7 - w) / 2, y + cell * 0.15, w, cell * 0.7, fade(c, o * fadeK * (k == 0 ? 1 : 0.8)));
            }
        }
    }

    /** Glowing sparks rising slowly from the bottom and burning out. */
    private static void embers(GuiRenderer r, double W, double H, double o, Color c1, Color c2, double density, double dt) {
        r.quad(0, 0, W, H, black(80 * o));
        r.quad(0, H * 0.6, W, H * 0.4, fade(c2, 0), fade(c2, 0), fade(c2, o * 0.35), fade(c2, o * 0.35));

        int count = (int) (70 * density);
        ensure(ThemeStyles.Backdrop.Embers, count, BackdropRenderer::newEmber);

        for (double[] p : parts) {
            p[1] -= p[4] * dt;
            p[0] += Math.sin(phase * 0.9 + p[5] * 6) * 0.0005;
            p[3] -= dt * 0.12; // life
            if (p[3] <= 0 || p[1] < -0.05) newEmber(p);

            double life = Math.max(0, p[3]);
            double a = Math.min(1, life * 3) * Math.min(1, (1 - life) * 6 + 0.2);
            double flicker = 0.75 + 0.25 * Math.sin(phase * 9 + p[5] * 40);
            double px = p[0] * W, py = p[1] * H, s = p[2] * H / 500;
            Color c = fade(mix(c1, c2, p[5]), o * a * flicker);
            r.quad(px - s * 4, py - s * 4, s * 8, s * 8, GuiRenderer.CIRCLE, fade(c, 0.18));
            r.quad(px - s, py - s, s * 2, s * 2, GuiRenderer.CIRCLE, c);
        }
    }

    private static void newEmber(double[] p) {
        p[0] = RANDOM.nextDouble();
        p[1] = 0.75 + RANDOM.nextDouble() * 0.3;
        p[2] = 0.6 + RANDOM.nextDouble() * 1.6;
        p[3] = 0.6 + RANDOM.nextDouble() * 0.4; // life 1 -> 0
        p[4] = 0.03 + RANDOM.nextDouble() * 0.06;
        p[5] = RANDOM.nextDouble();
    }

    /** Layered sine waves rolling along the bottom of the screen. */
    private static void waves(GuiRenderer r, double W, double H, double o, Color c1, Color c2) {
        r.quad(0, 0, W, H, black(70 * o));
        int cols = 120;
        double cw = W / cols;
        for (int l = 0; l < 4; l++) {
            double base = H * (0.62 + 0.08 * l);
            double amp = H * (0.035 - 0.005 * l);
            Color c = fade(mix(c1, c2, l / 3.0), o * 0.35);
            for (int i = 0; i < cols; i++) {
                double x = i * cw;
                double y = base + Math.sin(i * 0.09 + phase * (0.9 + l * 0.25) + l * 1.7) * amp
                    + Math.sin(i * 0.23 - phase * 0.6 + l) * amp * 0.35;
                r.quad(x, y, cw + 0.5, H - y, c, c, fade(c, 0.4), fade(c, 0.4));
            }
        }
    }

    // Overlays

    /** Soft light under the mouse: stacked transparent discs give a smooth falloff. */
    private static void cursorLight(GuiRenderer r, double W, double H, Color c, double o) {
        double mx = mouseX(), my = mouseY();
        double R = Math.min(W, H) * 0.28;
        for (int i = 0; i < 6; i++) {
            double rr = R * (1 - i / 6.0);
            r.quad(mx - rr, my - rr, rr * 2, rr * 2, GuiRenderer.CIRCLE, fade(c, o * 0.09));
        }
    }

    /** Dark edges. */
    private static void vignette(GuiRenderer r, double W, double H) {
        Color dark = black(150), clear = black(0);
        double v = H * 0.28, hz = W * 0.18;
        r.quad(0, 0, W, v, dark, dark, clear, clear);
        r.quad(0, H - v, W, v, clear, clear, dark, dark);
        r.quad(0, 0, hz, H, dark, clear, clear, dark);
        r.quad(W - hz, 0, hz, H, clear, dark, dark, clear);
    }

    // Helpers

    private interface Init {
        void init(double[] p);
    }

    /** Makes sure the particle arrays are for this backdrop and have the right count. */
    private static void ensure(ThemeStyles.Backdrop kind, int count, Init init) {
        if (partsFor == kind && parts.length == count) return;
        double[][] next = new double[count][];
        for (int i = 0; i < count; i++) {
            if (partsFor == kind && i < parts.length) next[i] = parts[i];
            else {
                next[i] = new double[6];
                init.init(next[i]);
            }
        }
        parts = next;
        partsFor = kind;
    }

    private static void wrap(double[] p) {
        if (p[0] < -0.02) p[0] += 1.04;
        if (p[0] > 1.02) p[0] -= 1.04;
        if (p[1] < -0.02) p[1] += 1.04;
        if (p[1] > 1.02) p[1] -= 1.04;
    }

    private static double mouseX() {
        double ww = Math.max(1, mc.getWindow().getWidth());
        return mc.mouse.getX() * Utils.getWindowWidth() / ww;
    }

    private static double mouseY() {
        double wh = Math.max(1, mc.getWindow().getHeight());
        return mc.mouse.getY() * Utils.getWindowHeight() / wh;
    }

    private static double wave(double x) {
        return 0.5 + 0.5 * Math.sin(x);
    }

    private static double hash(long a, long b) {
        return com.david.gundi.gui.widgets.AppleWindow.noise(a, b);
    }

    private static Color black(double a) {
        return new Color(0, 0, 0, (int) Math.max(0, Math.min(255, a)));
    }

    private static Color mix(Color a, Color b, double t) {
        return RoundRender.mix(a, b, t);
    }

    private static Color fade(Color c, double f) {
        return RoundRender.alpha(c, f);
    }
}
