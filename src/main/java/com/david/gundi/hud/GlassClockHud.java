package com.david.gundi.hud;

import com.david.gundi.Gundi;
import com.david.gundi.util.CrispText;
import com.david.gundi.util.ErrorReporter;
import com.david.gundi.util.ThemeLink;
import com.david.gundi.util.GlassStyle;
import com.david.gundi.util.HudShapes;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Clock in two styles:
 *  - Lock screen: big real time, date, and a little sky dome showing where the Minecraft sun/moon is
 *    (the dome is tinted blue by day, orange at sunrise/sunset, deep navy at night) + "Day 154 · 09:12".
 *  - Analog: an Apple Watch-like face with hour ticks and smooth hands (optional second hand),
 *    with the date and Minecraft day underneath.
 */
public class GlassClockHud extends GundiHud {
    public static final HudElementInfo<GlassClockHud> INFO = new HudElementInfo<>(Gundi.HUD_GROUP, "glass-clock", "Real time clock with Minecraft day/night arc.", GlassClockHud::new);

    private static final String NAME = "glass-clock";

    public enum Style { LockScreen, Analog }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    // Follow the selected GUI theme (first setting in General). See util/ThemeLink.
    private final ThemeLink link = new ThemeLink(sgGeneral);
    private final SettingGroup sgLook = settings.createGroup("Style");
    private final SettingGroup sgColors = settings.createGroup("Colors", false);

    // General

    private final Setting<Style> style = sgLook.add(new EnumSetting.Builder<Style>()
        .name("style").description("Lock screen = big digital time. Analog = watch face with hands.").defaultValue(Style.LockScreen).build());

    private final Setting<Boolean> hours24 = sgGeneral.add(new BoolSetting.Builder()
        .name("24-hour").description("24-hour clock instead of AM/PM (lock screen style).").defaultValue(true).build());

    private final Setting<Boolean> seconds = sgGeneral.add(new BoolSetting.Builder()
        .name("seconds").description("Show seconds (small after the time, or a second hand on the analog face).").defaultValue(false).build());

    private final Setting<Boolean> showDate = sgGeneral.add(new BoolSetting.Builder()
        .name("show-date").description("Show the date.").defaultValue(true).build());

    private final Setting<Boolean> showGameTime = sgGeneral.add(new BoolSetting.Builder()
        .name("show-game-time").description("Show the Minecraft day and in-game time (and the sky dome on the lock screen).").defaultValue(true).build());

    // Look

    private final Setting<Double> scale = sgLook.add(new DoubleSetting.Builder()
        .name("scale").description("Size.").defaultValue(1).range(0.5, 3).build());

    private final Setting<SettingColor> timeColor = sgColors.add(new ColorSetting.Builder()
        .name("time-color").description("Time / hands.").defaultValue(new SettingColor(245, 245, 247)).visible(link.own()).build());

    private final Setting<SettingColor> accent = sgColors.add(new ColorSetting.Builder()
        .name("accent").description("Date and second hand.").defaultValue(new SettingColor(255, 159, 10)).visible(link.own()).build());

    private final Setting<SettingColor> dimColor = sgColors.add(new ColorSetting.Builder()
        .name("dim-color").description("Small secondary text.").defaultValue(new SettingColor(152, 152, 157)).visible(link.own()).build());

    private final GlassStyle panel = new GlassStyle(settings, link, new GlassStyle.Defaults(
        new SettingColor(20, 21, 26, 185), 18, false, new SettingColor(255, 159, 10, 60)));

    private static final Color SUN = new Color(255, 214, 10);
    private static final Color MOON = new Color(200, 215, 255);
    private static final Color SKY_DAY = new Color(90, 170, 255);
    private static final Color SKY_DUSK = new Color(255, 140, 60);
    private static final Color SKY_NIGHT = new Color(40, 52, 120);

