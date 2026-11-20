package com.david.gundi.hud;

import com.david.gundi.Gundi;
import com.david.gundi.gui.Spring;
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
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * "Maps"-style coordinates card.
 *
 *   Tiles layout (default):
 *     ┌──────┐┌──────┐┌──────┐
 *     │ X    ││ Y    ││ Z    │    each axis in its own tile, labels in 3D-axis colors (X red, Y green, Z blue)
 *     │ 1234 ││  64  ││-5678 │
 *     └──────┘└──────┘└──────┘
 *     ● Nether   154 · -709          dimension pill with the converted coords
 *     (needle) North  -Z             mini compass needle that turns with you
 *
 *   Row layout: everything on one compact line. Stacked: the classic three lines.
 *
 * Privacy: fades out while chat is open, so coordinates don't end up in chat screenshots.
 */
public class GlassCoordsHud extends GundiHud {
    public static final HudElementInfo<GlassCoordsHud> INFO = new HudElementInfo<>(Gundi.HUD_GROUP, "glass-coords", "Apple-style coordinates with Nether/Overworld conversion.", GlassCoordsHud::new);

    private static final String NAME = "glass-coords";

    public enum Layout { Tiles, Row, Stacked }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    // Follow the selected GUI theme (first setting in General). See util/ThemeLink.
    private final ThemeLink link = new ThemeLink(sgGeneral);
    private final SettingGroup sgLook = settings.createGroup("Style");
    private final SettingGroup sgColors = settings.createGroup("Colors", false);

    // General

    private final Setting<Boolean> convert = sgGeneral.add(new BoolSetting.Builder()
        .name("show-converted").description("Also show the matching Nether coords (in the Overworld) or Overworld coords (in the Nether).")
        .defaultValue(true).build());

    private final Setting<Boolean> direction = sgGeneral.add(new BoolSetting.Builder()
        .name("show-direction").description("Show which way you're facing and the axis (+X, -Z, ...).")
        .defaultValue(true).build());

    private final Setting<Integer> decimals = sgGeneral.add(new IntSetting.Builder()
        .name("decimals").description("Decimal places for the coordinates.")
        .defaultValue(0).range(0, 2).build());

