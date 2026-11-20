package com.david.gundi.hud;

import com.david.gundi.Gundi;
import com.david.gundi.gui.Spring;
import com.david.gundi.util.CrispText;
import com.david.gundi.util.ErrorReporter;
import com.david.gundi.util.ThemeLink;
import com.david.gundi.util.HudShapes;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Circular radar, you in the middle. Shows every loaded player and every loaded chest
 * (single and double chests look different), with N / E / S / W, height arrows and a smooth auto-zoom
 * (no range limit: it zooms out so everything loaded fits).
 *
 * Look: dark glass disc, glowing rim with a compass bezel, distance rings with labels, an optional radar sweep,
 * your view cone, glowing markers that fade/grow in and fade out. Almost everything is customizable.
 *
 * Drawing order: background -> grid -> sweep -> chests -> players -> you -> border/bezel -> directions -> text.
 */
public class RadarHud extends GundiHud {
    public static final HudElementInfo<RadarHud> INFO = new HudElementInfo<>(Gundi.HUD_GROUP, "radar", "Circular radar with players, chests and directions.", RadarHud::new);

    private static final String NAME = "radar";

    public enum BackgroundStyle { Glass, Solid, Minimal }
    public enum DirectionStyle { Chips, Letters, None }
    public enum DirectionPosition { Rim, Inside, Outside }
    public enum PlayerIcon { Arrow, Dot, Diamond, Head }
    public enum ChestIcon { Icons, Shapes, Dots }
    public enum SelfIcon { Arrow, Dot }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    // Follow the selected GUI theme (first setting in General). See util/ThemeLink.
    private final ThemeLink link = new ThemeLink(sgGeneral);
    private final SettingGroup sgShow = settings.createGroup("Show");
    private final SettingGroup sgPlayers = settings.createGroup("Players");
    private final SettingGroup sgChests = settings.createGroup("Chests");
    private final SettingGroup sgSelf = settings.createGroup("You", false);
    private final SettingGroup sgDirections = settings.createGroup("Directions", false);
    private final SettingGroup sgBackground = settings.createGroup("Disc", false);
    private final SettingGroup sgGrid = settings.createGroup("Grid & Sweep", false);
    private final SettingGroup sgEffects = settings.createGroup("Effects", false);

    // ===== General =====

    private final Setting<Integer> radarSize = sgGeneral.add(new IntSetting.Builder()
        .name("radar-size").description("Radius of the radar circle. Bigger = more room between markers.")
        .defaultValue(150).range(40, 400).sliderRange(60, 250).build());

