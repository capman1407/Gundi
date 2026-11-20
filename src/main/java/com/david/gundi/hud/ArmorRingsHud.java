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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Apple Watch-style "complications" for your gear: each armor piece (and optionally your main-hand item)
 * with its durability.
 *
 * Styles:
 *  - Rings: Activity-ring look, the arc gets brighter towards its end, round caps.
 *  - Bars: rounded tile with the item and a slim bar underneath.
 *  - Minimal: just the item and its percentage, colored.
 * Layouts: Row, Column or Grid (3 wide).
 * Colors go green -> yellow -> red as items wear down; low items pulse with a red glow.
 */
public class ArmorRingsHud extends GundiHud {
    public static final HudElementInfo<ArmorRingsHud> INFO = new HudElementInfo<>(Gundi.HUD_GROUP, "armor-rings", "Armor durability as Apple Watch-style rings.", ArmorRingsHud::new);

    private static final String NAME = "armor-rings";

    public enum Style { Rings, Bars, Minimal }
    public enum Layout { Row, Column, Grid }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    // Follow the selected GUI theme (first setting in General). See util/ThemeLink.
    private final ThemeLink link = new ThemeLink(sgGeneral);
    private final SettingGroup sgLook = settings.createGroup("Style");
    private final SettingGroup sgColors = settings.createGroup("Colors", false);

    // General

    private final Setting<Boolean> mainHand = sgGeneral.add(new BoolSetting.Builder()
        .name("main-hand").description("Also show the item in your main hand (if it has durability).").defaultValue(true).build());

    private final Setting<Boolean> percent = sgGeneral.add(new BoolSetting.Builder()
        .name("percent").description("Show the durability percentage.").defaultValue(true).build());

    private final Setting<Integer> warnAt = sgGeneral.add(new IntSetting.Builder()
        .name("warn-at").description("Pulses with a red glow at or below this percentage.").defaultValue(15).range(0, 100).sliderRange(0, 50).build());

    // Look

    private final Setting<Style> style = sgLook.add(new EnumSetting.Builder<Style>()
        .name("style").description("Rings = Activity rings. Bars = tiles with a bar. Minimal = item + percentage.").defaultValue(Style.Rings).build());

    private final Setting<Layout> layout = sgLook.add(new EnumSetting.Builder<Layout>()
        .name("layout").description("Row, Column, or Grid (3 wide).").defaultValue(Layout.Row).build());

    private final Setting<Double> scale = sgLook.add(new DoubleSetting.Builder()
        .name("scale").description("Size.").defaultValue(1).range(0.5, 3).build());

    private final Setting<Double> ringThickness = sgLook.add(new DoubleSetting.Builder()
        .name("ring-thickness").description("Thickness of the rings.").defaultValue(3.5).range(1, 8)
        .visible(() -> style.get() == Style.Rings).build());

    private final Setting<Boolean> gradient = sgLook.add(new BoolSetting.Builder()
        .name("ring-gradient").description("Ring gets brighter towards its end, like Apple's Activity rings.").defaultValue(true)
        .visible(() -> style.get() == Style.Rings).build());

    private final Setting<Boolean> warnGlow = sgLook.add(new BoolSetting.Builder()
        .name("warn-glow").description("Red glow pulse on items about to break.").defaultValue(true).build());

    // Colors

    private final Setting<SettingColor> good = sgColors.add(new ColorSetting.Builder()
        .name("good-color").description("Full durability.").defaultValue(new SettingColor(48, 209, 88)).build());

    private final Setting<SettingColor> ok = sgColors.add(new ColorSetting.Builder()
        .name("ok-color").description("Half durability.").defaultValue(new SettingColor(255, 214, 10)).build());

    private final Setting<SettingColor> bad = sgColors.add(new ColorSetting.Builder()
        .name("bad-color").description("Almost broken.").defaultValue(new SettingColor(255, 69, 58)).build());

    private final Setting<SettingColor> noDurability = sgColors.add(new ColorSetting.Builder()
        .name("unbreakable-color").description("Items without durability.").defaultValue(new SettingColor(10, 132, 255)).visible(link.own()).build());

    private final GlassStyle panel = new GlassStyle(settings, link, new GlassStyle.Defaults(
        new SettingColor(20, 21, 26, 180), 16, false, new SettingColor(48, 209, 88, 60)));

