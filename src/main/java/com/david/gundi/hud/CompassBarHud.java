package com.david.gundi.hud;

import com.david.gundi.Gundi;
import com.david.gundi.util.CrispText;
import com.david.gundi.util.Colors;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.MathHelper;

import java.util.Optional;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Skyrim-style compass strip. N/E/S/W and ticks slide as you turn, with markers for players in render
 * distance and your last death spot. Markers fade with distance and towards the ends.
 *
 * Styles:
 *  - Glass: rounded glass pill (default)
 *  - Ribbon: no panel, a dark band that fades out at both ends, very cinematic
 *  - Minimal: no background at all
 * A heading bubble above the middle shows your exact heading; under the bar you see the name and distance
 * of whatever marker you look straight at. Turning can be smoothed so the strip glides.
 */
public class CompassBarHud extends GundiHud {
    public static final HudElementInfo<CompassBarHud> INFO = new HudElementInfo<>(Gundi.HUD_GROUP, "compass-bar", "Compass strip with player and death markers.", CompassBarHud::new);

    private static final String NAME = "compass-bar";

    public enum Style { Glass, Ribbon, Minimal }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    // Follow the selected GUI theme (first setting in General). See util/ThemeLink.
    private final ThemeLink link = new ThemeLink(sgGeneral);
    private final SettingGroup sgLook = settings.createGroup("Style");
    private final SettingGroup sgColors = settings.createGroup("Colors", false);

    // General

    private final Setting<Integer> fov = sgGeneral.add(new IntSetting.Builder()
        .name("view-angle").description("How many degrees of the compass are visible across the bar.")
        .defaultValue(180).range(60, 360).sliderRange(90, 360).build());

    private final Setting<Boolean> players = sgGeneral.add(new BoolSetting.Builder()
        .name("players").description("Show other players in render distance as dots.").defaultValue(true).build());

    private final Setting<Boolean> death = sgGeneral.add(new BoolSetting.Builder()
        .name("last-death").description("Show where you last died (same dimension only).").defaultValue(true).build());

    private final Setting<Boolean> heading = sgGeneral.add(new BoolSetting.Builder()
        .name("show-heading").description("Under the bar: what you're looking at (name + distance), otherwise your heading.").defaultValue(true).build());

    private final Setting<Boolean> smooth = sgGeneral.add(new BoolSetting.Builder()
        .name("smooth-turning").description("The strip glides after your view instead of following every tiny jitter.").defaultValue(true).build());

    // Look

    private final Setting<Style> style = sgLook.add(new EnumSetting.Builder<Style>()
        .name("style").description("Glass = rounded pill. Ribbon = fading dark band. Minimal = no background.").defaultValue(Style.Glass).build());

    private final Setting<Double> scale = sgLook.add(new DoubleSetting.Builder()
        .name("scale").description("Size.").defaultValue(1).range(0.5, 3).build());

    private final Setting<Integer> barWidth = sgLook.add(new IntSetting.Builder()
        .name("width").description("Width of the strip (before scale).").defaultValue(360).range(150, 800).sliderRange(200, 600).build());

    private final Setting<Boolean> bubble = sgLook.add(new BoolSetting.Builder()
        .name("heading-bubble").description("Small bubble above the middle with your exact heading (e.g. 248°).").defaultValue(true).build());

    private final Setting<Boolean> intercardinal = sgLook.add(new BoolSetting.Builder()
        .name("intercardinal").description("Show NE / SE / SW / NW.").defaultValue(true).build());

    private final Setting<Boolean> markerGlow = sgLook.add(new BoolSetting.Builder()
        .name("marker-glow").description("Soft glow around markers.").defaultValue(true).build());

    // Colors

    private final Setting<SettingColor> accent = sgColors.add(new ColorSetting.Builder()
        .name("accent").description("Center pointer and heading bubble.").defaultValue(new SettingColor(10, 132, 255)).visible(link.own()).build());

    private final Setting<SettingColor> northColor = sgColors.add(new ColorSetting.Builder()
        .name("north-color").description("N, like Apple's compass.").defaultValue(new SettingColor(255, 69, 58)).build());

    private final Setting<SettingColor> letterColor = sgColors.add(new ColorSetting.Builder()
        .name("letter-color").description("E / S / W.").defaultValue(new SettingColor(245, 245, 247)).visible(link.own()).build());

    private final Setting<SettingColor> tickColor = sgColors.add(new ColorSetting.Builder()
        .name("tick-color").description("Tick marks and NE / SE...").defaultValue(new SettingColor(152, 152, 157)).visible(link.own()).build());

    private final Setting<SettingColor> playerColor = sgColors.add(new ColorSetting.Builder()
        .name("player-color").description("Player markers.").defaultValue(new SettingColor(100, 210, 255)).build());

    private final Setting<SettingColor> deathColor = sgColors.add(new ColorSetting.Builder()
        .name("death-color").description("Last death marker.").defaultValue(new SettingColor(255, 214, 10)).build());

