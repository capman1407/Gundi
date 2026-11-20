package com.david.gundi.modules;

import com.david.gundi.gui.GundiTheme;
import com.david.gundi.gui.Spring;
import com.david.gundi.gui.ThemeStyles.CornerStyle;
import com.david.gundi.util.Colors;
import com.david.gundi.util.CombatTracker;
import com.david.gundi.util.ErrorReporter;
import com.david.gundi.util.Shapes2D;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.Vec3d;

/**
 * Custom crosshair that replaces Minecraft's (style Vanilla gives the original back).
 *
 * - 14 shapes, each with its own settings (arm length, circle radius, dashes, fill, center dot, rotation, spin).
 * - Appearance: Client Theme (colors, corners and glow from the selected Gundi GUI theme) or Custom.
 * - Dynamic: spreads while moving / jumping / sprinting, kicks when you attack, attack-cooldown indicator,
 *   turns the target color when aiming at something with lock-on brackets and a target health ring,
 *   flashes and pulses on hits and kills. Everything glides with springs, and switching style cross-fades.
 * - Visibility: first/third person, spectator, spyglass, open menus, only with a weapon.
 *
 * Sizes are in "crosshair pixels": 1 = one pixel of Minecraft's GUI, so it scales with your GUI scale like the
 * vanilla crosshair (vanilla is 15 wide and 1 thick).
 */
public class Crosshair extends GundiModule {
    private static final String NAME = "crosshair";

    /** Read by CrosshairMixin. */
    public static Crosshair INSTANCE;

    public enum Style { Vanilla, Classic, Plus, TShape, Cross, Dot, Circle, CircleDot, Scope, Square, Diamond, Triangle, Chevron, Brackets, Triad, Orbit }
    public enum Appearance { ClientTheme, Custom }
    public enum Cooldown { None, Arc, Bar, Fade }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgShape = settings.createGroup("Shape");
    private final SettingGroup sgDynamic = settings.createGroup("Dynamic");
    private final SettingGroup sgColors = settings.createGroup("Colors", false);
    private final SettingGroup sgOutline = settings.createGroup("Outline & Glow", false);
    private final SettingGroup sgVisibility = settings.createGroup("Visibility", false);

    // ===== General =====

    private final Setting<Style> style = sgGeneral.add(new EnumSetting.Builder<Style>()
        .name("style").description("Crosshair shape. Vanilla = Minecraft's own crosshair.").defaultValue(Style.Classic).build());
    private final Setting<Appearance> appearance = sgGeneral.add(new EnumSetting.Builder<Appearance>()
        .name("appearance").description("Client Theme = colors, corners and glow from the selected Gundi GUI theme. Custom = your own colors.")
        .defaultValue(Appearance.ClientTheme).visible(this::custom0).build());
    private final Setting<Double> size = sgGeneral.add(new DoubleSetting.Builder()
        .name("size").description("Scales the whole crosshair.").defaultValue(1).range(0.25, 4).visible(this::custom0).build());
    private final Setting<Double> thickness = sgGeneral.add(new DoubleSetting.Builder()
        .name("thickness").description("Line thickness (1 = vanilla).").defaultValue(1).range(0.25, 4).visible(this::custom0).build());
    private final Setting<Double> gap = sgGeneral.add(new DoubleSetting.Builder()
        .name("gap").description("Space between the center and the arms.").defaultValue(2).range(0, 12).visible(this::hasGap).build());
    private final Setting<Double> opacity = sgGeneral.add(new DoubleSetting.Builder()
        .name("opacity").description("How see-through the whole crosshair is.").defaultValue(1).range(0.05, 1).visible(this::custom0).build());
    private final Setting<Boolean> scaleWithGui = sgGeneral.add(new BoolSetting.Builder()
        .name("scale-with-gui").description("Grow with Minecraft's GUI scale, like the vanilla crosshair. Off = fixed size in screen pixels.")
        .defaultValue(true).visible(this::custom0).build());

    // ===== Shape (each only shows for the styles it applies to) =====