    private final Spring[] shown = {new Spring(0), new Spring(0), new Spring(0), new Spring(0), new Spring(0)};

    public ArmorRingsHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        try {
            link.begin();
            double s = scale.get();
            double dt = link.dt(renderer);
            ItemStack[] stacks = gear();
            int count = stacks.length;

            // Cell size per style
            double textSize = 11 * s;
            double textH = percent.get() ? CrispText.height(renderer, textSize) + 4 * s : 0;
            double cellW, cellH;
            switch (style.get()) {
                case Rings -> { cellW = 40 * s; cellH = 40 * s + textH; }
                case Bars -> { cellW = 38 * s; cellH = 44 * s + textH; }
                default -> { cellW = 34 * s; cellH = 34 * s + textH; }
            }

            int cols = switch (layout.get()) { case Row -> count; case Column -> 1; case Grid -> Math.min(3, count); };
            int rows = (int) Math.ceil(count / (double) cols);
            double pad = 10 * s, gap = 8 * s;
            double w = pad * 2 + cols * cellW + (cols - 1) * gap;
            double h = pad * 2 + rows * cellH + (rows - 1) * gap;
            setSize(w, h);

            panel.draw(renderer, x, y, w, h, s, 1);
            double time = System.nanoTime() / 1e9;

            for (int i = 0; i < count; i++) {
                ItemStack stack = stacks[i];
                int col = i % cols, row = i / cols;
                double cx0 = x + pad + col * (cellW + gap), cy0 = y + pad + row * (cellH + gap);

                boolean has = stack != null && !stack.isEmpty();
                boolean damageable = has && stack.isDamageable() && stack.getMaxDamage() > 0;
                double frac = damageable ? 1 - (double) stack.getDamage() / stack.getMaxDamage() : (has ? 1 : 0);
                double p = Math.max(0, Math.min(1, shown[i].update(frac, dt, 0.5)));

                Color c = !damageable && has ? link.accent(noDurability) : durabilityColor(p);
                double pulse = warnGlow.get() && damageable && frac * 100 <= warnAt.get() ? 0.5 + 0.5 * Math.sin(time * Math.PI * 4) : 0;

                switch (style.get()) {
                    case Rings -> drawRing(renderer, stack, has, p, c, pulse, cx0 + cellW / 2, cy0 + 20 * s, 20 * s, s);
                    case Bars -> drawBar(renderer, stack, has, damageable, p, c, pulse, cx0, cy0, cellW, s);
                    case Minimal -> drawMinimal(renderer, stack, has, c, pulse, cx0 + cellW / 2, cy0 + 17 * s, s);
                }

                if (percent.get() && damageable) {
                    String txt = Math.round(frac * 100) + "%";
                    double tw = CrispText.width(renderer, txt, textSize);
                    CrispText.draw(renderer, txt, cx0 + (cellW - tw) / 2, cy0 + cellH - textH + 2 * s, HudShapes.scaleAlpha(c, 0.95), false, textSize);
                }
            }
        } catch (Exception e) {
            ErrorReporter.report(NAME, "render", e);
        }
    }

    // ===== Styles =====

    /** Activity ring: dim track, arc that gets brighter towards its end, round caps, item in the middle. */
    private void drawRing(HudRenderer r, ItemStack stack, boolean has, double p, Color c, double pulse, double cx, double cy, double ringR, double s) {
        double thick = ringThickness.get() * s;

        if (pulse > 0) HudShapes.ring(r, cx, cy, ringR + 3 * s * pulse, thick + 3 * s * pulse, HudShapes.scaleAlpha(bad.get(), 0.4 * pulse));
        HudShapes.ring(r, cx, cy, ringR, thick, HudShapes.scaleAlpha(c, has ? 0.2 : 0.1));

        if (has && p > 0.001) {
            double start = -Math.PI / 2, total = Math.PI * 2 * p, mid = ringR - thick / 2;
            int segments = Math.max(4, (int) (p * 48));
            Color dark = HudShapes.mix(new Color(0, 0, 0, c.a), c, 0.55);
            for (int k = 0; k < segments; k++) {
                double a1 = start + total * k / segments, a2 = start + total * (k + 1) / segments;
                Color seg = gradient.get() ? HudShapes.mix(dark, c, (k + 1.0) / segments) : c;
                HudShapes.arc(r, cx, cy, ringR, thick, a1, a2 + 0.002, seg); // tiny overlap so no hairline gaps
            }
            HudShapes.circle(r, cx + Math.cos(start) * mid, cy + Math.sin(start) * mid, thick / 2, gradient.get() ? dark : c);
            HudShapes.circle(r, cx + Math.cos(start + total) * mid, cy + Math.sin(start + total) * mid, thick / 2, c);
        }

        if (has) item(r, stack, cx, cy, ringR * 1.4);
    }

    /** Rounded tile with the item, and a slim durability bar underneath. */
    private void drawBar(HudRenderer r, ItemStack stack, boolean has, boolean damageable, double p, Color c, double pulse, double x0, double y0, double cellW, double s) {
        double tile = cellW, tileH = 36 * s;
        if (pulse > 0) HudShapes.outerGlow(r, x0, y0, tile, tileH, 9 * s, 6 * s, HudShapes.scaleAlpha(bad.get(), 0.6 * pulse));
        HudShapes.rect(r, x0, y0, tile, tileH, 9 * s, link.tile(new Color(255, 255, 255, has ? 16 : 8)));
        if (has) item(r, stack, x0 + tile / 2, y0 + tileH / 2, tileH * 0.9);

        double by = y0 + tileH + 4 * s, bh = Math.max(2, 3 * s);
        HudShapes.rect(r, x0 + 3 * s, by, tile - 6 * s, bh, bh / 2, HudShapes.scaleAlpha(c, 0.2));
        if (has && (damageable ? p : 1) > 0.001) HudShapes.rect(r, x0 + 3 * s, by, (tile - 6 * s) * (damageable ? p : 1), bh, bh / 2, c);
    }

    /** Just the item, with a soft colored glow behind it. */
    private void drawMinimal(HudRenderer r, ItemStack stack, boolean has, Color c, double pulse, double cx, double cy, double s) {
        HudShapes.circle(r, cx, cy, 13 * s, HudShapes.scaleAlpha(pulse > 0 ? bad.get() : c, 0.1 + 0.25 * pulse));
        if (has) item(r, stack, cx, cy, 26 * s);
    }

    /**
     * Draws an item icon centered at (cx, cy), about `fit` pixels big. Item icons are 16x16 pixel art and only
     * whole-number scales (1x, 2x, 3x...) stay sharp, so the scale is rounded and the position snapped to pixels.
     */
    private static void item(HudRenderer r, ItemStack stack, double cx, double cy, double fit) {
        int itemScale = (int) Math.max(1, Math.round(fit / 16));
        int size = 16 * itemScale;
        r.item(stack, (int) Math.round(cx - size / 2.0), (int) Math.round(cy - size / 2.0), itemScale, false);
    }

    /** Green above 50%, fading to yellow, then red near 0. */
    private Color durabilityColor(double p) {
        return p > 0.5 ? HudShapes.mix(ok.get(), good.get(), (p - 0.5) / 0.5) : HudShapes.mix(bad.get(), ok.get(), p / 0.5);
    }

    /** Head, chest, legs, feet (+ main hand). Example gear in the HUD editor when not in a world. */
    private ItemStack[] gear() {
        int n = mainHand.get() ? 5 : 4;
        ItemStack[] out = new ItemStack[n];

        if (mc.player == null) {
            ItemStack[] demo = {
                new ItemStack(Items.NETHERITE_HELMET), new ItemStack(Items.NETHERITE_CHESTPLATE),
                new ItemStack(Items.NETHERITE_LEGGINGS), new ItemStack(Items.NETHERITE_BOOTS),
                new ItemStack(Items.NETHERITE_SWORD)
            };
            System.arraycopy(demo, 0, out, 0, n);
            return out;
        }

        out[0] = mc.player.getEquippedStack(EquipmentSlot.HEAD);
        out[1] = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        out[2] = mc.player.getEquippedStack(EquipmentSlot.LEGS);
        out[3] = mc.player.getEquippedStack(EquipmentSlot.FEET);
        if (n == 5) {
            ItemStack hand = mc.player.getMainHandStack();
            out[4] = hand != null && hand.isDamageable() ? hand : ItemStack.EMPTY;
        }
        return out;
    }
}
