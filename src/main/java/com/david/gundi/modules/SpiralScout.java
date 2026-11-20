package com.david.gundi.modules;

import com.david.gundi.util.Colors;
import com.david.gundi.util.ErrorReporter;
import com.david.gundi.util.ThemeLink;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Flies an endless square spiral outward on ElytraFly (Vanilla), so a growing area gets loaded for base hunting.
 *
 * Flight: the direction is locked to exactly east, south, west or north, so every pass is a straight line.
 * At each corner it snaps 90 degrees and (optionally) stops your speed so it never drifts past the turn.
 * Passes are `path-gap` chunks apart; leg k is (k/2 + 1) × gap long: E, S, W, N, ...
 *
 * On top: it waits for chunks to arrive before pushing on, draws the path, feeds the Spiral Scout HUD,
 * and saves progress to disk (per server and dimension) so after a kick or restart it flies back and continues.
 *
 * ElytraFly is the player's job: turn it on (Vanilla mode) first, then this.
 */
public class SpiralScout extends GundiModule {
    private static final String NAME = "spiral-scout";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_SCAN = 64;         // chunks scanned sideways when measuring
    private static final int MAX_WAIT_TICKS = 100;  // stop waiting for chunks after 5 s
    private static final int AUTOSAVE_TICKS = 1200; // save progress every minute

    public enum CruiseHeight { Current, Fixed }

    // ── General ──
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final ThemeLink link = new ThemeLink(sgGeneral);

    private final Setting<Integer> pathGap = sgGeneral.add(new IntSetting.Builder()
        .name("path-gap")
        .description("Chunks between two parallel passes. Takes effect on the next new spiral. About 2 × the server's render distance covers everything.")
        .defaultValue(12)
        .range(1, 64)
        .sliderRange(2, 32)
        .build()
    );