    private final Setting<Double> armLength = sgShape.add(new DoubleSetting.Builder()
        .name("arm-length").description("Length of the lines.").defaultValue(5).range(1, 20)
        .visible(() -> is(Style.Classic, Style.Plus, Style.TShape, Style.Cross, Style.Scope, Style.Chevron, Style.Triad)).build());
    private final Setting<Double> radius = sgShape.add(new DoubleSetting.Builder()
        .name("radius").description("Size of the circle / square / diamond / triangle / brackets.").defaultValue(6).range(1, 24)
        .visible(() -> is(Style.Circle, Style.CircleDot, Style.Scope, Style.Square, Style.Diamond, Style.Triangle, Style.Brackets, Style.Orbit)).build());
    private final Setting<Integer> dashes = sgShape.add(new IntSetting.Builder()
        .name("dashes").description("Split the circle into this many dashes (0 = solid ring).").defaultValue(0).range(0, 16)
        .visible(() -> is(Style.Circle, Style.CircleDot, Style.Scope)).build());
    private final Setting<Integer> orbitDashes = sgShape.add(new IntSetting.Builder()
        .name("orbit-dashes").description("Dashes in the spinning ring.").defaultValue(3).range(2, 12).visible(() -> is(Style.Orbit)).build());
    private final Setting<Boolean> filled = sgShape.add(new BoolSetting.Builder()
        .name("filled").description("Fill the shape with a see-through color.").defaultValue(false)
        .visible(() -> is(Style.Square, Style.Diamond, Style.Triangle)).build());
    private final Setting<Boolean> centerDot = sgShape.add(new BoolSetting.Builder()
        .name("center-dot").description("Small dot in the middle.").defaultValue(false)
        .visible(() -> !is(Style.Vanilla, Style.Dot, Style.CircleDot, Style.Orbit)).build());
    private final Setting<Double> dotSize = sgShape.add(new DoubleSetting.Builder()
        .name("dot-size").description("Size of the (center) dot.").defaultValue(1.5).range(0.5, 8)
        .visible(() -> is(Style.Dot, Style.CircleDot, Style.Orbit) || (centerDot.get() && !is(Style.Vanilla))).build());
    private final Setting<Double> rotation = sgShape.add(new DoubleSetting.Builder()
        .name("rotation").description("Turns the crosshair, in degrees.").defaultValue(0).range(0, 360).sliderRange(0, 360)
        .visible(() -> !is(Style.Vanilla, Style.Dot)).build());
    private final Setting<Double> spin = sgShape.add(new DoubleSetting.Builder()
        .name("spin").description("Keeps turning, in turns per second (Orbit always spins a little).").defaultValue(0).range(-2, 2)
        .visible(() -> !is(Style.Vanilla, Style.Dot)).build());

    // ===== Dynamic =====

    private final Setting<Boolean> spread = sgDynamic.add(new BoolSetting.Builder()
        .name("movement-spread").description("Arms move apart while you move, jump or sprint, and come back when you stop.").defaultValue(true).visible(this::custom0).build());
    private final Setting<Double> spreadAmount = sgDynamic.add(new DoubleSetting.Builder()
        .name("spread-amount").description("How far they move apart.").defaultValue(3).range(0.5, 12).visible(() -> !is(Style.Vanilla) && spread.get()).build());
    private final Setting<Boolean> sneakTighten = sgDynamic.add(new BoolSetting.Builder()
        .name("sneak-tighten").description("Pulls in a little while sneaking.").defaultValue(true).visible(() -> !is(Style.Vanilla) && spread.get()).build());
    private final Setting<Boolean> attackKick = sgDynamic.add(new BoolSetting.Builder()
        .name("attack-kick").description("A quick kick outwards when you attack.").defaultValue(true).visible(this::custom0).build());
    private final Setting<Cooldown> cooldown = sgDynamic.add(new EnumSetting.Builder<Cooldown>()
        .name("cooldown-indicator").description("Attack cooldown: an arc around the crosshair, a bar under it, or the crosshair fading in.")
        .defaultValue(Cooldown.Arc).visible(this::custom0).build());
    private final Setting<Boolean> targetReact = sgDynamic.add(new BoolSetting.Builder()
        .name("target-color").description("Changes color when you aim at a mob or player (friends in the friend color).").defaultValue(true).visible(this::custom0).build());
    private final Setting<Boolean> lockOn = sgDynamic.add(new BoolSetting.Builder()
        .name("lock-on").description("Corner brackets close in around the crosshair while you aim at something.").defaultValue(true).visible(this::custom0).build());
    private final Setting<Boolean> targetHealth = sgDynamic.add(new BoolSetting.Builder()
        .name("target-health").description("A ring showing the health of what you're aiming at.").defaultValue(false).visible(this::custom0).build());
    private final Setting<Boolean> hitFeedback = sgDynamic.add(new BoolSetting.Builder()
        .name("hit-feedback").description("Flashes and pulses when you hit something; bigger for kills.").defaultValue(true).visible(this::custom0).build());
    private final Setting<Double> smoothness = sgDynamic.add(new DoubleSetting.Builder()
        .name("smoothness").description("How softly everything moves (seconds to settle).").defaultValue(0.18).range(0.03, 0.8).visible(this::custom0).build());

