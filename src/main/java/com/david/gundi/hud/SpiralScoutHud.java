package com.david.gundi.hud;

import com.david.gundi.Gundi;
import com.david.gundi.modules.SpiralScout;
import com.david.gundi.util.CrispText;
import com.david.gundi.util.ErrorReporter;
import com.david.gundi.util.GlassStyle;
import com.david.gundi.util.HudShapes;
import com.david.gundi.util.ThemeLink;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Status card for the Spiral Scout module.
 *
 *   ┌───────────────────────────────┐
 *   │ Spiral Scout        ● Flying  │   Flying / Loading chunks / Returning / Paused / Off
 *   │ ┌───────────────────────────┐ │
 *   │ │  mini map: loaded bands,  │ │   bands = area loaded along each flown leg
 *   │ │  spiral, current leg, you │ │
 *   │ └───────────────────────────┘ │
 *   │ Covered       Chunks loaded   │
 *   │ Ring · Leg    Radius          │
 *   │ Distance out  Time            │
 *   │ Next corner           340 m   │
 *   │ ████████████░░░░░░░░░░░░░░░░  │
 *   └───────────────────────────────┘
 */
public class SpiralScoutHud extends GundiHud {
    public static final HudElementInfo<SpiralScoutHud> INFO = new HudElementInfo<>(Gundi.HUD_GROUP, "spiral-scout",
        "Status of Spiral Scout: progress, chunks loaded and a mini map of the spiral.", SpiralScoutHud::new);

    private static final String NAME = "spiral-scout-hud";

    // Status colors have a fixed meaning, so they stay off the theme
    private static final Color GREEN = new Color(48, 209, 88);
    private static final Color AMBER = new Color(255, 159, 10);
    private static final Color BLUE = new Color(10, 132, 255);
    private static final Color GREY = new Color(152, 152, 157);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final ThemeLink link = new ThemeLink(sgGeneral);
    private final SettingGroup sgLook = settings.createGroup("Style");
    private final SettingGroup sgColors = settings.createGroup("Colors", false);

    private final Setting<Boolean> showMap = sgGeneral.add(new BoolSetting.Builder()
        .name("show-map").description("Mini map of the spiral and the area loaded along it.")
        .defaultValue(true).build());