    private final Setting<Double> iconScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("icon-scale").description("Size of markers, arrows and text (the circle keeps its size).")
        .defaultValue(1).range(0.5, 3).build());

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate").description("Turn the radar with you (up = where you face). Off = north is always up.")
        .defaultValue(true).build());

    private final Setting<Double> heightThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("height-threshold").description("Blocks above/below you before a height arrow shows.")
        .defaultValue(3).range(0.5, 64).sliderRange(1, 16).build());

    // ===== Show =====

    private final Setting<Boolean> players = sgShow.add(new BoolSetting.Builder().name("players").description("Show players.").defaultValue(true).build());
    private final Setting<Boolean> chests = sgShow.add(new BoolSetting.Builder().name("chests").description("Show chests and trapped chests.").defaultValue(true).build());
    private final Setting<Boolean> heightArrows = sgShow.add(new BoolSetting.Builder().name("height-arrows").description("Arrows for things above/below you.").defaultValue(true).build());
    private final Setting<Boolean> directions = sgShow.add(new BoolSetting.Builder().name("directions").description("N / E / S / W.").defaultValue(true).build());

    // ===== Background =====

    private final Setting<BackgroundStyle> bgStyle = sgBackground.add(new EnumSetting.Builder<BackgroundStyle>()
        .name("disc-style").description("Glass = soft light in the middle + glass reflection. Solid = flat. Minimal = no fill.")
        .defaultValue(BackgroundStyle.Glass).build());

    private final Setting<SettingColor> bgColor = sgBackground.add(new ColorSetting.Builder()
        .name("background-color").description("Disc color; its alpha is the opacity.")
        .defaultValue(new SettingColor(14, 15, 20, 205)).visible(() -> !link.useTheme.get() && bgStyle.get() != BackgroundStyle.Minimal).build());

    private final Setting<Double> highlight = sgBackground.add(new DoubleSetting.Builder()
        .name("glass-highlight").description("Strength of the light in the middle and the reflection on top.")
        .defaultValue(1).range(0, 3).visible(() -> bgStyle.get() == BackgroundStyle.Glass).build());

    private final Setting<Boolean> shadow = sgBackground.add(new BoolSetting.Builder()
        .name("shadow").description("Soft shadow under the radar.").defaultValue(true).build());

    // ===== Border =====

    private final Setting<Boolean> border = sgBackground.add(new BoolSetting.Builder().name("border").description("Rim around the radar.").defaultValue(true).build());

    private final Setting<SettingColor> borderColor = sgBackground.add(new ColorSetting.Builder()
        .name("border-color").description("Rim color; alpha = opacity.").defaultValue(new SettingColor(255, 255, 255, 70))
        .visible(() -> !link.useTheme.get() && border.get()).build());

    private final Setting<Double> borderThickness = sgBackground.add(new DoubleSetting.Builder()
        .name("border-thickness").description("Rim thickness in pixels.").defaultValue(1.5).range(0.5, 8).visible(border::get).build());

    private final Setting<Boolean> borderGlow = sgBackground.add(new BoolSetting.Builder()
        .name("border-glow").description("Soft glow outside the rim.").defaultValue(true).visible(border::get).build());

    private final Setting<SettingColor> borderGlowColor = sgBackground.add(new ColorSetting.Builder()
        .name("border-glow-color").description("Color of the rim glow.").defaultValue(new SettingColor(10, 132, 255, 90))
        .visible(() -> !link.useTheme.get() && border.get() && borderGlow.get()).build());

    private final Setting<Boolean> bezel = sgBackground.add(new BoolSetting.Builder()
        .name("bezel-ticks").description("Compass tick marks along the inside of the rim (every 10°, longer every 30°).").defaultValue(true).build());

    private final Setting<SettingColor> bezelColor = sgBackground.add(new ColorSetting.Builder()
        .name("bezel-color").description("Tick mark color.").defaultValue(new SettingColor(255, 255, 255, 55))
        .visible(() -> !link.useTheme.get() && bezel.get()).build());

    // ===== Grid =====

    private final Setting<Integer> rings = sgGrid.add(new IntSetting.Builder()
        .name("rings").description("Number of distance rings (0 = none).").defaultValue(3).range(0, 6).build());

    private final Setting<Boolean> ringLabels = sgGrid.add(new BoolSetting.Builder()
        .name("ring-labels").description("Distance on each ring (follows the auto-zoom).").defaultValue(true).visible(() -> rings.get() > 0).build());

    private final Setting<Boolean> crosshair = sgGrid.add(new BoolSetting.Builder()
        .name("crosshair").description("Faint lines through the middle.").defaultValue(true).build());

    private final Setting<SettingColor> gridColor = sgGrid.add(new ColorSetting.Builder()
        .name("grid-color").description("Rings and crosshair.").defaultValue(new SettingColor(255, 255, 255, 18)).visible(link.own()).build());

    // ===== Sweep =====

    private final Setting<Boolean> sweep = sgGrid.add(new BoolSetting.Builder()
        .name("sweep").description("Rotating radar beam.").defaultValue(true).build());

    private final Setting<SettingColor> sweepColor = sgGrid.add(new ColorSetting.Builder()
        .name("sweep-color").description("Beam color; alpha = strength.").defaultValue(new SettingColor(10, 132, 255, 70))
        .visible(() -> !link.useTheme.get() && sweep.get()).build());

    private final Setting<Double> sweepSpeed = sgGrid.add(new DoubleSetting.Builder()
        .name("sweep-speed").description("Turns per second.").defaultValue(0.25).range(0.02, 2).visible(sweep::get).build());

    private final Setting<Integer> sweepTrail = sgGrid.add(new IntSetting.Builder()
        .name("sweep-trail").description("Length of the fading trail, in degrees.").defaultValue(70).range(10, 180).visible(sweep::get).build());

    // ===== Directions =====

    private final Setting<DirectionStyle> dirStyle = sgDirections.add(new EnumSetting.Builder<DirectionStyle>()
        .name("direction-style").description("Chips = letters in small circles. Letters = just the letters.").defaultValue(DirectionStyle.Chips).build());

    private final Setting<DirectionPosition> dirPosition = sgDirections.add(new EnumSetting.Builder<DirectionPosition>()
        .name("position").description("On the rim, just inside it, or just outside it.").defaultValue(DirectionPosition.Rim).build());

    private final Setting<Boolean> intercardinal = sgDirections.add(new BoolSetting.Builder()
        .name("intercardinal").description("Also show NE / SE / SW / NW (smaller, dim).").defaultValue(false).build());

    private final Setting<Double> dirSize = sgDirections.add(new DoubleSetting.Builder()
        .name("text-size").description("Size of the direction letters.").defaultValue(1).range(0.5, 2.5).build());

    private final Setting<SettingColor> northColor = sgDirections.add(new ColorSetting.Builder()
        .name("north-color").description("North is highlighted, like Apple's compass.").defaultValue(new SettingColor(255, 69, 58)).build());

    private final Setting<SettingColor> dirColor = sgDirections.add(new ColorSetting.Builder()
        .name("letter-color").description("E / S / W letters.").defaultValue(new SettingColor(235, 235, 240)).visible(link.own()).build());

    private final Setting<SettingColor> chipColor = sgDirections.add(new ColorSetting.Builder()
        .name("chip-color").description("Background of the chips.").defaultValue(new SettingColor(30, 31, 38, 235))
        .visible(() -> !link.useTheme.get() && dirStyle.get() == DirectionStyle.Chips).build());

    // ===== Players =====

    private final Setting<PlayerIcon> playerIcon = sgPlayers.add(new EnumSetting.Builder<PlayerIcon>()
        .name("player-icon").description("Arrow = points where they face. Head = their skin.").defaultValue(PlayerIcon.Arrow).build());

    private final Setting<Boolean> names = sgPlayers.add(new BoolSetting.Builder()
        .name("names").description("Name next to each player.").defaultValue(true).build());

    private final Setting<Boolean> nameDistance = sgPlayers.add(new BoolSetting.Builder()
        .name("name-distance").description("Add the distance after the name.").defaultValue(false).visible(names::get).build());

    private final Setting<SettingColor> playerColor = sgPlayers.add(new ColorSetting.Builder()
        .name("player-color").description("Other players.").defaultValue(new SettingColor(255, 69, 58)).build());

    private final Setting<SettingColor> friendColor = sgPlayers.add(new ColorSetting.Builder()
        .name("friend-color").description("Friends.").defaultValue(new SettingColor(100, 210, 255)).build());

    private final Setting<SettingColor> nameColor = sgPlayers.add(new ColorSetting.Builder()
        .name("name-color").description("Name text.").defaultValue(new SettingColor(245, 245, 247, 230))
        .visible(() -> !link.useTheme.get() && names.get()).build());

    // ===== Chests =====

    private final Setting<ChestIcon> chestIcon = sgChests.add(new EnumSetting.Builder<ChestIcon>()
        .name("chest-icon").description("Icons = little chests (double = wide). Shapes = circle / square. Dots = small / big dot.")
        .defaultValue(ChestIcon.Icons).build());

    private final Setting<SettingColor> singleColor = sgChests.add(new ColorSetting.Builder()
        .name("single-color").description("Single chests.").defaultValue(new SettingColor(255, 214, 10)).build());

    private final Setting<SettingColor> doubleColor = sgChests.add(new ColorSetting.Builder()
        .name("double-color").description("Double chests.").defaultValue(new SettingColor(255, 159, 10)).build());

    private final Setting<Boolean> clustering = sgChests.add(new BoolSetting.Builder()
        .name("clustering").description("Chests close together on the radar merge into one marker with a count badge. Nothing is hidden.")
        .defaultValue(true).build());

    private final Setting<Integer> clusterSize = sgChests.add(new IntSetting.Builder()
        .name("cluster-size").description("How close (in pixels on the radar) chests must be to merge.")
        .defaultValue(16).range(4, 80).sliderRange(6, 40).visible(clustering::get).build());

    private final Setting<SettingColor> badgeColor = sgChests.add(new ColorSetting.Builder()
        .name("badge-color").description("Count badge (like an app badge on iPhone).")
        .defaultValue(new SettingColor(255, 59, 48)).visible(clustering::get).build());

    // ===== Self =====

    private final Setting<SelfIcon> selfIcon = sgSelf.add(new EnumSetting.Builder<SelfIcon>()
        .name("self-icon").description("How you are shown in the middle.").defaultValue(SelfIcon.Arrow).build());

    private final Setting<SettingColor> selfColor = sgSelf.add(new ColorSetting.Builder()
        .name("self-color").description("Your marker.").defaultValue(new SettingColor(255, 255, 255)).visible(link.own()).build());

    private final Setting<Boolean> viewCone = sgSelf.add(new BoolSetting.Builder()
        .name("view-cone").description("Soft cone showing your field of view.").defaultValue(true).build());

    private final Setting<SettingColor> coneColor = sgSelf.add(new ColorSetting.Builder()
        .name("cone-color").description("View cone color; alpha = strength.").defaultValue(new SettingColor(10, 132, 255, 55))
        .visible(() -> !link.useTheme.get() && viewCone.get()).build());

    // ===== Effects =====

    private final Setting<Boolean> glow = sgEffects.add(new BoolSetting.Builder()
        .name("glow").description("Soft glow around markers and the north indicator.").defaultValue(true).build());

    private final Setting<Double> glowIntensity = sgEffects.add(new DoubleSetting.Builder()
        .name("glow-intensity").description("How strong the glow is.").defaultValue(1).range(0.1, 3).visible(glow::get).build());

    private final Setting<Boolean> animations = sgGeneral.add(new BoolSetting.Builder()
        .name("animations").description("Fade/grow in, fade out, ripples, smooth zoom. Off = instant.").defaultValue(true).build());

    private final Setting<Double> animationSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("animation-speed").description("1 = default, higher = faster.").defaultValue(1).range(0.25, 4).visible(animations::get).build());

    private final Setting<Boolean> ripples = sgEffects.add(new BoolSetting.Builder()
        .name("ripples").description("A ripple when a new player shows up.").defaultValue(true).visible(animations::get).build());

    // ===== State =====

    private static final int PLAYER = 0, SINGLE = 1, DOUBLE = 2;
    private static final double MIN_ZOOM = 16;

    /** One chest in world coordinates. For a double chest: the middle of both halves. */
    private record Chest(long key, double x, double y, double z, boolean isDouble) {}

    /** Something on the radar. Kept a moment after it unloads so it can fade out. */
    private static final class Marker {
        int kind;
        double wx, wy, wz;     // world position
        float yaw;             // players: where they face
        String name;
        boolean friend;
        AbstractClientPlayerEntity skinOwner;
        final Spring alpha = new Spring(0);
        final long born = System.nanoTime();
        boolean seen;
    }

    private final List<Chest> chestList = new ArrayList<>();
    private final Map<Object, Marker> markers = new HashMap<>();
    private final Spring zoom = new Spring(MIN_ZOOM);
    private double sweepAngle;

    // Per-frame view, set at the start of render()
    private double cx, cy, radius, sin, cos, s;

    public RadarHud() {
        super(INFO);
    }

    /** These had the same names in several groups; they got unique names so saved values still load. */
    @Override
    protected Map<String, String> renamedSettings() {
        return Map.of(
            "Background/style", "disc-style",
            "Directions/style", "direction-style",
            "Players/icon", "player-icon",
            "Chests/icon", "chest-icon",
            "Self/icon", "self-icon",
            "Self/color", "self-color"
        );
    }

    /** 20 times a second: refresh the list of loaded chests (chests don't move, no need every frame). */
    @Override
    public void tick(HudRenderer renderer) {
        try {
            chestList.clear();
            if (mc.world == null || !chests.get()) return;

            for (BlockEntity be : Utils.blockEntities()) {
                if (!(be instanceof ChestBlockEntity)) continue; // also covers trapped chests

                BlockPos pos = be.getPos();
                BlockState state = be.getCachedState();
                if (!state.contains(ChestBlock.CHEST_TYPE)) continue;

                ChestType type = state.get(ChestBlock.CHEST_TYPE);
                if (type == ChestType.SINGLE) {
                    chestList.add(new Chest(pos.asLong(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, false));
                } else if (type == ChestType.LEFT) {
                    // A double chest is two block entities: only use the LEFT half so it shows once, in the middle.
                    Direction toOther = ChestBlock.getFacing(state);
                    chestList.add(new Chest(pos.asLong(),
                        pos.getX() + 0.5 + toOther.getOffsetX() * 0.5, pos.getY() + 0.5, pos.getZ() + 0.5 + toOther.getOffsetZ() * 0.5, true));
                }
            }
        } catch (Exception e) {
            ErrorReporter.report(NAME, "tick", e);
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        try {
            link.begin();
            s = iconScale.get();
            radius = radarSize.get();
            double outer = dirPosition.get() == DirectionPosition.Outside ? 16 * s * dirSize.get() : 8 * s;
            double size = (radius + outer) * 2;
            setSize(size, size);
            cx = x + size / 2;
            cy = y + size / 2;

            double dt = link.dt(renderer);
            boolean anim = animations.get();
            double speed = anim ? animationSpeed.get() : 1;

            // View: player position + facing (north-up when rotate is off, or when not in a world)
            boolean inWorld = mc.player != null && mc.world != null;
            float delta = mc.getRenderTickCounter().getTickProgress(true);
            double px = 0, py = 0, pz = 0, yaw = Math.PI;
            if (inWorld) {
                px = mc.player.lastX + (mc.player.getX() - mc.player.lastX) * delta;
                py = mc.player.getY();
                pz = mc.player.lastZ + (mc.player.getZ() - mc.player.lastZ) * delta;
                if (rotate.get()) yaw = Math.toRadians(mc.player.getYaw(delta));
            }
            sin = Math.sin(yaw);
            cos = Math.cos(yaw);

            if (inWorld) updateMarkers(px, pz, delta, dt, anim, speed);

            // Auto-zoom: fit the furthest visible marker (+10% room), never closer than MIN_ZOOM
            double furthest = 0;
            for (Marker m : markers.values()) if (m.seen) furthest = Math.max(furthest, Math.hypot(m.wx - px, m.wz - pz));
            double targetZoom = Math.max(MIN_ZOOM, furthest * 1.1);
            if (anim) zoom.update(targetZoom, dt, 0.6 / speed);
            else zoom.snap(targetZoom);

            drawBackground(renderer);
            drawGrid(renderer);
            if (sweep.get()) drawSweep(renderer, dt);

            // Markers: chests first (grouped if clustering is on), players on top
            long now = System.nanoTime();
            List<Marker> chestMarkers = new ArrayList<>(), playerMarkers = new ArrayList<>();
            for (Marker m : markers.values()) {
                if (m.alpha.value <= 0.01) continue;
                (m.kind == PLAYER ? playerMarkers : chestMarkers).add(m);
            }

            if (clustering.get()) drawChestClusters(renderer, chestMarkers, px, py, pz);
            else for (Marker m : chestMarkers) drawChest(renderer, m, toRadar(m.wx - px, m.wz - pz), clamp01(m.alpha.value), py);

            for (Marker m : playerMarkers) {
                drawPlayer(renderer, m, toRadar(m.wx - px, m.wz - pz), clamp01(m.alpha.value), py, now, anim);
            }

            if (viewCone.get() && inWorld) drawViewCone(renderer);
            drawSelf(renderer, inWorld);
            drawBorder(renderer);
            if (directions.get() && dirStyle.get() != DirectionStyle.None) drawDirections(renderer);
        } catch (Exception e) {
            ErrorReporter.report(NAME, "render", e);
        }
    }

    // ===== Markers =====

    /** Sync markers with what's loaded right now; markers that disappeared fade out, then get removed. */
    private void updateMarkers(double px, double pz, float delta, double dt, boolean anim, double speed) {
        for (Marker m : markers.values()) m.seen = false;

        if (players.get()) {
            for (PlayerEntity pl : mc.world.getPlayers()) {
                if (pl == mc.player) continue;
                Marker m = markers.computeIfAbsent(pl.getUuid(), k -> new Marker());
                m.kind = PLAYER;
                m.wx = pl.lastX + (pl.getX() - pl.lastX) * delta;
                m.wy = pl.getY();
                m.wz = pl.lastZ + (pl.getZ() - pl.lastZ) * delta;
                m.yaw = pl.getYaw(delta);
                m.name = pl.getName().getString();
                m.friend = Friends.get().isFriend(pl);
                m.skinOwner = pl instanceof AbstractClientPlayerEntity cp ? cp : null;
                m.seen = true;
            }
        }

        if (chests.get()) {
            for (Chest c : chestList) {
                Marker m = markers.computeIfAbsent(c.key, k -> new Marker());
                m.kind = c.isDouble ? DOUBLE : SINGLE;
                m.wx = c.x;
                m.wy = c.y;
                m.wz = c.z;
                m.seen = true;
            }
        }

        for (Iterator<Marker> it = markers.values().iterator(); it.hasNext(); ) {
            Marker m = it.next();
            double target = m.seen ? 1 : 0;
            if (anim) m.alpha.update(target, dt, 0.35 / speed);
            else m.alpha.snap(target);
            if (!m.seen && m.alpha.value < 0.01) it.remove();
        }
    }

    private void drawPlayer(HudRenderer r, Marker m, double[] p, double a, double py, long now, boolean anim) {
        Color col = m.friend ? friendColor.get() : playerColor.get();
        double grow = 0.6 + 0.4 * a; // grows in while fading in
        double size = 4.5 * s * grow;

        // Ripple when a new player shows up
        double age = (now - m.born) / 1e9 * (anim ? animationSpeed.get() : 1);
        if (anim && ripples.get() && age < 1) {
            HudShapes.ring(r, p[0], p[1], size + age * 18 * s, Math.max(1, 1.5 * s), HudShapes.scaleAlpha(col, (1 - age) * 0.8 * a));
        }

        if (glow.get()) {
            double gi = glowIntensity.get();
            HudShapes.circle(r, p[0], p[1], size * 2.2, HudShapes.scaleAlpha(col, 0.10 * gi * a));
            HudShapes.circle(r, p[0], p[1], size * 1.5, HudShapes.scaleAlpha(col, 0.18 * gi * a));
        }

        Color c = HudShapes.scaleAlpha(col, a);
        Color outline = new Color(255, 255, 255, (int) (200 * a));
        switch (playerIcon.get()) {
            case Dot -> {
                HudShapes.circle(r, p[0], p[1], size + 1.2 * s, outline);
                HudShapes.circle(r, p[0], p[1], size, c);
            }
            case Diamond -> {
                diamond(r, p[0], p[1], size + 1.5 * s, outline);
                diamond(r, p[0], p[1], size, c);
            }
            case Arrow -> {
                // Their facing direction in the world, turned the same way as everything else
                double yr = Math.toRadians(m.yaw);
                double ang = screenAngle(-Math.sin(yr), Math.cos(yr));
                arrow(r, p[0], p[1], size * 1.35 + 1.2 * s, ang, outline);
                arrow(r, p[0], p[1], size * 1.35, ang, c);
            }
            case Head -> {
                int hs = (int) Math.round(size * 2.4);
                HudShapes.rect(r, p[0] - hs / 2.0 - 1.5 * s, p[1] - hs / 2.0 - 1.5 * s, hs + 3 * s, hs + 3 * s, 3 * s, c);
                // Skins can't be faded, so only draw the face while mostly visible
                if (m.skinOwner != null && a > 0.5) {
                    // Minecraft's GUI drawing is scaled by the GUI scale; HUD positions are in real pixels, so undo it
                    // (same as Meteor's drawItem does for HUD items).
                    var matrices = r.drawContext.getMatrices();
                    matrices.pushMatrix();
                    matrices.scale(1.0f / mc.getWindow().getScaleFactor());
                    PlayerSkinDrawer.draw(r.drawContext, m.skinOwner.getSkin(), (int) Math.round(p[0] - hs / 2.0), (int) Math.round(p[1] - hs / 2.0), hs);
                    matrices.popMatrix();
                }
            }
        }

        if (heightArrows.get()) heightArrow(r, p[0], p[1], m.wy - py, size + 2.5 * s, c);

        if (names.get() && m.name != null) {
            String text = m.name;
            if (nameDistance.get() && mc.player != null) text += "  " + (int) Math.hypot(m.wx - mc.player.getX(), m.wz - mc.player.getZ()) + "m";
            double ts = 10 * s;
            CrispText.draw(r, text, p[0] + size + 4 * s, p[1] - CrispText.height(r, ts) / 2, HudShapes.scaleAlpha(link.text(nameColor), a), true, ts);
        }
    }

    private void drawChest(HudRenderer r, Marker m, double[] p, double a, double py) {
        boolean dbl = m.kind == DOUBLE;
        Color col = HudShapes.scaleAlpha(dbl ? doubleColor.get() : singleColor.get(), a);
        double grow = 0.6 + 0.4 * a;
        double h = 3.4 * s * grow;  // half height of the marker

        if (glow.get()) HudShapes.circle(r, p[0], p[1], h * (dbl ? 2.6 : 2.0), HudShapes.scaleAlpha(col, 0.12 * glowIntensity.get()));

        switch (chestIcon.get()) {
            case Icons -> {
                // Little chest: rounded body, darker lid band, latch. Double = twice as wide with a split line.
                double hw = dbl ? h * 1.9 : h;
                HudShapes.rect(r, p[0] - hw, p[1] - h, hw * 2, h * 2, h * 0.45, col);
                Color dark = new Color(0, 0, 0, (int) (90 * a));
                HudShapes.rect(r, p[0] - hw, p[1] - h * 0.25, hw * 2, Math.max(1, h * 0.28), 0, dark);
                if (dbl) HudShapes.rect(r, p[0] - Math.max(0.5, s * 0.5), p[1] - h, Math.max(1, s), h * 2, 0, dark);
                Color latch = new Color(255, 255, 255, (int) (220 * a));
                double lw = Math.max(1.2, h * 0.45);
                if (dbl) {
                    HudShapes.rect(r, p[0] - hw * 0.5 - lw / 2, p[1] - h * 0.35, lw, lw * 1.3, lw * 0.3, latch);
                    HudShapes.rect(r, p[0] + hw * 0.5 - lw / 2, p[1] - h * 0.35, lw, lw * 1.3, lw * 0.3, latch);
                } else {
                    HudShapes.rect(r, p[0] - lw / 2, p[1] - h * 0.35, lw, lw * 1.3, lw * 0.3, latch);
                }
            }
            case Shapes -> {
                if (dbl) HudShapes.rect(r, p[0] - h * 1.15, p[1] - h * 1.15, h * 2.3, h * 2.3, h * 0.3, col);
                else HudShapes.circle(r, p[0], p[1], h, col);
            }
            case Dots -> {
                if (dbl) {
                    HudShapes.ring(r, p[0], p[1], h * 1.5, Math.max(1, s), col);
                    HudShapes.circle(r, p[0], p[1], h * 0.9, col);
                } else {
                    HudShapes.circle(r, p[0], p[1], h * 0.7, col);
                }
            }
        }

        if (heightArrows.get()) heightArrow(r, p[0], p[1], m.wy - py, h + 2.5 * s, col);
    }

    /**
     * Groups chests that would sit within cluster-size pixels of each other into one marker with a count badge.
     * Grouping uses a grid in WORLD space (cell size follows the zoom, rounded to powers of 2 blocks), so groups
     * stay put while you turn around instead of flickering. A cell with one chest is drawn as a normal chest.
     */
    private void drawChestClusters(HudRenderer r, List<Marker> chestMarkers, double px, double py, double pz) {
        double pixelsPerBlock = radius / Math.max(1, zoom.value);
        double cellBlocks = clusterSize.get() * s / pixelsPerBlock;
        cellBlocks = Math.pow(2, Math.ceil(Math.log(Math.max(1, cellBlocks)) / Math.log(2)));

        Map<Long, List<Marker>> cells = new HashMap<>();
        for (Marker m : chestMarkers) {
            long gx = (long) Math.floor(m.wx / cellBlocks), gz = (long) Math.floor(m.wz / cellBlocks);
            cells.computeIfAbsent(gx * 31_000_000L + gz, k -> new ArrayList<>()).add(m);
        }

        for (List<Marker> group : cells.values()) {
            if (group.size() == 1) {
                Marker m = group.get(0);
                drawChest(r, m, toRadar(m.wx - px, m.wz - pz), clamp01(m.alpha.value), py);
                continue;
            }

            // Middle of the group, strongest fade value, average height
            double sx = 0, sy = 0, sz = 0, a = 0;
            boolean anyDouble = false;
            for (Marker m : group) {
                sx += m.wx;
                sy += m.wy;
                sz += m.wz;
                a = Math.max(a, clamp01(m.alpha.value));
                if (m.kind == DOUBLE) anyDouble = true;
            }
            int n = group.size();
            Marker c = new Marker();
            c.kind = anyDouble ? DOUBLE : SINGLE;
            c.wx = sx / n;
            c.wy = sy / n;
            c.wz = sz / n;

            double[] p = toRadar(c.wx - px, c.wz - pz);
            drawChest(r, c, p, a, py);
            countBadge(r, p[0], p[1], n, a);
        }
    }

    /** Red app-style badge with the number of chests, at the top-right of a cluster marker. */
    private void countBadge(HudRenderer r, double mx, double my, int count, double a) {
        String text = count > 99 ? "99+" : String.valueOf(count);
        double ts = 8 * s;
        double tw = CrispText.width(r, text, ts), th = CrispText.height(r, ts);
        double h = th + 2 * s, w = Math.max(h, tw + 5 * s);
        double bx = mx + 4 * s, by = my - 4 * s - h;

        HudShapes.rect(r, bx - 1, by - 1, w + 2, h + 2, (h + 2) / 2, new Color(0, 0, 0, (int) (110 * a))); // dark outline
        HudShapes.rect(r, bx, by, w, h, h / 2, HudShapes.scaleAlpha(badgeColor.get(), a));
        CrispText.draw(r, text, bx + (w - tw) / 2, by + (h - th) / 2, new Color(255, 255, 255, (int) (255 * a)), false, ts);
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** Arrow above the marker if it's higher than you, below if lower, nothing if about your level. */
    private void heightArrow(HudRenderer r, double mx, double my, double dy, double offset, Color col) {
        if (Math.abs(dy) < heightThreshold.get()) return;
        double a = 2.6 * s;
        if (dy > 0) {
            double tip = my - offset - a * 1.4;
            HudShapes.tri(r, mx, tip, mx + a, tip + a * 1.2, mx - a, tip + a * 1.2, col);
        } else {
            double tip = my + offset + a * 1.4;
            HudShapes.tri(r, mx, tip, mx - a, tip - a * 1.2, mx + a, tip - a * 1.2, col);
        }
    }

    // ===== Background, grid, sweep, border =====

    private void drawBackground(HudRenderer r) {
        // A real circle shadow (see HudShapes.circleShadow for why not shadow()). Follows the theme's shadow settings.
        var theme = link.theme();
        boolean shadowOn = shadow.get() && (theme == null || theme.shadows.get());
        double strength = theme != null ? theme.shadowStrength.get() : 1;
        if (shadowOn && strength > 0) HudShapes.circleShadow(r, cx, cy, radius, 10 * s, (int) Math.min(255, 110 * strength));
        if (bgStyle.get() == BackgroundStyle.Minimal) return;

        HudShapes.circle(r, cx, cy, radius, link.tint(bgColor));
        if (bgStyle.get() != BackgroundStyle.Glass) return;

        double hl = highlight.get();
        // Soft light in the middle: stacked faint circles, brightest in the center
        for (int i = 1; i <= 5; i++) {
            HudShapes.circle(r, cx, cy, radius * (1 - i * 0.16), new Color(255, 255, 255, (int) (3 * hl)));
        }
        // Darker towards the edge
        for (int i = 0; i < 4; i++) {
            HudShapes.ring(r, cx, cy, radius - i * radius * 0.04, radius * 0.04, new Color(0, 0, 0, (int) ((30 - i * 7) * Math.min(1, hl))));
        }
        // Glass reflection: a thin bright arc along the top
        HudShapes.arc(r, cx, cy, radius * 0.93, Math.max(1, radius * 0.012), Math.toRadians(205), Math.toRadians(335),
            new Color(255, 255, 255, (int) (28 * hl)));
    }

    private void drawGrid(HudRenderer r) {
        Color gc = link.tile(gridColor);
        int n = rings.get();
        // Evenly spaced rings; the outermost one is the rim itself (skipped when the border already draws it)
        for (int i = 1; i <= n; i++) {
            if (i == n && border.get()) continue;
            HudShapes.ring(r, cx, cy, radius * i / (double) n, Math.max(1, 0.8 * s), gc);
        }

        if (crosshair.get()) {
            double len = radius * 0.96;
            // Lines follow the world axes when rotating, so they turn with the radar
            double[] a1 = dirVec(0, -1), a2 = dirVec(1, 0);
            r.line(cx - a1[0] * len, cy - a1[1] * len, cx + a1[0] * len, cy + a1[1] * len, gc);
            r.line(cx - a2[0] * len, cy - a2[1] * len, cx + a2[0] * len, cy + a2[1] * len, gc);
        }

        if (n > 0 && ringLabels.get()) {
            double ts = 8.5 * s;
            Color lc = HudShapes.scaleAlpha(new Color(235, 235, 240), Math.min(1, gc.a / 18.0 * 0.55));
            for (int i = 1; i <= n; i++) {
                double rr = radius * i / (double) n;
                String label = Math.round(zoom.value * i / n) + "m";
                double ang = Math.toRadians(-45); // top-right, away from the N chip
                double lx = cx + Math.cos(ang) * rr, ly = cy + Math.sin(ang) * rr;
                CrispText.draw(r, label, lx + 2 * s, ly - CrispText.height(r, ts), lc, false, ts);
            }
        }
    }

    private void drawSweep(HudRenderer r, double dt) {
        boolean anim = animations.get();
        sweepAngle += dt * sweepSpeed.get() * Math.PI * 2 * (anim ? animationSpeed.get() : 1);
        sweepAngle %= Math.PI * 2;

        Color c = link.accent(sweepColor);
        double trail = Math.toRadians(sweepTrail.get());
        int slices = 24;
        for (int k = 0; k < slices; k++) {
            double a1 = sweepAngle - trail * (k + 1) / slices, a2 = sweepAngle - trail * k / slices;
            double f = 1 - (double) k / slices;
            sector(r, cx, cy, radius * 0.985, a1, a2, HudShapes.scaleAlpha(c, f * f));
        }
        // Bright leading edge
        r.line(cx, cy, cx + Math.cos(sweepAngle) * radius * 0.985, cy + Math.sin(sweepAngle) * radius * 0.985,
            HudShapes.scaleAlpha(c, 2.2)); // brighter than the trail
    }

    private void drawBorder(HudRenderer r) {
        if (border.get()) {
            double t = borderThickness.get();
            if (borderGlow.get()) {
                Color g = link.glow(borderGlowColor);
                double gi = glow.get() ? glowIntensity.get() : 1;
                for (int i = 1; i <= 5; i++) {
                    double f = 1 - (i - 1) / 5.0;
                    HudShapes.ring(r, cx, cy, radius + i * 1.6 * s, 1.6 * s, HudShapes.scaleAlpha(g, f * f * 0.6 * gi));
                }
            }
            HudShapes.ring(r, cx, cy, radius, t, link.outline(borderColor));
        }

        if (bezel.get()) {
            Color bc = link.tile(bezelColor);
            for (int deg = 0; deg < 360; deg += 10) {
                if (deg % 90 == 0) continue; // the direction indicators sit there
                double b = Math.toRadians(deg);
                double[] v = dirVec(Math.sin(b), -Math.cos(b)); // compass bearing -> world vector -> screen
                double len = (deg % 30 == 0 ? 7 : 3.5) * s;
                double r1 = radius - 2 * s - len, r2 = radius - 2 * s;
                r.line(cx + v[0] * r1, cy + v[1] * r1, cx + v[0] * r2, cy + v[1] * r2, deg % 30 == 0 ? bc : HudShapes.scaleAlpha(bc, 0.6));
            }
        }
    }

    // ===== Directions =====

    private void drawDirections(HudRenderer r) {
        double ds = dirSize.get();
        double dist = switch (dirPosition.get()) {
            case Rim -> radius;
            case Inside -> radius - 14 * s * ds;
            case Outside -> radius + 10 * s * ds;
        };

        String[] main = {"N", "E", "S", "W"};
        for (int i = 0; i < 4; i++) {
            double b = Math.toRadians(i * 90);
            double[] v = dirVec(Math.sin(b), -Math.cos(b));
            directionLabel(r, main[i], cx + v[0] * dist, cy + v[1] * dist, 12 * s * ds, i == 0, true);
        }

        if (intercardinal.get()) {
            String[] mid = {"NE", "SE", "SW", "NW"};
            for (int i = 0; i < 4; i++) {
                double b = Math.toRadians(45 + i * 90);
                double[] v = dirVec(Math.sin(b), -Math.cos(b));
                directionLabel(r, mid[i], cx + v[0] * dist, cy + v[1] * dist, 8.5 * s * ds, false, false);
            }
        }
    }

    private void directionLabel(HudRenderer r, String text, double lx, double ly, double ts, boolean north, boolean big) {
        Color letter = north ? northColor.get() : (big ? link.text(dirColor) : HudShapes.scaleAlpha(link.text(dirColor), 0.6));
        double w = CrispText.width(r, text, ts), h = CrispText.height(r, ts);

        if (dirStyle.get() == DirectionStyle.Chips) {
            double cr = Math.max(w, h) / 2 + 3.5 * s;
            if (north && glow.get()) {
                HudShapes.circle(r, lx, ly, cr * 1.7, HudShapes.scaleAlpha(northColor.get(), 0.12 * glowIntensity.get()));
                HudShapes.circle(r, lx, ly, cr * 1.3, HudShapes.scaleAlpha(northColor.get(), 0.18 * glowIntensity.get()));
            }
            HudShapes.circle(r, lx, ly, cr, link.tint(chipColor));
            HudShapes.ring(r, lx, ly, cr, Math.max(1, s), north ? HudShapes.scaleAlpha(northColor.get(), 0.9) : new Color(255, 255, 255, 40));
        } else if (north && glow.get()) {
            HudShapes.circle(r, lx, ly, Math.max(w, h) * 0.9, HudShapes.scaleAlpha(northColor.get(), 0.15 * glowIntensity.get()));
        }

        CrispText.draw(r, text, lx - w / 2, ly - h / 2, letter, dirStyle.get() == DirectionStyle.Letters, ts);
    }

    // ===== You =====

    private void drawSelf(HudRenderer r, boolean inWorld) {
        double a = 6 * s;
        // Rotating radar: you always point up. North-up radar: your arrow turns instead.
        double ang = -Math.PI / 2;
        if (!rotate.get() && inWorld) {
            double yr = Math.toRadians(mc.player.getYaw());
            ang = screenAngle(-Math.sin(yr), Math.cos(yr));
        }

        Color c = link.text(selfColor);
        if (glow.get()) HudShapes.circle(r, cx, cy, a * 1.6, HudShapes.scaleAlpha(c, 0.12 * glowIntensity.get()));

        if (selfIcon.get() == SelfIcon.Dot) {
            HudShapes.circle(r, cx, cy, a * 0.75, new Color(0, 0, 0, 120));
            HudShapes.circle(r, cx, cy, a * 0.6, c);
        } else {
            arrow(r, cx, cy, a + 1.5 * s, ang, new Color(0, 0, 0, 120));
            arrow(r, cx, cy, a, ang, c);
        }
    }

    /** Your field of view as a soft cone, fading out with distance from the middle. */
    private void drawViewCone(HudRenderer r) {
        double yr = Math.toRadians(mc.player.getYaw());
        double facing = rotate.get() ? -Math.PI / 2 : screenAngle(-Math.sin(yr), Math.cos(yr));
        double half = Math.toRadians(mc.options.getFov().getValue() / 2.0);
        Color c = link.accent(coneColor);

        int layers = 6;
        for (int i = 0; i < layers; i++) {
            double rr = radius * 0.55 * (i + 1) / layers;
            double f = 1 - (double) i / layers;
            sector(r, cx, cy, rr, facing - half, facing + half, HudShapes.scaleAlpha(c, f * 0.35));
        }
    }

    // ===== Geometry helpers =====

    /**
     * World offset (dx, dz from you) -> radar position, using the current auto-zoom.
     * Facing direction in the world for yaw: (-sin, cos); your right-hand side: (-cos, -sin).
     */
    private double[] toRadar(double dx, double dz) {
        double forward = -dx * sin + dz * cos;
        double right = -dx * cos - dz * sin;

        double k = radius / Math.max(1, zoom.value);
        double rx = right * k, ry = -forward * k;
        // While the zoom is still gliding out to fit something new, keep it on the edge instead of outside the disc
        double dist = Math.hypot(rx, ry);
        double edge = radius - 6 * s;
        if (dist > edge) {
            rx *= edge / dist;
            ry *= edge / dist;
        }
        return new double[] {cx + rx, cy + ry};
    }

    /** A world direction (dx, dz) as a unit vector on screen. */
    private double[] dirVec(double dx, double dz) {
        double forward = -dx * sin + dz * cos;
        double right = -dx * cos - dz * sin;
        double len = Math.hypot(right, forward);
        if (len < 1e-9) return new double[] {0, -1};
        return new double[] {right / len, -forward / len};
    }

    /** A world direction (dx, dz) as a screen angle (0 = right, clockwise). */
    private double screenAngle(double dx, double dz) {
        double[] v = dirVec(dx, dz);
        return Math.atan2(v[1], v[0]);
    }

    /** Filled pie slice from angle a1 to a2 (radians, clockwise on screen). */
    private static void sector(HudRenderer r, double x, double y, double rad, double a1, double a2, Color c) {
        if (c.a <= 0 || a2 <= a1) return;
        int steps = Math.max(2, (int) Math.ceil((a2 - a1) / Math.toRadians(6)));
        for (int i = 0; i < steps; i++) {
            double b1 = a1 + (a2 - a1) * i / steps, b2 = a1 + (a2 - a1) * (i + 1) / steps;
            HudShapes.tri(r, x, y, x + Math.cos(b1) * rad, y + Math.sin(b1) * rad, x + Math.cos(b2) * rad, y + Math.sin(b2) * rad, c);
        }
    }

    /** Navigation-style arrow pointing at screen angle `ang`, with a notch at the back. */
    private static void arrow(HudRenderer r, double x, double y, double size, double ang, Color c) {
        double fx = Math.cos(ang), fy = Math.sin(ang), rx = -fy, ry = fx;
        double tipX = x + fx * size, tipY = y + fy * size;
        double lX = x - fx * size * 0.7 - rx * size * 0.75, lY = y - fy * size * 0.7 - ry * size * 0.75;
        double rX = x - fx * size * 0.7 + rx * size * 0.75, rY = y - fy * size * 0.7 + ry * size * 0.75;
        double nX = x - fx * size * 0.3, nY = y - fy * size * 0.3;
        HudShapes.tri(r, tipX, tipY, rX, rY, nX, nY, c);
        HudShapes.tri(r, tipX, tipY, nX, nY, lX, lY, c);
    }

    private static void diamond(HudRenderer r, double x, double y, double size, Color c) {
        HudShapes.tri(r, x, y - size, x + size, y, x, y + size, c);
        HudShapes.tri(r, x, y - size, x, y + size, x - size, y, c);
    }
}