    private final Setting<Boolean> resume = sgGeneral.add(new BoolSetting.Builder()
        .name("resume")
        .description("Continue the saved spiral for this server and dimension: fly back to where you stopped, then carry on.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderPath = sgGeneral.add(new BoolSetting.Builder()
        .name("render-path")
        .description("Draw the flown legs, the current leg and the next ones in the world.")
        .defaultValue(true)
        .build()
    );

    // ── Flight ──
    private final SettingGroup sgFlight = settings.createGroup("Flight");

    private final Setting<Boolean> waitForChunks = sgFlight.add(new BoolSetting.Builder()
        .name("wait-for-chunks")
        .description("Stop pushing forward until the chunks ahead and beside you have loaded, so nothing gets skipped.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stopAtCorners = sgFlight.add(new BoolSetting.Builder()
        .name("stop-at-corners")
        .description("Stop your speed at every corner so the turn is exact.")
        .defaultValue(true)
        .build()
    );

    private final Setting<CruiseHeight> cruiseMode = sgFlight.add(new EnumSetting.Builder<CruiseHeight>()
        .name("cruise-height")
        .description("Current = the height you're at when you turn it on. Fixed = the height below.")
        .defaultValue(CruiseHeight.Current)
        .build()
    );

    private final Setting<Integer> fixedHeight = sgFlight.add(new IntSetting.Builder()
        .name("height")
        .description("Height to fly at.")
        .defaultValue(250)
        .range(-60, 400)
        .sliderRange(60, 320)
        .visible(() -> cruiseMode.get() == CruiseHeight.Fixed)
        .build()
    );

    // ── Safety ──
    private final SettingGroup sgSafety = settings.createGroup("Safety");

    private final Setting<Double> minHealth = sgSafety.add(new DoubleSetting.Builder()
        .name("min-health")
        .description("Stop when your health drops below this (2 = one heart).")
        .defaultValue(8)
        .range(0, 20)
        .build()
    );

    private final Setting<Integer> maxDistance = sgSafety.add(new IntSetting.Builder()
        .name("max-distance")
        .description("Stop once this far from the spiral's centre, in blocks. 0 = never.")
        .defaultValue(0)
        .range(0, 1_000_000)
        .sliderRange(0, 50_000)
        .build()
    );

    // ── Colors ──
    private final SettingGroup sgColors = settings.createGroup("Colors", false);

    private final Setting<SettingColor> pathColor = sgColors.add(new ColorSetting.Builder()
        .name("path-color")
        .description("Flown legs and the preview of the next legs.")
        .defaultValue(new SettingColor(140, 200, 255, 200))
        .visible(link.own())
        .build()
    );

    private final Setting<SettingColor> targetColor = sgColors.add(new ColorSetting.Builder()
        .name("target-color")
        .description("The current leg and the corner it's flying to.")
        .defaultValue(new SettingColor(191, 90, 242, 255))
        .visible(link.own())
        .build()
    );

    // ── spiral (saved to disk) ──
    private boolean hasSpiral;
    private String spiralWorld;
    private double originX, originZ;
    private int leg;
    private int gapBlocks;
    private final List<double[]> corners = new ArrayList<>(); // corners.get(k) = start of leg k (grown on demand)

    // ── this run ──
    private final LongOpenHashSet seen = new LongOpenHashSet();
    private String stopReason;
    private boolean started;                 // onActivate finished; only then may onDeactivate save
    private int ticksActive, groundTicks, waitTicks;
    private long waitChunk;                  // chunk where the current wait began; a new chunk re-arms waiting
    private boolean seenGliding, waiting, returning;
    private double resumeX, resumeZ;
    private double lastReturnDist;
    private int growTicks;                   // ticks the distance to the resume point kept growing (we passed it)
    private double startY;                   // height when turned on, for cruise-height Current
    private int climbTicks;
    private double climbStartY;
    private boolean climbWarned;
    private int radiusChunks, maxRadiusSeen;
    private double legAlong, legLength;
    private long activeSince;

    public SpiralScout() {
        super(NAME, "Flies an endless square spiral on ElytraFly (Vanilla) to load a big area and find bases. Saves progress.");
    }

    // ═════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═════════════════════════════════════════════════════════════════════

    @Override
    public void onActivate() {
        super.onActivate();
        stopReason = null;
        started = false;
        ticksActive = 0;
        groundTicks = 0;
        waitTicks = 0;
        waitChunk = Long.MIN_VALUE;
        waiting = false;
        returning = false;
        seenGliding = false;
        lastReturnDist = Double.MAX_VALUE;
        growTicks = 0;
        climbTicks = 0;
        climbWarned = false;
        maxRadiusSeen = 0;
        seen.clear();
        activeSince = System.currentTimeMillis();

        try {
            if (mc.player == null || mc.world == null) {
                stopReason = "not in a world";
                return;
            }

            // ElytraFly's yaw lock only applies in Bounce mode, so Vanilla needs nothing changed there.
            ElytraFly ef = Modules.get().get(ElytraFly.class);
            if (ef == null || !ef.isActive() || ef.flightMode.get() != ElytraFlightModes.Vanilla) {
                stopReason = "turn on ElytraFly in Vanilla mode first, then Spiral Scout";
                return;
            }
            if (!wearsElytra()) {
                stopReason = "put on an elytra first";
                return;
            }
            if (ef.verticalSpeed.get() <= 0) {
                warning("ElytraFly's vertical-speed is 0, so Spiral Scout can't climb or descend to the cruise height.");
            }

            String world = worldKey();
            boolean resumed = false;
            if (resume.get()) {
                if (hasSpiral && world.equals(spiralWorld)) {
                    resumed = true;
                } else {
                    LoadResult result = load(world);
                    if (result == LoadResult.DAMAGED) {
                        stopReason = "the saved spiral was damaged, so it was kept as a .bak file. Turn Spiral Scout on again to start a new one";
                        return;
                    }
                    resumed = result == LoadResult.LOADED;
                }
            }
            if (!resumed) newSpiral(world);

            startY = mc.player.getY();

            if (resumed && horizontalDistance(resumeX, resumeZ) > 8) {
                returning = true;
                info("Continuing the saved spiral: flying back to %d, %d (%s away), leg %d.",
                    (int) resumeX, (int) resumeZ, formatDistance(horizontalDistance(resumeX, resumeZ)), leg + 1);
            } else {
                info("Scouting from %d, %d · leg %d · passes %d chunks apart.", (int) originX, (int) originZ, leg + 1, gapBlocks / 16);
            }
            started = true;
        } catch (Exception e) {
            ErrorReporter.report(NAME, "activate", e);
            stopReason = "couldn't start, see the log";
        }
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        try {
            waiting = false;
            // Only a run that really started may move the resume point, and only for its own world.
            if (started && hasSpiral && mc.player != null && mc.world != null && worldKey().equals(spiralWorld)) {
                save(!returning);
            }
        } catch (Exception e) {
            ErrorReporter.report(NAME, "deactivate", e);
        } finally {
            started = false;
            releaseKeys();
        }
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WButton reset = list.add(theme.button("Reset spiral (this server)")).widget();
        reset.action = () -> {
            try {
                if (isActive()) {
                    hasSpiral = false; // so turning off below saves nothing
                    toggle();
                }
                if (mc.world != null) {
                    File f = saveFile(worldKey());
                    if (f.exists() && !f.delete()) warning("Couldn't delete %s.", f.getName());
                }
                hasSpiral = false;
                corners.clear();
                info("Spiral reset and Spiral Scout stopped. The next start begins a new one where you are.");
            } catch (Exception e) {
                ErrorReporter.report(NAME, "reset", e);
            }
        };
        return list;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Events
    // ═════════════════════════════════════════════════════════════════════

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        try {
            seen.add(event.chunk().getPos().toLong());
        } catch (Exception e) {
            ErrorReporter.report(NAME, "chunk", e);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        try {
            if (stopReason != null) {
                stop(stopReason);
                return;
            }
            if (mc.player == null || mc.world == null) {
                stop("left the world");
                return;
            }
            ticksActive++;

            // ── safety ──
            ElytraFly ef = Modules.get().get(ElytraFly.class);
            if (ef == null || !ef.isActive()) { stop("ElytraFly was turned off"); return; }
            if (!wearsElytra()) { stop("no elytra equipped"); return; }
            if (mc.player.isTouchingWater() || mc.player.isInLava()) { stop("touched water or lava"); return; }
            if (mc.player.getHealth() < minHealth.get()) { stop("health is low"); return; }
            if (maxDistance.get() > 0 && horizontalDistance(originX, originZ) > maxDistance.get()) {
                stop("reached the max distance");
                return;
            }
            if (mc.player.isGliding()) seenGliding = true;
            if (!seenGliding && ticksActive > 200) { stop("you never took off"); return; }
            groundTicks = mc.player.isOnGround() ? groundTicks + 1 : 0;
            if (seenGliding && groundTicks > 60) { stop("you landed"); return; }

            if (ticksActive % AUTOSAVE_TICKS == 0) save(!returning);

            radiusChunks = measureRadius();
            maxRadiusSeen = Math.max(maxRadiusSeen, radiusChunks);

            // ── back to where the saved spiral stopped ──
            if (returning) {
                double dist = horizontalDistance(resumeX, resumeZ);
                Vec3d v = mc.player.getVelocity();
                double speed = Math.sqrt(v.x * v.x + v.z * v.z);
                // Close by and getting farther for 2 ticks in a row: we flew past the point.
                if (dist < 64) growTicks = dist > lastReturnDist + 0.05 ? growTicks + 1 : 0;
                lastReturnDist = dist;
                boolean arrived = dist < Math.max(5, speed * 2) || growTicks >= 2;

                if (arrived) {
                    returning = false;
                    if (stopAtCorners.get()) mc.player.setVelocity(0, 0, 0);
                    info("Back on the spiral, continuing leg %d.", leg + 1);
                } else {
                    double dx = resumeX - mc.player.getX(), dz = resumeZ - mc.player.getZ();
                    mc.player.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90));
                    // wait for chunks on the way back too, looking along the main direction of travel
                    int hx = Math.abs(dx) >= Math.abs(dz) ? (int) Math.signum(dx) : 0;
                    int hz = hx == 0 ? (int) Math.signum(dz) : 0;
                    updateWaiting(hx, hz);
                    mc.options.forwardKey.setPressed(!waiting);
                    holdAltitude();
                    return;
                }
            }

            // ── corner reached: snap to the next leg ──
            updateLeg();
            if (legAlong >= legLength) {
                leg++;
                if (stopAtCorners.get()) mc.player.setVelocity(0, 0, 0);
                ErrorReporter.step(NAME, "leg " + (leg + 1) + ", " + seen.size() + " chunks loaded this run");
                updateLeg();
            }

            // ── wait for chunks so nothing gets skipped ──
            updateWaiting(dirX(leg), dirZ(leg));

            // ── steer: locked to the leg's direction, with a small pull back onto the line ──
            float yaw = legYaw(leg);
            double[] start = corner(leg);
            double off = dirX(leg) != 0 ? mc.player.getZ() - start[1] : mc.player.getX() - start[0];
            // which way "left" is for this leg, so the pull goes toward the line
            double side = switch (leg % 4) { case 0 -> -off; case 1 -> off; case 2 -> off; default -> -off; };
            float pull = (float) MathHelper.clamp(side * 2.0, -12, 12);
            if (Math.abs(off) < 0.3) pull = 0;
            mc.player.setYaw(yaw + pull);
            mc.options.forwardKey.setPressed(!waiting);
            holdAltitude();
        } catch (Exception e) {
            ErrorReporter.report(NAME, "tick", e);
        }
    }

    @EventHandler
    private void onRender3d(Render3DEvent event) {
        try {
            if (!renderPath.get() || mc.player == null || !hasSpiralHere()) return;
            double y = cruiseY();

            Color target = link.accent(targetColor);
            Color flown = Colors.alpha(link.dim(pathColor), 0.45);
            Color preview = Colors.alpha(link.accent2(pathColor), 0.35);

            for (int k = 0; k < leg; k++) {
                double[] a = corner(k), b = corner(k + 1);
                event.renderer.line(a[0], y, a[1], b[0], y, b[1], flown);
            }

            double[] s = corner(leg), e = corner(leg + 1);
            event.renderer.line(s[0], y, s[1], e[0], y, e[1], target);
            event.renderer.box(e[0] - 1.5, y - 1.5, e[1] - 1.5, e[0] + 1.5, y + 1.5, e[1] + 1.5,
                Colors.alpha(target, 0.25), target, ShapeMode.Both, 0);

            for (int k = leg + 1; k <= leg + 2; k++) {
                double[] a = corner(k), b = corner(k + 1);
                event.renderer.line(a[0], y, a[1], b[0], y, b[1], preview);
            }

            if (returning) event.renderer.line(mc.player.getX(), y, mc.player.getZ(), resumeX, y, resumeZ, target);
        } catch (Exception e) {
            ErrorReporter.report(NAME, "render", e);
        }
    }

    @Override
    public String getInfoString() {
        try {
            if (!hasSpiralHere()) return null;
            String state = returning ? " · returning" : waiting ? " · loading…" : "";
            return String.format("leg %d · %s%s", leg + 1, formatDistance(distanceOut()), state);
        } catch (Exception e) {
            ErrorReporter.report(NAME, "info", e);
            return null;
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // Spiral geometry: legs E, S, W, N; leg k is (k/2 + 1) × gap long
    // ═════════════════════════════════════════════════════════════════════

    private void newSpiral(String world) {
        originX = mc.player.getBlockX() + 0.5;
        originZ = mc.player.getBlockZ() + 0.5;
        gapBlocks = pathGap.get() * 16;
        leg = 0;
        corners.clear();
        hasSpiral = true;
        spiralWorld = world;
        save(true);
        ErrorReporter.step(NAME, "new spiral at " + (int) originX + ", " + (int) originZ + ", gap " + pathGap.get() + " chunks");
    }

    /** Start of leg k (corners are built on demand from the centre). */
    private double[] corner(int k) {
        if (corners.isEmpty()) corners.add(new double[]{originX, originZ});
        while (corners.size() <= k) {
            int i = corners.size() - 1;
            double[] c = corners.get(i);
            double len = legLengthOf(i);
            corners.add(new double[]{c[0] + dirX(i) * len, c[1] + dirZ(i) * len});
        }
        return corners.get(k);
    }

    private double legLengthOf(int k) {
        return (k / 2 + 1) * (double) gapBlocks;
    }

    private void updateLeg() {
        double[] start = corner(leg);
        legLength = legLengthOf(leg);
        legAlong = (mc.player.getX() - start[0]) * dirX(leg) + (mc.player.getZ() - start[1]) * dirZ(leg);
    }

    /** Minecraft yaw for each direction: east -90, south 0, west 90, north 180. */
    private static float legYaw(int k) {
        return switch (k % 4) { case 0 -> -90f; case 1 -> 0f; case 2 -> 90f; default -> 180f; };
    }

    private static int dirX(int k) {
        return switch (k % 4) { case 0 -> 1; case 2 -> -1; default -> 0; };
    }

    private static int dirZ(int k) {
        return switch (k % 4) { case 1 -> 1; case 3 -> -1; default -> 0; };
    }

    // ═════════════════════════════════════════════════════════════════════
    // Saving
    // ═════════════════════════════════════════════════════════════════════

    private static class SaveData {
        String world;
        double originX, originZ;
        int leg;
        int gapBlocks;
        double resumeX, resumeZ;
    }

    private File saveFile(String world) {
        String name = world.replaceAll("[^a-zA-Z0-9._-]", "_");
        return new File(new File(new File(MeteorClient.FOLDER, "gundi"), "spiral-scout"), name + ".json");
    }

    /** Writes the spiral; `atPlayer` = resume from where you are now (else keep the old resume point). */
    private void save(boolean atPlayer) {
        try {
            if (!hasSpiral || spiralWorld == null) return;
            if (atPlayer && mc.player != null) {
                resumeX = mc.player.getX();
                resumeZ = mc.player.getZ();
            }
            SaveData d = new SaveData();
            d.world = spiralWorld;
            d.originX = originX;
            d.originZ = originZ;
            d.leg = leg;
            d.gapBlocks = gapBlocks;
            d.resumeX = resumeX;
            d.resumeZ = resumeZ;
            File f = saveFile(spiralWorld);
            f.getParentFile().mkdirs();
            // Write a temp file and move it over the old one, so a crash mid-write never leaves a broken save.
            File tmp = new File(f.getPath() + ".tmp");
            try (Writer w = new FileWriter(tmp)) {
                GSON.toJson(d, w);
            }
            try {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            ErrorReporter.report(NAME, "save", e);
        }
    }

    private enum LoadResult { NONE, LOADED, DAMAGED }

    private LoadResult load(String world) {
        File f = saveFile(world);
        if (!f.exists()) return LoadResult.NONE;
        SaveData d;
        try (Reader r = new FileReader(f)) {
            d = GSON.fromJson(r, SaveData.class);
        } catch (Exception e) {
            ErrorReporter.report(NAME, "load", e);
            keepBackup(f);
            return LoadResult.DAMAGED;
        }
        if (d == null || d.gapBlocks <= 0) {
            keepBackup(f);
            return LoadResult.DAMAGED;
        }
        originX = d.originX;
        originZ = d.originZ;
        leg = Math.max(0, d.leg);
        gapBlocks = d.gapBlocks;
        resumeX = d.resumeX;
        resumeZ = d.resumeZ;
        corners.clear();
        hasSpiral = true;
        spiralWorld = world;
        ErrorReporter.step(NAME, "loaded saved spiral, leg " + (leg + 1));
        return LoadResult.LOADED;
    }

    /** Moves a save that can't be read out of the way, so it's never overwritten and can be recovered by hand. */
    private void keepBackup(File f) {
        File bak = new File(f.getPath() + "." + System.currentTimeMillis() + ".bak");
        if (f.renameTo(bak)) ErrorReporter.step(NAME, "damaged save kept as " + bak.getName());
        else warning("Couldn't move the damaged save %s aside.", f.getName());
    }

    // ═════════════════════════════════════════════════════════════════════
    // Chunks
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Sets `waiting` while chunks ahead (heading hx, hz) or beside you are missing. Each wait lasts at most
     * MAX_WAIT_TICKS; moving into a new chunk re-arms it. A timeout lowers the expected radius to what the
     * server actually sends, so a slow server doesn't cause a 5 s stop in every chunk.
     */
    private void updateWaiting(int hx, int hz) {
        long here = mc.player.getChunkPos().toLong();
        if (here != waitChunk) {
            waitChunk = here;
            waitTicks = 0;
        }
        int expected = Math.min(mc.options.getClampedViewDistance(), Math.max(1, maxRadiusSeen));
        boolean missing = waitForChunks.get() && (!aheadLoaded(hx, hz) || radiusChunks < expected - 1);
        if (missing && waitTicks < MAX_WAIT_TICKS) {
            waiting = true;
            waitTicks++;
            if (waitTicks == MAX_WAIT_TICKS) maxRadiusSeen = Math.max(1, radiusChunks + 1);
        } else {
            waiting = false;
            if (!missing) waitTicks = 0;
        }
    }

    /** The chunk two ahead of you has loaded (flying into unloaded chunks gets you pulled back on many servers). */
    private boolean aheadLoaded(int hx, int hz) {
        int cx = mc.player.getChunkPos().x + hx * 2, cz = mc.player.getChunkPos().z + hz * 2;
        return mc.world.getChunkManager().isChunkLoaded(cx, cz);
    }

    /** Chunks loaded on both sides of the path (perpendicular to the current leg), the smaller side. */
    private int measureRadius() {
        int pcx = mc.player.getChunkPos().x, pcz = mc.player.getChunkPos().z;
        int px = dirZ(leg) != 0 ? 1 : 0, pz = dirX(leg) != 0 ? 1 : 0;
        return Math.min(scan(pcx, pcz, px, pz), scan(pcx, pcz, -px, -pz));
    }

    private int scan(int cx, int cz, int sx, int sz) {
        int k = 0;
        while (k < MAX_SCAN && mc.world.getChunkManager().isChunkLoaded(cx + sx * (k + 1), cz + sz * (k + 1))) k++;
        return k;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Status for the HUD
    // ═════════════════════════════════════════════════════════════════════

    public boolean hasSpiral() { return hasSpiral; }

    /** A spiral is loaded and it belongs to the server and dimension you're in now. */
    public boolean hasSpiralHere() {
        return hasSpiral && spiralWorld != null && mc.world != null && spiralWorld.equals(worldKey());
    }
    public boolean isWaiting() { return isActive() && waiting; }
    public boolean isReturning() { return isActive() && returning; }
    public int legNumber() { return leg + 1; }
    public int ring() { return leg / 4 + 1; }
    public int radius() { return radiusChunks; }
    public int gapChunks() { return gapBlocks / 16; }
    public int chunksSeen() { return seen.size(); }
    public double originX() { return originX; }
    public double originZ() { return originZ; }
    public double resumeX() { return resumeX; }
    public double resumeZ() { return resumeZ; }
    public long activeMillis() { return isActive() ? System.currentTimeMillis() - activeSince : 0; }

    /** Start of leg k, for the map. */
    public double[] cornerAt(int k) { return corner(k).clone(); }

    public double legProgress() {
        return legLength <= 0 ? 0 : MathHelper.clamp(legAlong / legLength, 0, 1);
    }

    public double toCorner() {
        return Math.max(0, legLength - legAlong);
    }

    /** How far out the spiral reaches (half the side of the square it fills). */
    public double distanceOut() {
        double[] e = corner(leg + 1);
        return Math.max(Math.abs(e[0] - originX), Math.abs(e[1] - originZ));
    }

    /** Area inside the finished rings, in km². */
    public double coveredKm2() {
        double[] s = corner(leg);
        double half = Math.max(Math.abs(s[0] - originX), Math.abs(s[1] - originZ)) + gapBlocks / 2.0;
        return (2 * half) * (2 * half) / 1_000_000;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════

    /** The height to fly at, read live so changing the setting mid-flight takes effect. */
    private double cruiseY() {
        return cruiseMode.get() == CruiseHeight.Fixed ? fixedHeight.get() : startY;
    }

    /** Jump = up, sneak = down: ElytraFly (Vanilla) adds vertical-speed for each. */
    private void holdAltitude() {
        double y = mc.player.getY(), target = cruiseY();
        boolean up = y < target - 2;
        mc.options.jumpKey.setPressed(up);
        mc.options.sneakKey.setPressed(y > target + 2);

        // Holding "up" for 5 s without rising a block means something else is stopping the climb.
        if (up && mc.player.isGliding()) {
            if (climbTicks == 0) climbStartY = y;
            if (++climbTicks >= 100) {
                if (y - climbStartY < 1 && !climbWarned) {
                    climbWarned = true;
                    warning("Can't climb to %d: check that ElytraFly's vertical-speed isn't 0, or the server may be pulling you back.", (int) target);
                    ErrorReporter.step(NAME, "climb stalled at y " + (int) y + ", target " + (int) target);
                }
                climbTicks = 0;
            }
        } else {
            climbTicks = 0;
        }
    }

    private String worldKey() {
        ServerInfo server = mc.getCurrentServerEntry();
        String where;
        if (server != null) where = server.address;
        else if (mc.getServer() != null) where = "sp-" + mc.getServer().getSaveProperties().getLevelName();
        else where = "unknown";
        return where + "|" + mc.world.getRegistryKey().getValue();
    }

    private boolean wearsElytra() {
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return chest.contains(DataComponentTypes.GLIDER);
    }

    private double horizontalDistance(double x, double z) {
        double dx = x - mc.player.getX(), dz = z - mc.player.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static String formatDistance(double blocks) {
        return blocks >= 1000 ? String.format("%.1f km", blocks / 1000) : String.format("%d m", (int) blocks);
    }

    private void stop(String reason) {
        if (!isActive()) return;
        info("Stopped: %s.", reason);
        ErrorReporter.step(NAME, "stopped: " + reason);
        toggle();
    }

    /** Give the movement keys back to the keyboard: pressed only if you're physically holding them. */
    private void releaseKeys() {
        for (KeyBinding key : new KeyBinding[]{mc.options.forwardKey, mc.options.jumpKey, mc.options.sneakKey}) {
            key.setPressed(Input.isPressed(key));
        }
    }
}