    public GlassClockHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        try {
            link.begin();
            if (style.get() == Style.Analog) drawAnalog(renderer);
            else drawLockScreen(renderer);
        } catch (Exception e) {
            ErrorReporter.report(NAME, "render", e);
        }
    }

    // ===== Minecraft time =====

    /** Minecraft time of day (0 = ~6:00 sunrise, 6000 = noon, 12000 = 18:00, 18000 = midnight). */
    private long dayTicks() {
        long tod = mc.world != null ? mc.world.getTimeOfDay() : 6000;
        return Math.floorMod(tod, 24000L);
    }

    private String gameTimeText() {
        long tod = mc.world != null ? mc.world.getTimeOfDay() : 6000;
        int day = (int) (Math.floorDiv(tod, 24000L) + 1);
        int mcMinutes = (int) ((dayTicks() / 1000.0 + 6) * 60) % (24 * 60);
        return String.format("Day %d · %02d:%02d", day, mcMinutes / 60, mcMinutes % 60);
    }

    /** Sky color: blue by day, orange around sunrise/sunset, navy at night (smooth blends between). */
    private static Color skyColor(long t) {
        // Distance (in ticks) to the nearest sunrise (23500/0) or sunset (12500)
        double toSunrise = Math.min(Math.abs(t - 23500), Math.min(Math.abs(t + 500), Math.abs(t - 24000 - 500)));
        double toSunset = Math.abs(t - 12500);
        double dusk = Math.max(0, 1 - Math.min(toSunrise, toSunset) / 1500.0);
        boolean day = t < 12500 || t > 23500;
        Color base = day ? SKY_DAY : SKY_NIGHT;
        return HudShapes.mix(base, SKY_DUSK, dusk);
    }

    // ===== Lock screen =====

    private void drawLockScreen(HudRenderer r) {
        double s = scale.get();
        double pad = 14 * s, bigSize = 36 * s, smallSize = 13 * s, secSize = 16 * s;

        LocalDateTime now = LocalDateTime.now();
        String time = now.format(DateTimeFormatter.ofPattern(hours24.get() ? "HH:mm" : "h:mm", Locale.ENGLISH));
        String ampm = hours24.get() ? null : now.format(DateTimeFormatter.ofPattern("a", Locale.ENGLISH));
        String sec = seconds.get() ? now.format(DateTimeFormatter.ofPattern("ss", Locale.ENGLISH)) : null;
        String date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH));
        String game = gameTimeText();

        double bigH = CrispText.height(r, bigSize), smallH = CrispText.height(r, smallSize), secH = CrispText.height(r, secSize);
        double domeW = 40 * s, domeH = domeW / 2;

        double timeW = CrispText.width(r, time, bigSize);
        if (sec != null) timeW += 4 * s + CrispText.width(r, sec, secSize);
        if (ampm != null) timeW += 4 * s + CrispText.width(r, ampm, secSize);

        double w = timeW;
        if (showDate.get()) w = Math.max(w, CrispText.width(r, date, smallSize));
        if (showGameTime.get()) w = Math.max(w, domeW + 8 * s + CrispText.width(r, game, smallSize));
        w += pad * 2;

        double h = pad + (showDate.get() ? smallH + 2 * s : 0) + bigH;
        if (showGameTime.get()) h += 10 * s + Math.max(domeH, smallH);
        h += pad;
        setSize(w, h);

        panel.draw(r, x, y, w, h, s, 1);

        double ty = y + pad;
        // Date on top in the accent color, like the iPhone lock screen
        if (showDate.get()) {
            CrispText.draw(r, date, x + pad, ty, link.accent(accent), false, smallSize);
            ty += smallH + 2 * s;
        }

        double tx = CrispText.draw(r, time, x + pad, ty, link.text(timeColor), false, bigSize);
        if (sec != null) tx = CrispText.draw(r, sec, tx + 4 * s, ty + bigH - secH - 3 * s, link.dim(dimColor), false, secSize);
        if (ampm != null) CrispText.draw(r, ampm, tx + 4 * s, ty + bigH - secH - 3 * s, link.dim(dimColor), false, secSize);
        ty += bigH;

        if (showGameTime.get()) {
            ty += 10 * s;
            double rowH = Math.max(domeH, smallH);
            double acx = x + pad + domeW / 2, acy = ty + rowH;
            double ar = domeW / 2;
            long t = dayTicks();

            // Sky dome: tinted half-disc, horizon line, faint arc
            Color sky = skyColor(t);
            sector(r, acx, acy, ar, Math.PI, Math.PI * 2, HudShapes.scaleAlpha(sky, 0.35));
            r.quad(acx - ar - 2 * s, acy, domeW + 4 * s, Math.max(1, s), HudShapes.scaleAlpha(link.dim(dimColor), 0.6));
            HudShapes.arc(r, acx, acy, ar, Math.max(1, 1.2 * s), Math.PI, Math.PI * 2, HudShapes.scaleAlpha(link.dim(dimColor), 0.5));

            // Sun (day) or moon (night) travelling along the dome
            boolean isDay = t < 12500 || t > 23500;
            double progress = isDay ? Math.floorMod(t + 500, 24000L) / 13000.0 : (t - 12500) / 11000.0;
            progress = Math.max(0, Math.min(1, progress));
            double angle = Math.PI + Math.PI * progress;
            double bx = acx + Math.cos(angle) * ar, by = acy + Math.sin(angle) * ar;
            Color body = isDay ? SUN : MOON;
            HudShapes.circle(r, bx, by, 6 * s, HudShapes.scaleAlpha(body, 0.22));
            HudShapes.circle(r, bx, by, 3.2 * s, body);

            CrispText.draw(r, game, x + pad + domeW + 8 * s, ty + rowH - smallH, link.dim(dimColor), false, smallSize);
        }
    }

    // ===== Analog =====

    private void drawAnalog(HudRenderer r) {
        double s = scale.get();
        double pad = 12 * s, radius = 42 * s, smallSize = 11 * s;
        double smallH = CrispText.height(r, smallSize);

        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)).toUpperCase(Locale.ENGLISH);
        String game = gameTimeText();

        double w = radius * 2 + pad * 2;
        if (showGameTime.get()) w = Math.max(w, CrispText.width(r, game, smallSize) + pad * 2);
        double h = pad + radius * 2 + (showGameTime.get() ? 6 * s + smallH : 0) + pad;
        setSize(w, h);

        panel.draw(r, x, y, w, h, s, 1);

        double cx = x + w / 2, cy = y + pad + radius;

        // Face: subtle dial + 12 ticks (longer every 3 hours)
        HudShapes.circle(r, cx, cy, radius, new Color(0, 0, 0, 60));
        HudShapes.ring(r, cx, cy, radius, Math.max(1, s), link.tile(new Color(255, 255, 255, 30)));
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 - Math.PI / 2;
            boolean major = i % 3 == 0;
            double r1 = radius - (major ? 9 : 5) * s, r2 = radius - 3 * s;
            double t = (major ? 2 : 1.2) * s;
            thickLine(r, cx + Math.cos(a) * r1, cy + Math.sin(a) * r1, cx + Math.cos(a) * r2, cy + Math.sin(a) * r2, t,
                major ? link.text(timeColor) : HudShapes.scaleAlpha(link.text(timeColor), 0.45));
        }

        // Date window on the right, like a watch complication
        if (showDate.get()) {
            double dw = CrispText.width(r, date, smallSize);
            CrispText.draw(r, date, cx + radius * 0.42 - dw / 2, cy - smallH / 2, link.accent(accent), false, smallSize);
        }

        // Smooth hands (fractions include the smaller units, so nothing ticks in jumps)
        double secF = now.getSecond() + now.getNano() / 1e9;
        double minF = now.getMinute() + secF / 60;
        double hourF = (now.getHour() % 12) + minF / 60;

        hand(r, cx, cy, hourF / 12, radius * 0.5, 3.2 * s, link.text(timeColor));
        hand(r, cx, cy, minF / 60, radius * 0.78, 2.2 * s, link.text(timeColor));
        if (seconds.get()) hand(r, cx, cy, secF / 60, radius * 0.86, 1.1 * s, link.accent(accent));

        HudShapes.circle(r, cx, cy, 3 * s, seconds.get() ? link.accent(accent) : link.text(timeColor));
        HudShapes.circle(r, cx, cy, 1.2 * s, new Color(20, 21, 26));

        if (showGameTime.get()) {
            double gw = CrispText.width(r, game, smallSize);
            CrispText.draw(r, game, cx - gw / 2, cy + radius + 6 * s, link.dim(dimColor), false, smallSize);
        }
    }

    /** Clock hand: fraction 0..1 of a full turn, starting at 12 o'clock. Short tail behind the center. */
    private static void hand(HudRenderer r, double cx, double cy, double fraction, double len, double thick, Color c) {
        double a = Math.PI * 2 * fraction - Math.PI / 2;
        double tx = cx + Math.cos(a) * len, ty = cy + Math.sin(a) * len;
        double bx = cx - Math.cos(a) * len * 0.12, by = cy - Math.sin(a) * len * 0.12;
        thickLine(r, bx, by, tx, ty, thick, c);
        HudShapes.circle(r, tx, ty, thick / 2, c); // rounded tip
    }

    /** Line with a real thickness (two triangles). */
    private static void thickLine(HudRenderer r, double x1, double y1, double x2, double y2, double t, Color c) {
        double dx = x2 - x1, dy = y2 - y1, len = Math.hypot(dx, dy);
        if (len < 1e-6) return;
        double nx = -dy / len * t / 2, ny = dx / len * t / 2;
        HudShapes.tri(r, x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, c);
        HudShapes.tri(r, x1 + nx, y1 + ny, x2 - nx, y2 - ny, x1 - nx, y1 - ny, c);
    }

    /** Filled pie slice from angle a1 to a2 (radians, clockwise on screen). */
    private static void sector(HudRenderer r, double x, double y, double rad, double a1, double a2, Color c) {
        int steps = Math.max(4, (int) Math.ceil((a2 - a1) / Math.toRadians(8)));
        for (int i = 0; i < steps; i++) {
            double b1 = a1 + (a2 - a1) * i / steps, b2 = a1 + (a2 - a1) * (i + 1) / steps;
            HudShapes.tri(r, x, y, x + Math.cos(b1) * rad, y + Math.sin(b1) * rad, x + Math.cos(b2) * rad, y + Math.sin(b2) * rad, c);
        }
    }
}