    // ===== Colors (Custom appearance) =====

    private final Setting<SettingColor> color = sgColors.add(new ColorSetting.Builder()
        .name("color").description("Crosshair color.").defaultValue(new SettingColor(255, 255, 255, 235)).visible(this::customColors).build());
    private final Setting<Boolean> rainbow = sgColors.add(new BoolSetting.Builder()
        .name("rainbow").description("Cycles through all colors.").defaultValue(false).visible(this::customColors).build());
    private final Setting<Double> rainbowSpeed = sgColors.add(new DoubleSetting.Builder()
        .name("rainbow-speed").description("Color cycles per second.").defaultValue(0.25).range(0.02, 2).visible(() -> customColors() && rainbow.get()).build());
    private final Setting<SettingColor> targetColor = sgColors.add(new ColorSetting.Builder()
        .name("target-color").description("While aiming at a mob or player.").defaultValue(new SettingColor(255, 69, 58, 240)).visible(this::customColors).build());
    private final Setting<SettingColor> friendColor = sgColors.add(new ColorSetting.Builder()
        .name("friend-color").description("While aiming at a friend.").defaultValue(new SettingColor(100, 210, 255, 240)).visible(this::customColors).build());
    private final Setting<SettingColor> hitColor = sgColors.add(new ColorSetting.Builder()
        .name("hit-color").description("Flash when you hit something.").defaultValue(new SettingColor(255, 214, 10, 255)).visible(this::customColors).build());
    private final Setting<SettingColor> killColor = sgColors.add(new ColorSetting.Builder()
        .name("kill-color").description("Flash when something you hit dies.").defaultValue(new SettingColor(255, 59, 48, 255)).visible(this::customColors).build());

    // ===== Outline & glow =====

    private final Setting<Boolean> outline = sgOutline.add(new BoolSetting.Builder()
        .name("outline").description("Dark edge around the crosshair so it shows on bright and dark backgrounds.").defaultValue(true).visible(this::custom0).build());
    private final Setting<Double> outlineWidth = sgOutline.add(new DoubleSetting.Builder()
        .name("outline-width").description("Edge thickness, in screen pixels.").defaultValue(1).range(0.5, 4).visible(() -> !is(Style.Vanilla) && outline.get()).build());
    private final Setting<SettingColor> outlineColor = sgOutline.add(new ColorSetting.Builder()
        .name("outline-color").description("Edge color.").defaultValue(new SettingColor(0, 0, 0, 170)).visible(() -> customColors() && outline.get()).build());
    private final Setting<Boolean> glow = sgOutline.add(new BoolSetting.Builder()
        .name("glow").description("Soft halo in the crosshair color. With Client Theme it follows the theme's glow.").defaultValue(false).visible(this::customColors).build());

    // ===== Visibility =====

    private final Setting<Boolean> firstPerson = sgVisibility.add(new BoolSetting.Builder()
        .name("first-person").description("Show in first person.").defaultValue(true).build());
    private final Setting<Boolean> thirdPerson = sgVisibility.add(new BoolSetting.Builder()
        .name("third-person").description("Also show in third person (F5); vanilla doesn't.").defaultValue(false).build());
    private final Setting<Boolean> inSpectator = sgVisibility.add(new BoolSetting.Builder()
        .name("in-spectator").description("Show while in spectator mode.").defaultValue(true).build());
    private final Setting<Boolean> hideScoping = sgVisibility.add(new BoolSetting.Builder()
        .name("hide-while-scoping").description("Hide while looking through a spyglass.").defaultValue(true).build());
    private final Setting<Boolean> hideInMenus = sgVisibility.add(new BoolSetting.Builder()
        .name("hide-in-menus").description("Hide while a menu, inventory or chat is open.").defaultValue(false).build());
    private final Setting<Boolean> onlyWeapon = sgVisibility.add(new BoolSetting.Builder()
        .name("only-with-weapon").description("Only show while holding a sword, axe, mace, trident, bow or crossbow.").defaultValue(false).build());

    // ===== State =====

    private final Spring spreadSpring = new Spring(0);
    private final Spring kick = new Spring(0);
    private final Spring targetSpring = new Spring(0);
    private final Spring lockSpring = new Spring(0);
    private final Spring cooldownShow = new Spring(0);
    private final Spring healthSpring = new Spring(0);
    private final Spring styleFade = new Spring(1);
    private Style shownStyle, previousStyle;
    private long lastSeenHit = -1;
    private boolean wasAttackPressed;
    private boolean targetFriend;

