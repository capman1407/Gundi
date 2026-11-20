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
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Cinematic area titles: when you enter a new biome or dimension, a large title fades in at the
 * element's position, letters drifting together, with thin lines growing out on both sides.
 *
 *            ——————  E N T E R I N G  ——————
 *                    Dark Forest
 *
 * Put the element in the middle-upper part of the screen in the HUD editor.
 */
public class AreaTitleHud extends GundiHud {
    public static final HudElementInfo<AreaTitleHud> INFO = new HudElementInfo<>(Gundi.HUD_GROUP, "area-titles", "Cinematic title when you enter a new biome or dimension.", AreaTitleHud::new);

    private static final String NAME = "area-titles";

    private static final double FADE_IN = 0.9, FADE_OUT = 1.2; // seconds

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    // Follow the selected GUI theme (first setting in General). See util/ThemeLink.
    private final ThemeLink link = new ThemeLink(sgGeneral);
    private final SettingGroup sgLook = settings.createGroup("Style");
    private final SettingGroup sgColors = settings.createGroup("Colors", false);

    private final Setting<Boolean> biomes = sgGeneral.add(new BoolSetting.Builder()
        .name("biomes")
        .description("Show a title when you enter a new biome.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> dimensions = sgGeneral.add(new BoolSetting.Builder()
        .name("dimensions")
        .description("Show a title when you change dimension (Overworld / Nether / End).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> settleTime = sgGeneral.add(new DoubleSetting.Builder()
        .name("settle-time")
        .description("Seconds you have to stay in a new biome before its title shows (stops flicker at biome borders).")
        .defaultValue(1.5)
        .range(0, 10)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("repeat-cooldown")
        .description("Seconds before the same biome's title can show again.")
        .defaultValue(60)
        .range(0, 600)
        .build()
    );

    private final Setting<Double> holdTime = sgGeneral.add(new DoubleSetting.Builder()
        .name("hold-time")
        .description("Seconds the title stays fully visible.")
        .defaultValue(2.5)
        .range(0.5, 10)
        .build()
    );

    private final Setting<Double> scale = sgLook.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Size of the title.")
        .defaultValue(1)
        .range(0.5, 3)
        .build()
    );

    private final Setting<SettingColor> titleColor = sgColors.add(new ColorSetting.Builder()
        .name("title-color")
        .description("Color of the area name.")
        .defaultValue(new SettingColor(255, 255, 255)).visible(link.own())
        .build()
    );

    private final Setting<SettingColor> accentColor = sgColors.add(new ColorSetting.Builder()
        .name("accent-color")
        .description("Color of the small 'Entering' text and the lines.")
        .defaultValue(new SettingColor(235, 220, 180)).visible(link.own())
        .build()
    );

    private final Setting<Boolean> backdrop = sgLook.add(new BoolSetting.Builder()
        .name("backdrop")
        .description("Soft dark band behind the title so it's readable on bright backgrounds (Cinematic / Minimal).")
        .defaultValue(true)
        .build()
    );

    public enum Style { Cinematic, Banner, Minimal }

    private final Setting<Style> style = sgLook.add(new EnumSetting.Builder<Style>()
        .name("style")
        .description("Cinematic = big spaced title with lines. Banner = glass pill sliding down. Minimal = just the title.")
        .defaultValue(Style.Cinematic)
        .build()
    );

    private final Setting<Boolean> dimensionColors = sgLook.add(new BoolSetting.Builder()
        .name("dimension-colors")
        .description("Accent turns orange-red in the Nether and purple in the End.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> titleGlow = sgLook.add(new BoolSetting.Builder()
        .name("title-glow")
        .description("Soft glow in the accent color behind the title (Cinematic).")
        .defaultValue(true)
        .build()
    );

    // Panel settings, used by the Banner style
    private final GlassStyle panel = new GlassStyle(settings, link, new GlassStyle.Defaults(
        new SettingColor(20, 21, 26, 200), 20, true, new SettingColor(235, 220, 180, 60)));

    private static final Color NETHER_ACCENT = new Color(255, 120, 80);
    private static final Color END_ACCENT = new Color(191, 90, 242);

    // Detection state
    private String currentArea;          // what's on screen / last shown
    private String candidate;            // new biome we're waiting to "settle" in
    private long candidateSince;
    private RegistryKey<World> lastDimension;
    private final Map<String, Long> lastShown = new HashMap<>();

    // Title animation
    private String title = "";
    private String subtitle = "";
    private long titleStart = -1;

    public AreaTitleHud() {
        super(INFO);
    }

    @Override
    public void tick(HudRenderer renderer) {
        try {
            if (mc.player == null || mc.world == null) {
                lastDimension = null;
                currentArea = null;
                return;
            }

            long now = System.nanoTime();

            // Dimension change: shows immediately, it's always a big moment.
            RegistryKey<World> dim = mc.world.getRegistryKey();
            if (dim != lastDimension) {
                boolean firstTick = lastDimension == null;
                lastDimension = dim;
                candidate = null;
                currentArea = biomeName();
                if (!firstTick && dimensions.get()) start(dimensionName(dim), "Entering");
                return;
            }

            if (!biomes.get()) return;

            String biome = biomeName();
            if (biome == null || biome.equals(currentArea)) {
                candidate = null;
                return;
            }

            // New biome: wait until we've stayed in it for settle-time seconds
            if (!biome.equals(candidate)) {
                candidate = biome;
                candidateSince = now;
                return;
            }
            if ((now - candidateSince) / 1e9 < settleTime.get()) return;

            currentArea = biome;
            candidate = null;

            Long last = lastShown.get(biome);
            if (last != null && (now - last) / 1e9 < cooldown.get()) return;
            lastShown.put(biome, now);
            start(biome, "Entering");
        } catch (Exception e) {
            ErrorReporter.report(NAME, "tick", e);
        }
    }

    private void start(String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
        this.titleStart = System.nanoTime();
    }

    /** "minecraft:dark_forest" -> "Dark Forest". Returns null if the biome can't be read. */
    private String biomeName() {
        Optional<? extends RegistryKey<?>> key = mc.world.getBiome(mc.player.getBlockPos()).getKey();
        if (key.isEmpty()) return null;
        return prettify(key.get().getValue().getPath());
    }

    private static String dimensionName(RegistryKey<World> dim) {
        if (dim == World.NETHER) return "The Nether";
        if (dim == World.END) return "The End";
        if (dim == World.OVERWORLD) return "The Overworld";
        return prettify(dim.getValue().getPath());
    }

    private static String prettify(String path) {
        StringBuilder sb = new StringBuilder();
        for (String word : path.split("[_/]")) {
            if (word.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    @Override
    public void render(HudRenderer renderer) {
        try {
            link.begin();
            double s = scale.get();
            double boxW = 520 * s, boxH = 90 * s;
            setSize(boxW, boxH);

            // Animation progress: 0..1 in, hold, 1..0 out
            String text = title, sub = subtitle;
            double t;
            if (isInEditor() && (titleStart < 0 || elapsed() > total())) {
                text = "Dark Forest";
                sub = "Entering";
                t = 1;
            } else {
                if (titleStart < 0) return;
                double e = elapsed();
                if (e > total()) return;
                if (e < FADE_IN) t = e / FADE_IN;
                else if (e < FADE_IN + holdTime.get()) t = 1;
                else t = 1 - (e - FADE_IN - holdTime.get()) / FADE_OUT;
            }

            double ease = 1 - Math.pow(1 - Math.max(0, Math.min(1, t)), 3); // ease-out cubic
            double alpha = ease;
            if (alpha <= 0.01) return;

            Color baseAccent = link.accent(accentColor);
            if (dimensionColors.get() && mc.world != null) {
                if (mc.world.getRegistryKey() == World.NETHER) baseAccent = NETHER_ACCENT;
                else if (mc.world.getRegistryKey() == World.END) baseAccent = END_ACCENT;
            }

            switch (style.get()) {
                case Cinematic -> drawCinematic(renderer, text, sub, s, boxW, boxH, ease, alpha, baseAccent);
                case Banner -> drawBanner(renderer, text, sub, s, boxW, ease, alpha, baseAccent);
                case Minimal -> drawMinimal(renderer, text, s, boxW, boxH, alpha);
            }
        } catch (Exception e) {
            ErrorReporter.report(NAME, "render", e);
        }
    }

    /** Big spaced title; letters drift together, lines grow out of the subtitle and under the title. */
    private void drawCinematic(HudRenderer r, String text, String sub, double s, double boxW, double boxH, double ease, double alpha, Color baseAccent) {
        double titleSize = 40 * s, subSize = 13 * s;
        double cx = x + boxW / 2;

        backdrop(r, boxW, boxH, alpha);

        Color accent = HudShapes.scaleAlpha(baseAccent, alpha);
        Color main = HudShapes.scaleAlpha(link.text(titleColor), alpha);

        // Subtitle in wide-spaced small capitals, with lines growing out on both sides
        String subCaps = sub.toUpperCase();
        double subTracking = subSize * 0.45;
        double subW = trackedWidth(r, subCaps, subSize, subTracking);
        double subH = CrispText.height(r, subSize);
        double subY = y + boxH * 0.14;
        drawTracked(r, subCaps, cx - subW / 2, subY, accent, subSize, subTracking);

        double lineLen = 70 * s * ease, lineGap = 12 * s, lineY = subY + subH / 2;
        fadeLine(r, cx - subW / 2 - lineGap - lineLen, lineY, lineLen, Math.max(1, s), accent, true);
        fadeLine(r, cx + subW / 2 + lineGap, lineY, lineLen, Math.max(1, s), accent, false);

        // Title: letters start spread out and drift together while fading in
        double tracking = titleSize * (0.06 + 0.25 * (1 - ease));
        double titleW = trackedWidth(r, text, titleSize, tracking);
        double titleH = CrispText.height(r, titleSize);
        double titleY = subY + subH + 8 * s;

        if (titleGlow.get()) {
            // Soft horizontal glow band behind the title, in the accent color
            Color g = HudShapes.scaleAlpha(baseAccent, 0.16 * alpha), clear = new Color(g.r, g.g, g.b, 0);
            double gw = titleW * 0.6 + 40 * s, gy = titleY + titleH * 0.15, gh = titleH * 0.7;
            r.quad(cx - gw, gy, gw, gh, clear, g, g, clear);
            r.quad(cx, gy, gw, gh, g, clear, clear, g);
        }
        drawTracked(r, text, cx - titleW / 2, titleY, main, titleSize, tracking);

        // Thin accent line under the title, growing from the middle
        double ul = titleW * 0.5 * ease;
        fadeLine(r, cx - ul, titleY + titleH + 4 * s, ul, Math.max(1, s), HudShapes.scaleAlpha(accent, 0.8), true);
        fadeLine(r, cx, titleY + titleH + 4 * s, ul, Math.max(1, s), HudShapes.scaleAlpha(accent, 0.8), false);
    }

    /** Glass pill that slides down from the top of the box: [dot] ENTERING  Dark Forest. */
    private void drawBanner(HudRenderer r, String text, String sub, double s, double boxW, double ease, double alpha, Color baseAccent) {
        double titleSize = 22 * s, subSize = 10 * s, pad = 14 * s, gap = 10 * s;
        String subCaps = sub.toUpperCase();
        double subTracking = subSize * 0.3;

        double titleW = CrispText.width(r, text, titleSize), titleH = CrispText.height(r, titleSize);
        double subW = trackedWidth(r, subCaps, subSize, subTracking), subH = CrispText.height(r, subSize);
        double dot = 4 * s;

        double w = pad + dot * 2 + gap + subW + gap + titleW + pad;
        double h = titleH + pad * 1.2;
        double bx = x + (boxW - w) / 2;
        double by = y + 6 * s - (1 - ease) * 22 * s; // slides down into place

        panel.draw(r, bx, by, w, h, s, alpha, HudShapes.scaleAlpha(baseAccent, panel.glowColor.get().a / 255.0));

        double cy = by + h / 2;
        double dx = bx + pad + dot;
        HudShapes.circle(r, dx, cy, dot * 2, HudShapes.scaleAlpha(baseAccent, 0.22 * alpha));
        HudShapes.circle(r, dx, cy, dot, HudShapes.scaleAlpha(baseAccent, alpha));

        double sx = dx + dot + gap;
        drawTracked(r, subCaps, sx, cy - subH / 2, HudShapes.scaleAlpha(baseAccent, alpha), subSize, subTracking);
        CrispText.draw(r, text, sx + subW + gap, cy - titleH / 2, HudShapes.scaleAlpha(link.text(titleColor), alpha), false, titleSize);
    }

    /** Just the title, centered, fading in and out. */
    private void drawMinimal(HudRenderer r, String text, double s, double boxW, double boxH, double alpha) {
        double titleSize = 32 * s;
        backdrop(r, boxW, boxH, alpha * 0.8);
        double w = CrispText.width(r, text, titleSize), h = CrispText.height(r, titleSize);
        CrispText.draw(r, text, x + (boxW - w) / 2, y + (boxH - h) / 2, HudShapes.scaleAlpha(link.text(titleColor), alpha), true, titleSize);
    }

    /** Soft dark band: dark in the middle, fading to clear on the sides. */
    private void backdrop(HudRenderer r, double boxW, double boxH, double alpha) {
        if (!backdrop.get()) return;
        double cx = x + boxW / 2;
        Color mid = new Color(0, 0, 0, (int) (90 * alpha)), clear = new Color(0, 0, 0, 0);
        double bandY = y + boxH * 0.08, bandH = boxH * 0.84;
        r.quad(x, bandY, boxW / 2, bandH, clear, mid, mid, clear);
        r.quad(cx, bandY, boxW / 2, bandH, mid, clear, clear, mid);
    }

    /** Horizontal line that fades out towards one end (towardsLeft = clear on the left end). */
    private static void fadeLine(HudRenderer r, double lx, double ly, double len, double thick, Color c, boolean clearOnLeft) {
        if (len <= 0) return;
        Color clear = new Color(c.r, c.g, c.b, 0);
        if (clearOnLeft) r.quad(lx, ly, len, thick, clear, c, c, clear);
        else r.quad(lx, ly, len, thick, c, clear, clear, c);
    }

    private double elapsed() {
        return (System.nanoTime() - titleStart) / 1e9;
    }

    private double total() {
        return FADE_IN + holdTime.get() + FADE_OUT;
    }

    /** Width of text drawn letter by letter with extra spacing between letters. */
    private static double trackedWidth(HudRenderer r, String text, double size, double tracking) {
        double w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += CrispText.width(r, String.valueOf(text.charAt(i)), size);
            if (i < text.length() - 1) w += tracking;
        }
        return w;
    }

    private static void drawTracked(HudRenderer r, String text, double x, double y, Color color, double size, double tracking) {
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            CrispText.draw(r, ch, x, y, color, true, size);
            x += CrispText.width(r, ch, size) + tracking;
        }
    }
}