    private final Setting<Boolean> onlyWhenActive = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-active").description("Hide the card while Spiral Scout is off.")
        .defaultValue(false).build());

    private final Setting<Double> scale = sgLook.add(new DoubleSetting.Builder()
        .name("scale").description("Size of the card.").defaultValue(1).range(0.5, 3).build());

    private final Setting<SettingColor> textColor = sgColors.add(new ColorSetting.Builder()
        .name("text-color").description("Values and title.").defaultValue(new SettingColor(245, 245, 247)).visible(link.own()).build());

    private final Setting<SettingColor> labelColor = sgColors.add(new ColorSetting.Builder()
        .name("label-color").description("Dim labels.").defaultValue(new SettingColor(152, 152, 157)).visible(link.own()).build());

    private final Setting<SettingColor> accent = sgColors.add(new ColorSetting.Builder()
        .name("accent").description("Progress bar, current leg and loaded area.").defaultValue(new SettingColor(191, 90, 242)).visible(link.own()).build());

    private final Setting<SettingColor> tileColor = sgColors.add(new ColorSetting.Builder()
        .name("tile-color").description("Background of the map and the progress bar.").defaultValue(new SettingColor(255, 255, 255, 14)).visible(link.own()).build());

    private final GlassStyle panel = new GlassStyle(settings, link, new GlassStyle.Defaults(
        new SettingColor(20, 21, 26, 190), 14, false, new SettingColor(191, 90, 242, 70)));

    public SpiralScoutHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer r) {
        try {
            link.begin();
            double s = scale.get();
            SpiralScout mod = Modules.get().get(SpiralScout.class);
            boolean active = mod != null && mod.isActive();
            boolean data = mod != null && mod.hasSpiralHere();

            if (onlyWhenActive.get() && !active && !isInEditor()) {
                setSize(200 * s, 40 * s);
                return;
            }

            double pad = 11 * s, gap = 8 * s, w = 200 * s;
            double title = 13 * s, label = 9.5 * s, value = 13 * s, small = 10 * s;
            double titleH = CrispText.height(r, title), labelH = CrispText.height(r, label), valueH = CrispText.height(r, value);
            double mapH = showMap.get() ? 112 * s : 0;
            double cellH = labelH + 2 * s + valueH;
            double barH = 5 * s;
            double h = pad + titleH + gap + (showMap.get() ? mapH + gap : 0) + cellH * 3 + gap * 3
                + CrispText.height(r, small) + 4 * s + barH + pad;

            setSize(w, h);
            panel.draw(r, x, y, w, h, s, 1);

            Color text = link.text(textColor), dim = link.dim(labelColor), acc = link.accent(accent), tile = link.tile(tileColor);
            double cx = x + pad, cy = y + pad, inner = w - pad * 2;

            // ── title + status pill ──
            CrispText.draw(r, "Spiral Scout", cx, cy, text, false, title);
            String status;
            Color statusColor;
            if (!active) { status = data ? "Paused" : "Off"; statusColor = GREY; }
            else if (mod.isReturning()) { status = "Returning"; statusColor = BLUE; }
            else if (mod.isWaiting()) { status = "Loading chunks"; statusColor = AMBER; }
            else { status = "Flying"; statusColor = GREEN; }
            double sw = CrispText.width(r, status, small);
            double pillW = sw + 20 * s, pillH = titleH + 2 * s;
            double pillX = x + w - pad - pillW;
            HudShapes.rect(r, pillX, cy - 1 * s, pillW, pillH, pillH / 2, HudShapes.scaleAlpha(statusColor, 0.18));
            HudShapes.circle(r, pillX + 8 * s, cy - 1 * s + pillH / 2, 2.6 * s, statusColor);
            CrispText.draw(r, status, pillX + 14 * s, cy - 1 * s + (pillH - CrispText.height(r, small)) / 2, statusColor, false, small);
            cy += titleH + gap;

            // ── mini map ──
            if (showMap.get()) {
                HudShapes.rect(r, cx, cy, inner, mapH, 9 * s, tile);
                if (data) drawMap(r, mod, active, cx, cy, inner, mapH, s, acc, dim, text);
                else {
                    String hint = "Turn on Spiral Scout to start";
                    double hw = CrispText.width(r, hint, small);
                    CrispText.draw(r, hint, cx + (inner - hw) / 2, cy + (mapH - CrispText.height(r, small)) / 2, dim, false, small);
                }
                cy += mapH + gap;
            }

            // ── stats ──
            String covered = data ? String.format("%.2f km²", mod.coveredKm2()) : "—";
            String chunks = data && active ? String.format("%,d", mod.chunksSeen()) : "—";
            String ringLeg = data ? mod.ring() + " · " + mod.legNumber() : "—";
            String radius = data && active ? mod.radius() + " / " + (mod.gapChunks() + 1) / 2 + " ch" : data ? "gap " + mod.gapChunks() + " ch" : "—";
            String dist = data ? formatDistance(mod.distanceOut()) : "—";
            String time = active ? formatTime(mod.activeMillis()) : "—";

            double colW = inner / 2;
            cell(r, cx, cy, s, "Covered", covered, label, value, dim, text);
            cell(r, cx + colW, cy, s, "Chunks loaded", chunks, label, value, dim, text);
            cy += cellH + gap;
            cell(r, cx, cy, s, "Ring · Leg", ringLeg, label, value, dim, text);
            // loaded radius vs. half the gap: below half the gap, passes leave gaps between them
            boolean tooNarrow = data && active && mod.radius() < (mod.gapChunks() + 1) / 2;
            cell(r, cx + colW, cy, s, "Radius / needed", radius, label, value, dim, tooNarrow ? AMBER : text);
            cy += cellH + gap;
            cell(r, cx, cy, s, "Distance out", dist, label, value, dim, text);
            cell(r, cx + colW, cy, s, "Time", time, label, value, dim, text);
            cy += cellH + gap;

            // ── next corner progress ──
            String nextLabel = active && mod.isReturning() ? "Back to spiral" : "Next corner";
            String toCorner = !data ? "—"
                : active && mod.isReturning() && mc.player != null
                ? formatDistance(Math.hypot(mod.resumeX() - mc.player.getX(), mod.resumeZ() - mc.player.getZ()))
                : formatDistance(mod.toCorner());
            CrispText.draw(r, nextLabel, cx, cy, dim, false, small);
            double tw = CrispText.width(r, toCorner, small);
            CrispText.draw(r, toCorner, cx + inner - tw, cy, text, false, small);
            cy += CrispText.height(r, small) + 4 * s;
            HudShapes.rect(r, cx, cy, inner, barH, barH / 2, tile);
            double p = data ? mod.legProgress() : 0;
            Color barColor = active && mod.isWaiting() ? AMBER : active && mod.isReturning() ? BLUE : acc;
            if (p > 0) HudShapes.rect(r, cx, cy, Math.max(barH, inner * p), barH, barH / 2, barColor);
        } catch (Exception e) {
            ErrorReporter.report(NAME, "render", e);
        }
    }

    /** One stat: dim label with the value under it. */
    private void cell(HudRenderer r, double cx, double cy, double s, String lbl, String val, double label, double value, Color dim, Color text) {
        CrispText.draw(r, lbl, cx, cy, dim, false, label);
        CrispText.draw(r, val, cx, cy + CrispText.height(r, label) + 2 * s, text, false, value);
    }

    /** Loaded bands, flown legs, current leg, next corner and you, fitted into the box. North is up. */
    private void drawMap(HudRenderer r, SpiralScout mod, boolean active, double mx, double my, double mw, double mh, double s,
                         Color acc, Color dim, Color text) {
        int leg = mod.legNumber() - 1;
        double band = (active && mod.radius() > 0 ? mod.radius() : mod.gapChunks() / 2.0) * 16;
        double px = mc.player != null ? mc.player.getX() : mod.originX();
        double pz = mc.player != null ? mc.player.getZ() : mod.originZ();

        // World bounds: the spiral so far (plus its loaded band), the next corner and you
        double[] next = mod.cornerAt(leg + 1);
        double minX = Math.min(px, next[0]), maxX = Math.max(px, next[0]);
        double minZ = Math.min(pz, next[1]), maxZ = Math.max(pz, next[1]);
        for (int k = 0; k <= leg; k++) {
            double[] c = mod.cornerAt(k);
            minX = Math.min(minX, c[0] - band); maxX = Math.max(maxX, c[0] + band);
            minZ = Math.min(minZ, c[1] - band); maxZ = Math.max(maxZ, c[1] + band);
        }
        double m = 8 * s;
        double k = Math.min((mw - m * 2) / Math.max(16, maxX - minX), (mh - m * 2) / Math.max(16, maxZ - minZ));
        double ox = mx + mw / 2 - (minX + maxX) / 2 * k, oz = my + mh / 2 - (minZ + maxZ) / 2 * k;

        // Loaded bands along flown legs and the current one so far
        Color bandColor = HudShapes.scaleAlpha(acc, 0.16);
        for (int i = 0; i < leg; i++) segment(r, ox, oz, k, mod.cornerAt(i), mod.cornerAt(i + 1), band * 2 * k, bandColor);
        double[] start = mod.cornerAt(leg);
        double[] here = mod.isReturning() ? start : new double[]{
            start[0] + (next[0] - start[0]) * mod.legProgress(), start[1] + (next[1] - start[1]) * mod.legProgress()};
        segment(r, ox, oz, k, start, here, band * 2 * k, bandColor);

        // Flown legs, current leg, next corner
        double t = Math.max(1, 1.1 * s);
        for (int i = 0; i < leg; i++) segment(r, ox, oz, k, mod.cornerAt(i), mod.cornerAt(i + 1), t, HudShapes.scaleAlpha(dim, 0.8));
        segment(r, ox, oz, k, start, next, t * 1.6, acc);
        HudShapes.circle(r, ox + next[0] * k, oz + next[1] * k, 2.2 * s, acc);

        // Start and you
        HudShapes.circle(r, ox + mod.originX() * k, oz + mod.originZ() * k, 1.8 * s, dim);
        double ux = ox + px * k, uz = oz + pz * k;
        HudShapes.circle(r, ux, uz, 4.2 * s, HudShapes.scaleAlpha(text, 0.25));
        HudShapes.circle(r, ux, uz, 2.4 * s, text);
    }

    /** A straight east-west or north-south piece, `t` thick. */
    private void segment(HudRenderer r, double ox, double oz, double k, double[] a, double[] b, double t, Color c) {
        double x1 = ox + Math.min(a[0], b[0]) * k, x2 = ox + Math.max(a[0], b[0]) * k;
        double z1 = oz + Math.min(a[1], b[1]) * k, z2 = oz + Math.max(a[1], b[1]) * k;
        HudShapes.rect(r, x1 - t / 2, z1 - t / 2, (x2 - x1) + t, (z2 - z1) + t, 0, c);
    }

    private static String formatDistance(double blocks) {
        return blocks >= 1000 ? String.format("%.2f km", blocks / 1000) : String.format("%d m", (int) blocks);
    }

    private static String formatTime(long ms) {
        long sec = ms / 1000;
        return sec >= 3600 ? String.format("%dh %02dm", sec / 3600, (sec % 3600) / 60) : String.format("%dm %02ds", sec / 60, sec % 60);
    }
}