    private final GlassStyle panel = new GlassStyle(settings, link, new GlassStyle.Defaults(
        new SettingColor(20, 21, 26, 175), 15, false, new SettingColor(10, 132, 255, 60)));

    private double smoothBearing = Double.NaN;

    public CompassBarHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        try {
            link.begin();
            double s = scale.get();
            double dt = link.dt(renderer);
            double barW = barWidth.get() * s, barH = 30 * s;
            double bubbleH = bubble.get() ? 16 * s : 0;
            double headingSize = 11 * s;
            double headingH = heading.get() ? CrispText.height(renderer, headingSize) + 5 * s : 0;
            setSize(barW, bubbleH + barH + headingH);

            double by = y + bubbleH; // bar top

            // Compass bearing: 0 = north, 90 = east, 180 = south, 270 = west.  Minecraft yaw + 180 = bearing.
            float yaw = mc.player != null ? mc.player.getYaw(mc.getRenderTickCounter().getTickProgress(true)) : 0;
            double target = MathHelper.floorMod(yaw + 180.0, 360.0);
            if (!smooth.get() || Double.isNaN(smoothBearing)) smoothBearing = target;
            else smoothBearing = MathHelper.floorMod(smoothBearing + wrap(target - smoothBearing) * (1 - Math.exp(-22 * dt)), 360.0);
            double bearing = smoothBearing;

            double half = fov.get() / 2.0;
            double cx = x + barW / 2;
            double inner = barW / 2 - barH / 2; // keep marks inside the rounded ends

            drawBackground(renderer, x, by, barW, barH, s);

            // Ticks every 15 degrees, letters on the 45s (and 90s)
            double labelSize = 13 * s, smallSize = 10 * s;
            for (int deg = 0; deg < 360; deg += 15) {
                double rel = wrap(deg - bearing);
                if (Math.abs(rel) > half) continue;
                double px = cx + rel / half * inner;
                double a = edgeFade(rel, half);
                if (a <= 0.02) continue;

                if (deg % 90 == 0) {
                    String l = deg == 0 ? "N" : deg == 90 ? "E" : deg == 180 ? "S" : "W";
                    Color c = HudShapes.scaleAlpha(deg == 0 ? northColor.get() : link.text(letterColor), a);
                    double lw = CrispText.width(renderer, l, labelSize), lh = CrispText.height(renderer, labelSize);
                    if (deg == 0 && markerGlow.get()) HudShapes.circle(renderer, px, by + barH / 2, lh * 0.8, HudShapes.scaleAlpha(northColor.get(), 0.14 * a));
                    CrispText.draw(renderer, l, px - lw / 2, by + (barH - lh) / 2, c, style.get() != Style.Glass, labelSize);
                } else if (deg % 45 == 0 && intercardinal.get()) {
                    String l = deg == 45 ? "NE" : deg == 135 ? "SE" : deg == 225 ? "SW" : "NW";
                    double lw = CrispText.width(renderer, l, smallSize), lh = CrispText.height(renderer, smallSize);
                    CrispText.draw(renderer, l, px - lw / 2, by + (barH - lh) / 2, HudShapes.scaleAlpha(link.dim(tickColor), a), style.get() != Style.Glass, smallSize);
                } else {
                    double th = barH * (deg % 45 == 0 ? 0.3 : 0.2);
                    HudShapes.rect(renderer, px - Math.max(1, s) / 2, by + (barH - th) / 2, Math.max(1, s), th, Math.max(0.5, s / 2), HudShapes.scaleAlpha(link.dim(tickColor), a * 0.8));
                }
            }

            String lookingAt = null;
            if (mc.player != null && mc.world != null) {
                if (players.get()) {
                    for (PlayerEntity p : mc.world.getPlayers()) {
                        if (p == mc.player) continue;
                        double dx = p.getX() - mc.player.getX(), dz = p.getZ() - mc.player.getZ();
                        double dist = Math.hypot(dx, dz);
                        Double rel = markerRel(dx, dz, bearing, half);
                        if (rel == null) continue;

                        double px = cx + rel / half * inner, my = by + barH * 0.8;
                        double a = edgeFade(rel, half) * Math.max(0.35, 1 - dist / 160);
                        double r = (3.2 - Math.min(1.6, dist / 80)) * s;
                        Color pc = playerColor.get();
                        if (markerGlow.get()) HudShapes.circle(renderer, px, my, r * 2.2, HudShapes.scaleAlpha(pc, 0.18 * a));
                        HudShapes.circle(renderer, px, my, r, HudShapes.scaleAlpha(pc, a));
                        if (Math.abs(rel) < 6) lookingAt = p.getName().getString() + "  " + (int) dist + "m";
                    }
                }

                if (death.get()) {
                    Optional<GlobalPos> lastDeath = mc.player.getLastDeathPos();
                    if (lastDeath.isPresent() && lastDeath.get().dimension() == mc.world.getRegistryKey()) {
                        BlockPos dp = lastDeath.get().pos();
                        double dx = dp.getX() + 0.5 - mc.player.getX(), dz = dp.getZ() + 0.5 - mc.player.getZ();
                        Double rel = markerRel(dx, dz, bearing, half);
                        if (rel != null) {
                            double px = cx + rel / half * inner;
                            double a = edgeFade(rel, half);
                            double d = 4 * s, my = by + barH * 0.22;
                            Color dc = deathColor.get();
                            if (markerGlow.get()) HudShapes.circle(renderer, px, my, d * 2, HudShapes.scaleAlpha(dc, 0.18 * a));
                            HudShapes.tri(renderer, px, my - d, px + d, my, px, my + d, HudShapes.scaleAlpha(dc, a));
                            HudShapes.tri(renderer, px, my - d, px, my + d, px - d, my, HudShapes.scaleAlpha(dc, a));
                            if (Math.abs(rel) < 6 && lookingAt == null) lookingAt = "Last death  " + (int) Math.hypot(dx, dz) + "m";
                        }
                    }
                }
            }

            // Center pointer + heading bubble
            Color ac = link.accent(accent);
            double ts = 4 * s;
            if (bubble.get()) {
                String deg = (int) Math.round(bearing) % 360 + "°";
                double bs = 10 * s;
                double tw = CrispText.width(renderer, deg, bs), th = CrispText.height(renderer, bs);
                double bw = tw + 10 * s, bh = th + 4 * s;
                double bx = cx - bw / 2, byy = y + (bubbleH - bh) / 2 - 1 * s;
                HudShapes.rect(renderer, bx, byy, bw, bh, bh / 2, ac);
                HudShapes.tri(renderer, cx - ts, byy + bh - 0.5, cx + ts, byy + bh - 0.5, cx, byy + bh + ts * 1.1, ac);
                CrispText.draw(renderer, deg, cx - tw / 2, byy + (bh - th) / 2, new Color(255, 255, 255), false, bs);
            } else {
                HudShapes.tri(renderer, cx - ts, by + 1, cx + ts, by + 1, cx, by + 1 + ts * 1.2, ac);
            }

            // Under the bar: what you're looking at, otherwise your heading (when there's no bubble showing it)
            if (heading.get()) {
                String text = lookingAt != null ? lookingAt : (bubble.get() ? cardinalName(bearing) : (int) Math.round(bearing) % 360 + "°  " + cardinal(bearing));
                double tw = CrispText.width(renderer, text, headingSize);
                CrispText.draw(renderer, text, cx - tw / 2, by + barH + 5 * s, lookingAt != null ? link.text(letterColor) : link.dim(tickColor), true, headingSize);
            }
        } catch (Exception e) {
            ErrorReporter.report(NAME, "render", e);
        }
    }

    private void drawBackground(HudRenderer r, double bx, double by, double w, double h, double s) {
        switch (style.get()) {
            case Glass -> panel.draw(r, bx, by, w, h, s, 1);
            case Ribbon -> {
                // Dark band that fades to clear at both ends, with faint lines on top and bottom
                Color mid = HudShapes.scaleAlpha(link.tint(panel.tint), 0.9), clear = new Color(mid.r, mid.g, mid.b, 0);
                r.quad(bx, by, w / 2, h, clear, mid, mid, clear);
                r.quad(bx + w / 2, by, w / 2, h, mid, clear, clear, mid);
                Color line = link.tile(new Color(255, 255, 255, 40)), lineClear = Colors.alpha(line, 0);
                double t = Math.max(1, s * 0.75);
                r.quad(bx, by, w / 2, t, lineClear, line, line, lineClear);
                r.quad(bx + w / 2, by, w / 2, t, line, lineClear, lineClear, line);
                r.quad(bx, by + h - t, w / 2, t, lineClear, line, line, lineClear);
                r.quad(bx + w / 2, by + h - t, w / 2, t, line, lineClear, lineClear, line);
            }
            case Minimal -> {}
        }
    }

    /** Where a marker sits relative to the center, in degrees, or null if it's off the visible part. */
    private static Double markerRel(double dx, double dz, double bearing, double half) {
        if (Math.abs(dx) < 1e-6 && Math.abs(dz) < 1e-6) return null;
        double markerBearing = Math.toDegrees(Math.atan2(dx, -dz)); // north (-Z) = 0, east (+X) = 90
        double rel = wrap(markerBearing - bearing);
        return Math.abs(rel) > half ? null : rel;
    }

    /** Things fade out towards the ends of the bar. */
    private static double edgeFade(double rel, double half) {
        double t = Math.abs(rel) / half;
        return Math.max(0, 1 - t * t);
    }

    /** Wraps any angle into -180..180. */
    private static double wrap(double deg) {
        return MathHelper.wrapDegrees(deg);
    }

    private static String cardinal(double bearing) {
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[(int) Math.round(bearing / 45) % 8];
    }

    private static String cardinalName(double bearing) {
        String[] names = {"North", "North-East", "East", "South-East", "South", "South-West", "West", "North-West"};
        return names[(int) Math.round(bearing / 45) % 8];
    }
}