    public Crosshair() {
        super(NAME, "Custom crosshair: many shapes, theme colors, movement spread, hit and target feedback. Style Vanilla = Minecraft's own.");
        INSTANCE = this;
    }

    /** Used by CrosshairMixin: hide Minecraft's crosshair while ours is in charge. */
    public static boolean replacesVanilla() {
        Crosshair c = INSTANCE;
        return c != null && c.isActive() && c.style.get() != Style.Vanilla;
    }

    // Visibility helpers for the settings
    private boolean is(Style... styles) {
        for (Style s : styles) if (style.get() == s) return true;
        return false;
    }

    private boolean custom0() {
        return style.get() != Style.Vanilla;
    }

    private boolean hasGap() {
        return is(Style.Classic, Style.TShape, Style.Cross, Style.Chevron, Style.Triad);
    }

    private boolean customColors() {
        return custom0() && appearance.get() == Appearance.Custom;
    }

    // ===== Rendering =====

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        try {
            if (style.get() == Style.Vanilla || !shouldShow()) return;

            GundiTheme theme = appearance.get() == Appearance.ClientTheme && GuiThemes.get() instanceof GundiTheme g ? g : null;
            double dt = Math.min(Math.max(event.frameTime, 0), 0.1);
            if (theme != null) dt *= theme.animationSpeed.get();
            double resp = smoothness.get();

            // Unit = one GUI pixel (vanilla crosshair size), or a screen pixel with scale-with-gui off
            double unit = (scaleWithGui.get() ? mc.getWindow().getScaleFactor() : 2) * size.get();
            double t = Math.max(1, Math.round(thickness.get() * unit));

            updateAnimations(dt, resp);

            // Color: base -> target color -> hit / kill flash
            long now = System.nanoTime();
            CombatTracker ct = CombatTracker.INSTANCE;
            double hitAge = ct.lastHitNanos > 0 ? (now - ct.lastHitNanos) / 1e9 : 99;
            double killAge = ct.lastKillNanos > 0 ? (now - ct.lastKillNanos) / 1e9 : 99;
            double hitFlash = hitFeedback.get() ? Math.max(0, 1 - hitAge / 0.3) : 0;
            double killFlash = hitFeedback.get() ? Math.max(0, 1 - killAge / 0.6) : 0;

            Color base = baseColor(theme);
            Color tgt = targetFriend ? pick(theme, friendColor.get(), theme != null ? theme.switchOn.get() : null) : pick(theme, targetColor.get(), theme != null ? theme.accent.get() : null);
            Color c = Colors.mix(base, Colors.withAlpha(tgt, base.a), clamp01(targetSpring.value));
            c = Colors.mix(c, pick(theme, hitColor.get(), theme != null ? theme.accent2.get() : null), hitFlash * hitFlash);
            c = Colors.mix(c, pick(theme, killColor.get(), theme != null ? theme.minus.get() : null), killFlash);

            // Cooldown "Fade": the crosshair itself fades in as the attack recharges
            double cool = mc.player.getAttackCooldownProgress(0);
            double alpha = opacity.get() * (cooldown.get() == Cooldown.Fade ? 0.35 + 0.65 * cool : 1);
            c = Colors.alpha(c, alpha);
            Color outlineC = Colors.alpha(theme != null ? Colors.withAlpha(Colors.lighten(theme.window.get(), -20), 190) : outlineColor.get(), alpha);

            double pulse = 1 + 0.25 * hitFlash * hitFlash + 0.4 * killFlash * killFlash;
            double extra = (spreadSpring.value + kick.value) * unit;
            double angle = Math.toRadians(rotation.get()) + (now / 1e9) * Math.PI * 2 * (spin.get() + (is(Style.Orbit) ? 0.35 : 0));

            // Corners follow the theme (squares, bars) like the rest of the client
            Shapes2D.corners = theme != null ? theme.corners.get() : CornerStyle.Round;

            double cx = Math.floor(Utils.getWindowWidth() / 2.0), cy = Math.floor(Utils.getWindowHeight() / 2.0);
            double ow = outline.get() ? Math.max(1, outlineWidth.get()) : 0;
            boolean glowOn = theme != null ? theme.glow.get() : glow.get();

            Renderer2D.COLOR.begin();
            // Smooth style switch: the old style fades out while the new one fades in
            double fade = clamp01(styleFade.value);
            if (previousStyle != null && fade < 1) drawLayers(previousStyle, cx, cy, unit * pulse, t, extra, angle, Colors.alpha(c, 1 - fade), Colors.alpha(outlineC, 1 - fade), ow, glowOn);
            drawLayers(shownStyle, cx, cy, unit * pulse, t, extra, angle, Colors.alpha(c, fade), Colors.alpha(outlineC, fade), ow, glowOn);

            drawExtras(theme, cx, cy, unit, t, extra, cool, c, outlineC, ow);
            Renderer2D.COLOR.render();
        } catch (Exception e) {
            ErrorReporter.report(NAME, "render", e);
        }
    }

    private boolean shouldShow() {
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return false;
        boolean first = mc.options.getPerspective().isFirstPerson();
        if (first ? !firstPerson.get() : !thirdPerson.get()) return false;
        if (mc.player.isSpectator() && !inSpectator.get()) return false;
        if (hideScoping.get() && mc.player.isUsingSpyglass()) return false;
        if (hideInMenus.get() && mc.currentScreen != null) return false;
        if (onlyWeapon.get() && !isWeapon(mc.player.getMainHandStack())) return false;
        return true;
    }

    private static boolean isWeapon(ItemStack s) {
        Item i = s.getItem();
        return s.isIn(ItemTags.SWORDS) || s.isIn(ItemTags.AXES) || i instanceof RangedWeaponItem || i == Items.TRIDENT || i == Items.MACE;
    }

    private void updateAnimations(double dt, double resp) {
        // Style cross-fade
        if (shownStyle != style.get()) {
            previousStyle = shownStyle;
            shownStyle = style.get();
            styleFade.snap(previousStyle == null ? 1 : 0);
        }
        styleFade.update(1, dt, 0.25);

        // Movement spread
        double target = 0;
        if (spread.get()) {
            Vec3d v = Utils.getPlayerSpeed();
            double moving = Math.min(1.5, Math.hypot(v.x, v.z) / 5.6);
            target = spreadAmount.get() * (moving * 0.6 + (mc.player.isOnGround() ? 0 : 0.5) + (mc.player.isSprinting() ? 0.25 : 0));
            if (sneakTighten.get() && mc.player.isSneaking()) target -= Math.min(gap.get(), 1.5);
        }
        spreadSpring.update(target, dt, resp);

        // Attack kick: on a click (even in the air) and on every hit
        boolean attack = mc.options.attackKey.isPressed();
        boolean hit = CombatTracker.INSTANCE.lastHitNanos != lastSeenHit;
        if (attackKick.get() && ((attack && !wasAttackPressed) || hit)) kick.velocity += 150; // ~2 crosshair pixels at the peak
        wasAttackPressed = attack;
        lastSeenHit = CombatTracker.INSTANCE.lastHitNanos;
        kick.update(0, dt, resp * 0.8, 0.6);

        // Target
        Entity e = mc.targetedEntity;
        boolean hasTarget = e instanceof LivingEntity le && le.isAlive() && !le.isInvisible();
        if (hasTarget) {
            targetFriend = e instanceof PlayerEntity p && Friends.get().isFriend(p);
            LivingEntity le = (LivingEntity) e;
            healthSpring.update((le.getHealth() + le.getAbsorptionAmount()) / Math.max(1, le.getMaxHealth()), dt, 0.25);
        }
        targetSpring.update(targetReact.get() && hasTarget ? 1 : 0, dt, resp);
        lockSpring.update(lockOn.get() && hasTarget ? 1 : 0, dt, 0.3, 0.7);

        double cool = mc.player.getAttackCooldownProgress(0);
        cooldownShow.update(cool < 0.999 ? 1 : 0, dt, cool < 0.999 ? 0.08 : 0.3);
    }

    /** Outline + glow + fill for one style. Outlines go first, then fills, so overlapping parts look clean. */
    private void drawLayers(Style st, double cx, double cy, double u, double t, double extra, double angle, Color c, Color oc, double ow, boolean glowOn) {
        if (c.a <= 1) return;
        if (glowOn) {
            Color halo = Colors.alpha(c, 0.18);
            drawStyle(st, cx, cy, u, t, extra, angle, halo, t * 1.2, true);
            drawStyle(st, cx, cy, u, t, extra, angle, Colors.alpha(c, 0.25), t * 0.6, true);
        }
        if (ow > 0 && oc.a > 1) drawStyle(st, cx, cy, u, t, extra, angle, oc, ow, false);
        drawStyle(st, cx, cy, u, t, extra, angle, c, 0, false);
    }

    /** Draws one style. grow = how much every part is enlarged (outline / glow pass), soft = no fills (glow pass). */
    private void drawStyle(Style st, double cx, double cy, double u, double t, double extra, double angle, Color c, double grow, boolean soft) {
        double g = gap.get() * u + extra;
        double len = armLength.get() * u;
        double rad = radius.get() * u + extra;
        double dot = dotSize.get() * u * 0.5;

        switch (st) {
            case Classic -> arms(cx, cy, angle, new double[] {0, 90, 180, 270}, g, g + len, t, grow, c);
            case TShape -> arms(cx, cy, angle, new double[] {0, 90, 180}, g, g + len, t, grow, c);
            case Cross -> arms(cx, cy, angle + Math.PI / 4, new double[] {0, 90, 180, 270}, g, g + len, t, grow, c);
            case Triad -> arms(cx, cy, angle, new double[] {90, 210, 330}, g, g + len, t, grow, c);
            case Plus -> {
                double l = len + extra;
                bar(cx, cy, angle, -l, l, t, grow, c);
                bar(cx, cy, angle + Math.PI / 2, -l, l, t, grow, c);
            }
            case Dot -> disc(cx, cy, Math.max(1, dotSize.get() * u) + extra * 0.3 + grow, c);
            case Circle -> ring(cx, cy, rad, t, grow, dashes.get(), angle, c);
            case CircleDot -> {
                ring(cx, cy, rad, t, grow, dashes.get(), angle, c);
                disc(cx, cy, Math.max(1, dot) + grow, c);
            }
            case Scope -> {
                ring(cx, cy, rad, t, grow, dashes.get(), angle, c);
                double thin = Math.max(1, t * 0.6);
                arms(cx, cy, angle, new double[] {0, 90, 180, 270}, rad * 0.45, rad + len, thin, grow, c);
                disc(cx, cy, Math.max(1, dot) + grow, c);
            }
            case Square -> polygon(cx, cy, rad, 4, angle + Math.PI / 4, t, grow, c, soft);
            case Diamond -> polygon(cx, cy, rad, 4, angle, t, grow, c, soft);
            case Triangle -> polygon(cx, cy, rad, 3, angle - Math.PI / 2, t, grow, c, soft);
            case Chevron -> {
                // "^" just below the center, tip pointing at it
                double w = len, h = len * 0.6;
                double[] p1 = rot(-w, g + h, angle), p2 = rot(0, g, angle), p3 = rot(w, g + h, angle);
                seg(cx + p1[0], cy + p1[1], cx + p2[0], cy + p2[1], t, grow, c);
                seg(cx + p2[0], cy + p2[1], cx + p3[0], cy + p3[1], t, grow, c);
            }
            case Brackets -> brackets(cx, cy, rad, rad * 0.5, angle, t, grow, c);
            case Orbit -> {
                ring(cx, cy, rad, t, grow, orbitDashes.get(), angle, c);
                disc(cx, cy, Math.max(1, dot) + grow, c);
            }
            default -> {}
        }

        if (centerDot.get() && !is(Style.Dot, Style.CircleDot, Style.Orbit, Style.Scope)) disc(cx, cy, Math.max(1, dot) + grow, c);
    }

    /** Cooldown arc / bar, lock-on brackets and the target health ring. */
    private void drawExtras(GundiTheme theme, double cx, double cy, double u, double t, double extra, double cool, Color c, Color oc, double ow) {
        double show = clamp01(cooldownShow.value);
        double outer = outerRadius(u) + extra;

        // Attack cooldown
        if (show > 0.01 && cooldown.get() != Cooldown.None && cooldown.get() != Cooldown.Fade) {
            Color track = Colors.alpha(c, 0.25 * show), fill = Colors.alpha(c, show);
            double th = Math.max(1, t * 0.75);
            if (cooldown.get() == Cooldown.Arc) {
                double r = outer + 4 * u, start = -Math.PI / 2;
                arc(cx, cy, r, th, start, start + Math.PI * 2, Colors.alpha(oc, 0.6 * show), ow);
                arc(cx, cy, r, th, start, start + Math.PI * 2, track, 0);
                arc(cx, cy, r, th, start, start + Math.PI * 2 * cool, fill, 0);
            } else {
                double w = 16 * u, y = cy + outer + 5 * u;
                Shapes2D.rect(cx - w / 2 - ow, y - ow, w + ow * 2, th + ow * 2, (th + ow * 2) / 2, Colors.alpha(oc, 0.6 * show));
                Shapes2D.rect(cx - w / 2, y, w, th, th / 2, track);
                Shapes2D.rect(cx - w / 2, y, w * cool, th, th / 2, fill);
            }
        }

        // Lock-on: four corner brackets close in from further out
        double lock = clamp01(lockSpring.value);
        if (lock > 0.01) {
            double r = outer + (3 + 10 * (1 - lock)) * u;
            Color lc = Colors.alpha(c, lock);
            double spinIn = (1 - lock) * Math.PI / 2;
            if (ow > 0) brackets(cx, cy, r, r * 0.35, spinIn, Math.max(1, t * 0.75), ow, Colors.alpha(oc, lock));
            brackets(cx, cy, r, r * 0.35, spinIn, Math.max(1, t * 0.75), 0, lc);
        }

        // Target health: ring that empties as the target loses health (green -> yellow -> red)
        if (targetHealth.get() && lock > 0.01 || targetHealth.get() && targetSpring.value > 0.01) {
            double a = clamp01(Math.max(lock, targetSpring.value));
            double hp = clamp01(healthSpring.value);
            Color hc = hp > 0.5 ? Colors.mix(new Color(255, 214, 10), new Color(48, 209, 88), (hp - 0.5) * 2) : Colors.mix(new Color(255, 69, 58), new Color(255, 214, 10), hp * 2);
            double r = outer + 8 * u, th = Math.max(1, t * 0.75);
            double start = Math.PI / 2 + Math.PI * 0.25, sweep = Math.PI * 0.5; // bottom quarter arc
            arc(cx, cy, r, th, start, start + sweep, Colors.alpha(new Color(0, 0, 0, 120), a), ow);
            arc(cx, cy, r, th, start, start + sweep * hp, Colors.alpha(hc, a), 0);
        }
    }

    /** Roughly how far the current style reaches from the center (so indicators sit outside it). */
    private double outerRadius(double u) {
        return switch (shownStyle == null ? style.get() : shownStyle) {
            case Classic, TShape, Cross, Triad, Chevron -> (gap.get() + armLength.get()) * u;
            case Plus -> armLength.get() * u;
            case Dot -> dotSize.get() * u;
            case Scope -> (radius.get() + armLength.get()) * u;
            default -> radius.get() * u;
        };
    }

    // ===== Primitives (Renderer2D.COLOR must be begun) =====

    private void arms(double cx, double cy, double angle, double[] degs, double from, double to, double t, double grow, Color c) {
        for (double d : degs) bar(cx, cy, angle + Math.toRadians(d), from, to, t, grow, c);
    }

    /** A line from distance `from` to `to` along `angle` from the center. Straight ones snap to whole pixels. */
    private void bar(double cx, double cy, double angle, double from, double to, double t, double grow, Color c) {
        double ca = Math.cos(angle), sa = Math.sin(angle);
        if (Math.abs(ca) < 1e-6 || Math.abs(sa) < 1e-6) {
            // Axis-aligned: plain rectangle on whole pixels, so it's perfectly crisp
            double a = from - grow, b = to + grow, half = t / 2 + grow;
            double x1 = cx + ca * a, x2 = cx + ca * b, y1 = cy + sa * a, y2 = cy + sa * b;
            double minX = Math.min(x1, x2), maxX = Math.max(x1, x2), minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
            if (Math.abs(ca) > 0.5) { minY = cy - half; maxY = cy + half; } else { minX = cx - half; maxX = cx + half; }
            minX = Math.round(minX); maxX = Math.round(maxX); minY = Math.round(minY); maxY = Math.round(maxY);
            if (maxX > minX && maxY > minY) Renderer2D.COLOR.quad(minX, minY, maxX - minX, maxY - minY, c);
            return;
        }
        seg(cx + ca * from, cy + sa * from, cx + ca * to, cy + sa * to, t, grow, c);
    }

    /** Thick line between two points, lengthened and widened by grow (for outlines). */
    private static void seg(double x1, double y1, double x2, double y2, double t, double grow, Color c) {
        double dx = x2 - x1, dy = y2 - y1, len = Math.hypot(dx, dy);
        if (len < 1e-6) return;
        double ux = dx / len, uy = dy / len;
        x1 -= ux * grow; y1 -= uy * grow; x2 += ux * grow; y2 += uy * grow;
        double nx = -uy * (t / 2 + grow), ny = ux * (t / 2 + grow);
        Shapes2D.tri(x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, c);
        Shapes2D.tri(x1 + nx, y1 + ny, x2 - nx, y2 - ny, x1 - nx, y1 - ny, c);
    }

    private static void disc(double cx, double cy, double r, Color c) {
        Shapes2D.circle(cx, cy, r, c);
    }

    /** Ring of thickness t (outer edge at r), optionally split into dashes. */
    private static void ring(double cx, double cy, double r, double t, double grow, int dashes, double angle, Color c) {
        if (dashes <= 0) {
            arc(cx, cy, r, t, 0, Math.PI * 2, c, grow);
            return;
        }
        double slot = Math.PI * 2 / dashes, gapA = slot * 0.35;
        for (int i = 0; i < dashes; i++) {
            double a = angle + i * slot;
            arc(cx, cy, r, t, a, a + slot - gapA, c, grow);
        }
    }

    /** Part of a ring (outer edge at r, thickness t), grown by `grow` on both edges and ends. */
    private static void arc(double cx, double cy, double r, double t, double a1, double a2, Color c, double grow) {
        if (r <= 0 || c.a <= 0 || a2 <= a1) return;
        double outer = r + grow, inner = Math.max(0, r - t - grow);
        boolean full = a2 - a1 >= Math.PI * 2 - 1e-6;
        if (!full && grow > 0) {
            double extend = grow / Math.max(1, r);
            a1 -= extend;
            a2 += extend;
        }
        int steps = Math.max(8, (int) ((a2 - a1) / (Math.PI * 2) * Math.max(24, r * 2)));
        for (int i = 0; i < steps; i++) {
            double b1 = a1 + (a2 - a1) * i / steps, b2 = a1 + (a2 - a1) * (i + 1) / steps;
            double ox1 = cx + Math.cos(b1) * outer, oy1 = cy + Math.sin(b1) * outer, ox2 = cx + Math.cos(b2) * outer, oy2 = cy + Math.sin(b2) * outer;
            double ix1 = cx + Math.cos(b1) * inner, iy1 = cy + Math.sin(b1) * inner, ix2 = cx + Math.cos(b2) * inner, iy2 = cy + Math.sin(b2) * inner;
            Shapes2D.tri(ox1, oy1, ox2, oy2, ix2, iy2, c);
            Shapes2D.tri(ox1, oy1, ix2, iy2, ix1, iy1, c);
        }
    }

    /** Regular polygon outline (corner points at distance r), optionally filled with a see-through color. */
    private static void polygon(double cx, double cy, double r, int sides, double angle, double t, double grow, Color c, boolean noFill) {
        double[] xs = new double[sides], ys = new double[sides];
        for (int i = 0; i < sides; i++) {
            double a = angle + Math.PI * 2 * i / sides;
            xs[i] = cx + Math.cos(a) * r;
            ys[i] = cy + Math.sin(a) * r;
        }
        Crosshair self = INSTANCE;
        if (!noFill && grow == 0 && self != null && self.filled.get()) {
            Color fc = Colors.alpha(c, 0.3);
            for (int i = 0; i < sides; i++) Shapes2D.tri(cx, cy, xs[i], ys[i], xs[(i + 1) % sides], ys[(i + 1) % sides], fc);
        }
        for (int i = 0; i < sides; i++) seg(xs[i], ys[i], xs[(i + 1) % sides], ys[(i + 1) % sides], t, grow, c);
        // Fill the joints so thick corners don't show notches
        for (int i = 0; i < sides; i++) Shapes2D.circle(xs[i], ys[i], t / 2 + grow, c);
    }

    /** Four L-shaped corners around a square of half-size r, each leg `leg` long, turned by angle. */
    private static void brackets(double cx, double cy, double r, double leg, double angle, double t, double grow, Color c) {
        for (int k = 0; k < 4; k++) {
            double a = angle + Math.PI / 2 * k;
            double[] corner = rot(r, r, a), along1 = rot(r - leg, r, a), along2 = rot(r, r - leg, a);
            seg(cx + corner[0], cy + corner[1], cx + along1[0], cy + along1[1], t, grow, c);
            seg(cx + corner[0], cy + corner[1], cx + along2[0], cy + along2[1], t, grow, c);
            Shapes2D.circle(cx + corner[0], cy + corner[1], t / 2 + grow, c);
        }
    }

    private static double[] rot(double x, double y, double a) {
        double ca = Math.cos(a), sa = Math.sin(a);
        return new double[] {x * ca - y * sa, x * sa + y * ca};
    }

    // ===== Colors =====

    private Color baseColor(GundiTheme theme) {
        if (theme != null) return theme.text.get();
        Color c = color.get();
        if (!rainbow.get()) return c;
        return Colors.hsv(System.nanoTime() / 1e9 * rainbowSpeed.get(), 0.75, 1, c.a);
    }

    /** Custom color, or the theme's color for the same role. */
    private static Color pick(GundiTheme theme, Color custom, Color fromTheme) {
        return theme != null && fromTheme != null ? fromTheme : custom;
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