    private final Setting<Boolean> hideInChat = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-in-chat").description("Fade out while chat is open, so your coordinates don't leak in screenshots.")
        .defaultValue(true).build());

    // Look

    private final Setting<Layout> layout = sgLook.add(new EnumSetting.Builder<Layout>()
        .name("layout").description("Tiles = each axis in its own tile. Row = one compact line. Stacked = three lines.")
        .defaultValue(Layout.Tiles).build());

    private final Setting<Double> scale = sgLook.add(new DoubleSetting.Builder()
        .name("scale").description("Size of the card.").defaultValue(1).range(0.5, 3).build());

    private final Setting<Boolean> axisColors = sgLook.add(new BoolSetting.Builder()
        .name("axis-colors").description("Color the X / Y / Z labels like 3D axes (red, green, blue).").defaultValue(true).build());

    private final Setting<SettingColor> xColor = sgColors.add(new ColorSetting.Builder()
        .name("x-color").description("X label.").defaultValue(new SettingColor(255, 69, 58)).visible(axisColors::get).build());

    private final Setting<SettingColor> yColor = sgColors.add(new ColorSetting.Builder()
        .name("y-color").description("Y label.").defaultValue(new SettingColor(48, 209, 88)).visible(axisColors::get).build());

    private final Setting<SettingColor> zColor = sgColors.add(new ColorSetting.Builder()
        .name("z-color").description("Z label.").defaultValue(new SettingColor(10, 132, 255)).visible(axisColors::get).build());

    private final Setting<SettingColor> textColor = sgColors.add(new ColorSetting.Builder()
        .name("text-color").description("Numbers.").defaultValue(new SettingColor(245, 245, 247)).visible(link.own()).build());

    private final Setting<SettingColor> labelColor = sgColors.add(new ColorSetting.Builder()
        .name("label-color").description("Dim labels.").defaultValue(new SettingColor(152, 152, 157)).visible(link.own()).build());

    private final Setting<SettingColor> accent = sgColors.add(new ColorSetting.Builder()
        .name("accent").description("Direction axis and compass highlight.").defaultValue(new SettingColor(10, 132, 255)).visible(link.own()).build());

    private final Setting<SettingColor> tileColor = sgColors.add(new ColorSetting.Builder()
        .name("tile-color").description("Background of the tiles and pills.").defaultValue(new SettingColor(255, 255, 255, 14)).visible(link.own()).build());

    private final GlassStyle panel = new GlassStyle(settings, link, new GlassStyle.Defaults(
        new SettingColor(20, 21, 26, 190), 14, false, new SettingColor(10, 132, 255, 70)));

    private static final Color NETHER = new Color(255, 99, 72);
    private static final Color OVERWORLD = new Color(48, 209, 88);
    private static final Color NEEDLE_N = new Color(255, 69, 58);

    private final Spring visible = new Spring(1);

    public GlassCoordsHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        try {
            link.begin();
            double s = scale.get();
            double dt = link.dt(renderer);

            boolean hide = hideInChat.get() && mc.currentScreen instanceof ChatScreen && !isInEditor();
            visible.update(hide ? 0 : 1, dt, 0.25);
            double alpha = Math.max(0, Math.min(1, visible.value));

            // Data (sample values when not in a world, e.g. HUD editor on the title screen)
            boolean hasPlayer = mc.player != null && mc.world != null;
            double px = hasPlayer ? mc.player.getX() : 1234.5, py = hasPlayer ? mc.player.getY() : 64, pz = hasPlayer ? mc.player.getZ() : -5678.5;
            float yaw = hasPlayer ? mc.player.getYaw() : 180;
            boolean nether = hasPlayer && mc.world.getRegistryKey() == World.NETHER;
            boolean end = hasPlayer && mc.world.getRegistryKey() == World.END;

            String fmt = "%." + decimals.get() + "f";
            String[] labels = {"X", "Y", "Z"};
            String[] values = {String.format(fmt, px), String.format(fmt, py), String.format(fmt, pz)};
            Color[] axis = axisColors.get()
                ? new Color[] {xColor.get(), yColor.get(), zColor.get()}
                : new Color[] {link.dim(labelColor), link.dim(labelColor), link.dim(labelColor)};

            String convLabel = null, convValue = null;
            Color dimColor = OVERWORLD;
            if (convert.get() && !end) {
                if (nether) {
                    convLabel = "Overworld";
                    convValue = String.format(fmt, px * 8) + " · " + String.format(fmt, pz * 8);
                    dimColor = OVERWORLD;
                } else {
                    convLabel = "Nether";
                    convValue = String.format(fmt, px / 8) + " · " + String.format(fmt, pz / 8);
                    dimColor = NETHER;
                }
            }

            String facing = null, axisText = null;
            if (direction.get()) {
                int dir = Math.floorMod(Math.round(MathHelper.wrapDegrees(yaw) / 90f), 4); // 0 south, 1 west, 2 north, 3 east
                facing = switch (dir) { case 0 -> "South"; case 1 -> "West"; case 2 -> "North"; default -> "East"; };
                axisText = switch (dir) { case 0 -> "+Z"; case 1 -> "-X"; case 2 -> "-Z"; default -> "+X"; };
            }

            Ctx c = new Ctx(renderer, s, alpha, labels, values, axis, convLabel, convValue, dimColor, facing, axisText, yaw);
            switch (layout.get()) {
                case Tiles -> drawTiles(c);
                case Row -> drawRow(c);
                case Stacked -> drawStacked(c);
            }
        } catch (Exception e) {
            ErrorReporter.report(NAME, "render", e);
        }
    }

    /** Everything one frame needs, so the three layouts share it. */
    private record Ctx(HudRenderer r, double s, double alpha, String[] labels, String[] values, Color[] axis,
                       String convLabel, String convValue, Color dimColor, String facing, String axisText, float yaw) {}

    // ===== Layouts =====

    private void drawTiles(Ctx c) {
        HudRenderer r = c.r;
        double s = c.s, a = c.alpha;
        double pad = 10 * s, gap = 6 * s, tilePad = 8 * s;
        double big = 19 * s, small = 12 * s, label = 10.5 * s;
        double bigH = CrispText.height(r, big), smallH = CrispText.height(r, small), labelH = CrispText.height(r, label);

        double tileW = 0;
        for (String v : c.values) tileW = Math.max(tileW, CrispText.width(r, v, big));
        tileW += tilePad * 2;
        double tileH = tilePad + labelH + 2 * s + bigH + tilePad;
        double tilesW = tileW * 3 + gap * 2;

        double rowH = Math.max(smallH, 12 * s) + 8 * s; // pills
        double w = tilesW;
        if (c.convLabel != null) w = Math.max(w, pillWidth(r, c, true) );
        if (c.facing != null) w = Math.max(w, pillWidth(r, c, false));
        w += pad * 2;
        double h = pad + tileH + (c.convLabel != null ? gap + rowH : 0) + (c.facing != null ? gap + rowH : 0) + pad;

        setSize(w, h);
        if (a <= 0.01) return;
        panel.draw(r, x, y, w, h, s, a);

        Color tile = HudShapes.scaleAlpha(link.tile(tileColor), a);
        Color text = HudShapes.scaleAlpha(link.text(textColor), a);
        double tx = x + pad + (w - pad * 2 - tilesW) / 2, ty = y + pad;
        for (int i = 0; i < 3; i++) {
            double cx = tx + i * (tileW + gap);
            HudShapes.rect(r, cx, ty, tileW, tileH, 8 * s, tile);
            // Small colored axis chip
            HudShapes.circle(r, cx + tilePad + 2.5 * s, ty + tilePad + labelH / 2, 2.5 * s, HudShapes.scaleAlpha(c.axis[i], a));
            CrispText.draw(r, c.labels[i], cx + tilePad + 8 * s, ty + tilePad, HudShapes.scaleAlpha(c.axis[i], a), false, label);
            double vw = CrispText.width(r, c.values[i], big);
            CrispText.draw(r, c.values[i], cx + (tileW - vw) / 2, ty + tilePad + labelH + 2 * s, text, false, big);
        }

        double yy = ty + tileH;
        if (c.convLabel != null) {
            yy += gap;
            drawPill(r, c, x + pad, yy, w - pad * 2, rowH, true);
            yy += rowH;
        }
        if (c.facing != null) {
            yy += gap;
            drawPill(r, c, x + pad, yy, w - pad * 2, rowH, false);
        }
    }

    private double pillWidth(HudRenderer r, Ctx c, boolean conversion) {
        double s = c.s, small = 12 * s;
        if (conversion) return 8 * s + 14 * s + CrispText.width(r, c.convLabel, small) + 8 * s + CrispText.width(r, c.convValue, small) + 8 * s;
        return 8 * s + 14 * s + CrispText.width(r, c.facing, small) + 8 * s + CrispText.width(r, c.axisText, small) + 8 * s;
    }

    /** Rounded row: dimension dot + "Nether 154 · -709", or compass needle + "North -Z". */
    private void drawPill(HudRenderer r, Ctx c, double px, double py, double pw, double ph, boolean conversion) {
        double s = c.s, a = c.alpha, small = 12 * s;
        double sh = CrispText.height(r, small);
        HudShapes.rect(r, px, py, pw, ph, ph / 2, HudShapes.scaleAlpha(link.tile(tileColor), a));

        double ix = px + 8 * s + 5 * s, iy = py + ph / 2;
        double tx = px + 8 * s + 14 * s, tyy = py + (ph - sh) / 2;
        if (conversion) {
            HudShapes.circle(r, ix, iy, 6 * s, HudShapes.scaleAlpha(c.dimColor, 0.2 * a));
            HudShapes.circle(r, ix, iy, 3.2 * s, HudShapes.scaleAlpha(c.dimColor, a));
            double lx = CrispText.draw(r, c.convLabel, tx, tyy, HudShapes.scaleAlpha(link.dim(labelColor), a), false, small) + 8 * s;
            CrispText.draw(r, c.convValue, lx, tyy, HudShapes.scaleAlpha(link.text(textColor), a), false, small);
        } else {
            needle(r, ix, iy, 5.5 * s, c.yaw, a);
            double lx = CrispText.draw(r, c.facing, tx, tyy, HudShapes.scaleAlpha(link.text(textColor), a), false, small) + 8 * s;
            CrispText.draw(r, c.axisText, lx, tyy, HudShapes.scaleAlpha(link.accent(accent), a), false, small);
        }
    }

    private void drawRow(Ctx c) {
        HudRenderer r = c.r;
        double s = c.s, a = c.alpha;
        double pad = 9 * s, colGap = 12 * s, size = 15 * s, small = 11 * s;
        double th = CrispText.height(r, size);

        double w = pad;
        for (int i = 0; i < 3; i++) w += CrispText.width(r, c.labels[i], small) + 4 * s + CrispText.width(r, c.values[i], size) + (i < 2 ? colGap : 0);
        if (c.facing != null) w += colGap + 14 * s + CrispText.width(r, c.axisText, small);
        w += pad;
        double h = th + pad * 2;

        setSize(w, h);
        if (a <= 0.01) return;
        panel.draw(r, x, y, w, h, s, a);

        double cx = x + pad, cy = y + pad, sh = CrispText.height(r, small);
        for (int i = 0; i < 3; i++) {
            CrispText.draw(r, c.labels[i], cx, cy + th - sh, HudShapes.scaleAlpha(c.axis[i], a), false, small);
            cx += CrispText.width(r, c.labels[i], small) + 4 * s;
            cx = CrispText.draw(r, c.values[i], cx, cy, HudShapes.scaleAlpha(link.text(textColor), a), false, size) + colGap;
        }
        if (c.facing != null) {
            needle(r, cx + 5 * s, y + h / 2, 5 * s, c.yaw, a);
            CrispText.draw(r, c.axisText, cx + 14 * s, cy + (th - sh) / 2, HudShapes.scaleAlpha(link.accent(accent), a), false, small);
        }
    }

    private void drawStacked(Ctx c) {
        HudRenderer r = c.r;
        double s = c.s, a = c.alpha;
        double pad = 12 * s, gap = 6 * s, colGap = 14 * s;
        double big = 20 * s, small = 14 * s;
        double bigH = CrispText.height(r, big), smallH = CrispText.height(r, small);

        double line1 = 0;
        for (int i = 0; i < 3; i++) {
            line1 += CrispText.width(r, c.labels[i], small) + gap + CrispText.width(r, c.values[i], big);
            if (i < 2) line1 += colGap;
        }
        double w = line1;
        if (c.convLabel != null) w = Math.max(w, 14 * s + CrispText.width(r, c.convLabel, small) + gap + CrispText.width(r, c.convValue, small));
        if (c.facing != null) w = Math.max(w, 14 * s + CrispText.width(r, c.facing + "  " + c.axisText, small));
        w += pad * 2;
        double h = pad + bigH + (c.convLabel != null ? gap + smallH : 0) + (c.facing != null ? gap + smallH : 0) + pad;

        setSize(w, h);
        if (a <= 0.01) return;
        panel.draw(r, x, y, w, h, s, a);

        double cx = x + pad, cy = y + pad;
        for (int i = 0; i < 3; i++) {
            CrispText.draw(r, c.labels[i], cx, cy + bigH - smallH, HudShapes.scaleAlpha(c.axis[i], a), false, small);
            cx += CrispText.width(r, c.labels[i], small) + gap;
            cx = CrispText.draw(r, c.values[i], cx, cy, HudShapes.scaleAlpha(link.text(textColor), a), false, big) + colGap;
        }
        cy += bigH;

        if (c.convLabel != null) {
            cy += gap;
            HudShapes.circle(r, x + pad + 4 * s, cy + smallH / 2, 3 * s, HudShapes.scaleAlpha(c.dimColor, a));
            double lx = CrispText.draw(r, c.convLabel, x + pad + 14 * s, cy, HudShapes.scaleAlpha(link.dim(labelColor), a), false, small) + gap;
            CrispText.draw(r, c.convValue, lx, cy, HudShapes.scaleAlpha(link.text(textColor), a), false, small);
            cy += smallH;
        }
        if (c.facing != null) {
            cy += gap;
            needle(r, x + pad + 4 * s, cy + smallH / 2, 4.5 * s, c.yaw, a);
            double lx = CrispText.draw(r, c.facing + "  ", x + pad + 14 * s, cy, HudShapes.scaleAlpha(link.text(textColor), a), false, small);
            CrispText.draw(r, c.axisText, lx, cy, HudShapes.scaleAlpha(link.accent(accent), a), false, small);
        }
    }

    /**
     * Mini compass needle: red tip points to north on screen as you turn (up = the way you face),
     * grey tip points south.
     */
    private void needle(HudRenderer r, double cx, double cy, double size, float yaw, double a) {
        // Yaw 180 = facing north -> north straight up. Turning right moves north to the left.
        double ang = Math.toRadians(180 - yaw) - Math.PI / 2;
        double fx = Math.cos(ang), fy = Math.sin(ang), rx = -fy, ry = fx;
        double w = size * 0.42;
        HudShapes.circle(r, cx, cy, size * 1.15, HudShapes.scaleAlpha(link.tile(tileColor), a));
        HudShapes.tri(r, cx + fx * size, cy + fy * size, cx + rx * w, cy + ry * w, cx - rx * w, cy - ry * w, HudShapes.scaleAlpha(NEEDLE_N, a));
        HudShapes.tri(r, cx - fx * size, cy - fy * size, cx - rx * w, cy - ry * w, cx + rx * w, cy + ry * w, HudShapes.scaleAlpha(new Color(200, 200, 205), a));
    }
}
