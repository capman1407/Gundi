package com.david.gundi.modules;

import com.david.gundi.Gundi;
import com.david.gundi.gui.Spring;
import com.david.gundi.util.ErrorReporter;
import com.david.gundi.util.HudShapes;
import com.david.gundi.util.Shapes2D;
import com.david.gundi.util.ThemeLink;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.NameProtect;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import org.joml.Vector3d;

import java.util.*;

/**
 * Apple-style nametags: a frosted glass card above each player with
 *   [head]  Name                 (● FRIEND for friends)
 *           18.5 HP · 24m · 42ms
 *   ███████████░░░░░  health bar (gold part = absorption)
 *   [off][helmet][chest][legs][boots][main]   with durability bars and enchant tags under each slot
 *
 * Plus smaller glass tags for other entities (dropped items, TNT fuse, mobs) from the entities list.
 * Several options follow Meteor's own Nametags module (entities list, shown enchantments, ignore friends,
 * ignore self, culling, skip empty slots).
 */
public class AppleNametags extends GundiModule {
    private static final String NAME = "apple-nametags";

    /** Read by NametagMixin to hide Minecraft's own name above players while this is on. */
    public static AppleNametags INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    // Follow the selected GUI theme (card color, text colors, corners). See util/ThemeLink.
    private final ThemeLink link = new ThemeLink(sgGeneral);
    private final SettingGroup sgFilter = settings.createGroup("Filter");
    private final SettingGroup sgShow = settings.createGroup("Show");
    private final SettingGroup sgItems = settings.createGroup("Gear");
    private final SettingGroup sgLook = settings.createGroup("Colors", false);
    private final SettingGroup sgCulling = settings.createGroup("Culling", false);

    // General

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Which entities get a nametag.")
        .defaultValue(EntityType.PLAYER, EntityType.ITEM)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Size of the nametags.")
        .defaultValue(1)
        .range(0.3, 3)
        .build()
    );

    private final Setting<Boolean> hideVanilla = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-vanilla-names")
        .description("Hide Minecraft's own name text above players (it would overlap the card).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showInvisible = sgFilter.add(new BoolSetting.Builder()
        .name("show-invisible")
        .description("Also show nametags for invisible players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showSelf = sgFilter.add(new BoolSetting.Builder()
        .name("show-self")
        .description("Show your own tag in third person (F5) or Freecam.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Don't show tags for friends.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> culling = sgCulling.add(new BoolSetting.Builder()
        .name("culling")
        .description("Limit how many tags show and how far away. Off = no limit (shows everything the game knows about).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> cullRange = sgCulling.add(new DoubleSetting.Builder()
        .name("culling-range")
        .description("Only show tags within this many blocks of the camera.")
        .defaultValue(20)
        .min(0)
        .sliderMax(200)
        .visible(culling::get)
        .build()
    );

    private final Setting<Integer> cullCount = sgCulling.add(new IntSetting.Builder()
        .name("culling-count")
        .description("Only show this many tags (the closest ones).")
        .defaultValue(50)
        .min(1)
        .sliderRange(1, 100)
        .visible(culling::get)
        .build()
    );

    // Show

    private final Setting<Boolean> head = sgShow.add(new BoolSetting.Builder().name("head").description("Player head.").defaultValue(true).build());
    private final Setting<Boolean> health = sgShow.add(new BoolSetting.Builder().name("health").description("Health text and bar.").defaultValue(true).build());
    private final Setting<Boolean> distance = sgShow.add(new BoolSetting.Builder().name("distance").description("Distance to the player.").defaultValue(true).build());
    private final Setting<Boolean> ping = sgShow.add(new BoolSetting.Builder().name("ping").description("The player's ping.").defaultValue(true).build());

    // Items

    private final Setting<Boolean> items = sgItems.add(new BoolSetting.Builder().name("items").description("Armor and held items.").defaultValue(true).build());

    private final Setting<Boolean> skipEmpty = sgItems.add(new BoolSetting.Builder()
        .name("skip-empty-slots")
        .description("Don't leave empty boxes for missing armor or empty hands.")
        .defaultValue(true)
        .visible(items::get)
        .build()
    );

    private final Setting<Boolean> stackCount = sgItems.add(new BoolSetting.Builder()
        .name("stack-count")
        .description("Show how many items are in a stack (e.g. 64 crystals, 3 totems).")
        .defaultValue(true)
        .visible(items::get)
        .build()
    );

    private final Setting<Boolean> enchants = sgItems.add(new BoolSetting.Builder()
        .name("enchants")
        .description("Short enchant tags under the items (P4, S5...). M1 = Mending.")
        .defaultValue(true)
        .visible(items::get)
        .build()
    );

    private final Setting<Set<RegistryKey<Enchantment>>> shownEnchants = sgItems.add(new EnchantmentListSetting.Builder()
        .name("shown-enchantments")
        .description("Which enchantments get a tag. Mending always shows as M1.")
        .defaultValue(
            Enchantments.PROTECTION,
            Enchantments.BLAST_PROTECTION,
            Enchantments.SHARPNESS,
            Enchantments.POWER,
            Enchantments.DENSITY,
            Enchantments.BREACH
        )
        .visible(() -> items.get() && enchants.get())
        .build()
    );

    // Look

    private final Setting<SettingColor> tint = sgLook.add(new ColorSetting.Builder()
        .name("glass-tint")
        .description("Color of the glass card.")
        .defaultValue(new SettingColor(22, 22, 24, 200))
        .visible(link.own())
        .build()
    );

    private final Setting<SettingColor> friendColor = sgLook.add(new ColorSetting.Builder()
        .name("friend-color")
        .description("Name and badge color for friends.")
        .defaultValue(new SettingColor(100, 210, 255))
        .build()
    );

    // Colors
    private static final Color TEXT = new Color(245, 245, 247);
    private static final Color DIM = new Color(152, 152, 157);
    private static final Color HEART = new Color(255, 69, 58);
    private static final Color GOLD = new Color(255, 214, 10);
    private static final Color GREEN = new Color(48, 209, 88);
    private static final Color CURSE = new Color(255, 69, 58);
    private static final Color TRACK = new Color(255, 255, 255, 30);
    private static final Color RIM = new Color(255, 255, 255, 70);

    // Item slot box size and the item icon size inside it (local nametag units)
    private static final double SLOT = 26, ITEM_SIZE = 20;

    // Text sizes (1.0 = Meteor's nametag text). Kept close to Meteor's sizes: much smaller text gets blurry
    // when the tag shrinks with distance. Enchant tags use Meteor's own enchant size.
    private static final double NAME_SIZE = 1.0, INFO_SIZE = 0.9, BADGE_SIZE = 0.75, ENCHANT_SIZE = 0.5;

    // Short enchant names, in the order they're listed under an item. Others use their first 3 letters.
    private static final Map<RegistryKey<Enchantment>, String> SHORT = new LinkedHashMap<>();
    static {
        SHORT.put(Enchantments.PROTECTION, "P");
        SHORT.put(Enchantments.BLAST_PROTECTION, "BP");
        SHORT.put(Enchantments.FIRE_PROTECTION, "FP");
        SHORT.put(Enchantments.PROJECTILE_PROTECTION, "PP");
        SHORT.put(Enchantments.SHARPNESS, "S");
        SHORT.put(Enchantments.SMITE, "Sm");
        SHORT.put(Enchantments.BANE_OF_ARTHROPODS, "BA");
        SHORT.put(Enchantments.POWER, "Pw");
        SHORT.put(Enchantments.DENSITY, "D");
        SHORT.put(Enchantments.BREACH, "Br");
        SHORT.put(Enchantments.WIND_BURST, "WB");
        SHORT.put(Enchantments.FIRE_ASPECT, "FA");
        SHORT.put(Enchantments.KNOCKBACK, "K");
        SHORT.put(Enchantments.LOOTING, "L");
        SHORT.put(Enchantments.SWEEPING_EDGE, "SE");
        SHORT.put(Enchantments.EFFICIENCY, "E");
        SHORT.put(Enchantments.FORTUNE, "F");
        SHORT.put(Enchantments.SILK_TOUCH, "ST");
        SHORT.put(Enchantments.UNBREAKING, "U");
        SHORT.put(Enchantments.THORNS, "T");
        SHORT.put(Enchantments.FEATHER_FALLING, "FF");
        SHORT.put(Enchantments.DEPTH_STRIDER, "DS");
        SHORT.put(Enchantments.FROST_WALKER, "FW");
        SHORT.put(Enchantments.SOUL_SPEED, "SS");
        SHORT.put(Enchantments.SWIFT_SNEAK, "SwS");
        SHORT.put(Enchantments.RESPIRATION, "R");
        SHORT.put(Enchantments.AQUA_AFFINITY, "AA");
        SHORT.put(Enchantments.PUNCH, "Pu");
        SHORT.put(Enchantments.FLAME, "Fl");
        SHORT.put(Enchantments.INFINITY, "Inf");
        SHORT.put(Enchantments.MULTISHOT, "MS");
        SHORT.put(Enchantments.QUICK_CHARGE, "QC");
        SHORT.put(Enchantments.PIERCING, "Pi");
        SHORT.put(Enchantments.LOYALTY, "Lo");
        SHORT.put(Enchantments.IMPALING, "Im");
        SHORT.put(Enchantments.RIPTIDE, "Ri");
        SHORT.put(Enchantments.CHANNELING, "Ch");
        SHORT.put(Enchantments.LUCK_OF_THE_SEA, "LS");
        SHORT.put(Enchantments.LURE, "Lu");
        SHORT.put(Enchantments.LUNGE, "Lg");
        SHORT.put(Enchantments.BINDING_CURSE, "CB");
        SHORT.put(Enchantments.VANISHING_CURSE, "CV");
    }

    private record Tag(String text, Color color) {}

    // Smooth health bar per entity; entries for entities we no longer see are dropped
    private final Map<UUID, Spring> healthSprings = new HashMap<>();
    private final Vector3d pos = new Vector3d();

    public AppleNametags() {
        super("apple-nametags", "Apple-style nametags with head, health, gear and enchants.");
        INSTANCE = this;
    }

    // Players that got a card last frame. Only their vanilla name is hidden, so players we skip
    // (friends with ignore-friends, culled, invisible...) still have Minecraft's own name.
    private Set<Integer> tagged = Set.of();

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        healthSprings.clear();
        tagged = Set.of();
    }

    /** Used by the mixin: should Minecraft's own name above this entity be hidden? */
    public static boolean hidesVanillaName(Entity entity) {
        AppleNametags m = INSTANCE;
        return m != null && m.isActive() && m.hideVanilla.get() && entity instanceof PlayerEntity && m.tagged.contains(entity.getId());
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        try {
            var theme = link.theme();
            Shapes2D.corners = theme != null ? theme.corners.get() : com.david.gundi.gui.ThemeStyles.CornerStyle.Round;
            if (mc.player == null || mc.world == null) {
                tagged = Set.of();
                return;
            }

            List<Entity> list = collect();
            Set<UUID> seen = new HashSet<>();
            Set<Integer> players = new HashSet<>();
            double dt = Math.min(Math.max(event.frameTime, 0), 0.1);

            for (Entity e : list) {
                seen.add(e.getUuid());
                if (e instanceof PlayerEntity && e != mc.player) players.add(e.getId());
                try {
                    Utils.set(pos, e, event.tickDelta);
                    pos.add(0, anchorHeight(e), 0);
                    if (!NametagUtils.to2D(pos, scale.get())) continue; // behind you / off screen

                    NametagUtils.begin(pos, event.drawContext);
                    try {
                        if (e instanceof PlayerEntity p) drawPlayerTag(event.drawContext, p, dt);
                        else drawOtherTag(e, dt);
                    } finally {
                        NametagUtils.end(event.drawContext);
                    }
                } catch (Exception ex) {
                    ErrorReporter.report(NAME, "draw tag", ex);
                }
            }

            healthSprings.keySet().retainAll(seen);
            tagged = players;
        } catch (Exception e) {
            ErrorReporter.report(NAME, "render", e);
        }
    }

    /** Entities that get a tag, furthest first (so closer tags are drawn on top). */
    private List<Entity> collect() {
        boolean selfVisible = !mc.options.getPerspective().isFirstPerson() || Modules.get().isActive(Freecam.class);

        List<Entity> list = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (!entities.get().contains(e.getType())) continue;

            if (e instanceof PlayerEntity p) {
                if (p == mc.player && !(showSelf.get() && selfVisible)) continue;
                if (p.isDead()) continue;
                if (!showInvisible.get() && p.isInvisible()) continue;
                if (ignoreFriends.get() && Friends.get().isFriend(p)) continue;
            }

            // Culling (a limit, off by default): only within range of the camera
            if (culling.get() && !PlayerUtils.isWithinCamera(e, cullRange.get())) continue;
            list.add(e);
        }

        // Closest first, so culling keeps the closest ones...
        list.sort(Comparator.comparingDouble(PlayerUtils::distanceToCamera));
        if (culling.get() && list.size() > cullCount.get()) list = new ArrayList<>(list.subList(0, cullCount.get()));
        // ...then furthest first for drawing
        Collections.reverse(list);
        return list;
    }

    /** How high above the entity's feet the tag sits (same as Meteor's nametags, a bit higher for players). */
    private static double anchorHeight(Entity e) {
        double h = e.getEyeHeight(e.getPose());
        if (e instanceof PlayerEntity) return h + 0.6;
        if (e.getType() == EntityType.ITEM || e.getType() == EntityType.ITEM_FRAME || e.getType() == EntityType.GLOW_ITEM_FRAME) return h + 0.2;
        return h + 0.5;
    }

    // ----- Player card -----

    /** Draws one player card. Coordinates are local: (0, 0) is the point above the head, the card sits above it. */
    private void drawPlayerTag(DrawContext dc, PlayerEntity p, double dt) {
        TextRenderer t = TextRenderer.get();
        boolean shadow = Config.get().customFont.get();
        boolean friend = Friends.get().isFriend(p);
        boolean self = p == mc.player;

        // ----- Content -----
        String name = p.getName().getString();
        if (self) name = Modules.get().get(NameProtect.class).getName(name);
        float hp = p.getHealth(), absorb = p.getAbsorptionAmount(), maxHp = Math.max(1, p.getMaxHealth());

        List<String> infoParts = new ArrayList<>();
        if (health.get()) infoParts.add(String.format("%.1f HP", hp + absorb)); // no heart symbol: Meteor's font doesn't have it
        if (distance.get() && !self) infoParts.add(String.format("%.0fm", PlayerUtils.distanceToCamera(p)));
        if (ping.get()) infoParts.add(EntityUtils.getPing(p) + "ms");
        String info = String.join("  ·  ", infoParts);

        ItemStack[] allGear = {
            p.getOffHandStack(),
            p.getEquippedStack(EquipmentSlot.HEAD),
            p.getEquippedStack(EquipmentSlot.CHEST),
            p.getEquippedStack(EquipmentSlot.LEGS),
            p.getEquippedStack(EquipmentSlot.FEET),
            p.getMainHandStack()
        };
        List<ItemStack> gearList = new ArrayList<>();
        for (ItemStack s : allGear) if (!skipEmpty.get() || !s.isEmpty()) gearList.add(s);
        ItemStack[] gear = gearList.toArray(new ItemStack[0]);

        boolean anyItem = false;
        for (ItemStack s : gear) if (!s.isEmpty()) anyItem = true;
        boolean showItems = items.get() && anyItem;

        String badge = "FRIEND";
        Color badgeColor = friendColor.get();
        boolean showBadge = friend;

        // ----- Measure -----
        double pad = 8, gap = 6;
        double headSize = head.get() ? 30 : 0;
        double nameW = textW(t, name, NAME_SIZE, shadow), nameH = textH(t, NAME_SIZE, shadow);
        double infoW = info.isEmpty() ? 0 : textW(t, info, INFO_SIZE, shadow), infoH = info.isEmpty() ? 0 : textH(t, INFO_SIZE, shadow);
        double badgeW = showBadge ? textW(t, badge, BADGE_SIZE, false) : 0, badgeH = showBadge ? textH(t, BADGE_SIZE, false) : 0;
        double pillPadX = 6, pillW = showBadge ? badgeW + 8 + pillPadX * 2 : 0, pillH = showBadge ? badgeH + 4 : 0;

        double textBlockW = Math.max(nameW + (showBadge ? gap + pillW : 0), infoW);
        double rowH = Math.max(headSize, nameH + (infoH > 0 ? 2 + infoH : 0));
        double topRowW = headSize + (headSize > 0 ? gap : 0) + textBlockW;

        double slot = SLOT, slotGap = 3;
        double itemsW = showItems ? gear.length * slot + (gear.length - 1) * slotGap : 0;
        double barH = health.get() ? 5 : 0;

        // Enchant tags sit UNDER the slots: on top of a slot, Minecraft draws the item icon over them later.
        // One line per shown enchant, then "M1" if the item has Mending.
        List<List<Tag>> tags = new ArrayList<>();
        int maxLines = 0;
        for (ItemStack s : gear) {
            List<Tag> lines = showItems && enchants.get() ? tagsFor(s) : List.of();
            tags.add(lines);
            maxLines = Math.max(maxLines, lines.size());
        }
        double tagLineH = textH(t, ENCHANT_SIZE, true);
        double tagH = maxLines > 0 ? maxLines * tagLineH + 2 : 0;

        double innerW = Math.max(topRowW, itemsW);
        double w = innerW + pad * 2;
        double h = pad + rowH + (barH > 0 ? gap + barH : 0) + (showItems ? gap + slot + 4 + tagH : 0) + pad;
        double x = -w / 2, y = -h;

        // ----- Background: glass card -----
        Renderer2D.COLOR.begin();
        double radius = cardRadius(12);
        glass(x, y, w, h, radius, friend ? HudShapes.scaleAlpha(badgeColor, 0.9) : RIM); // blue glow for friends

        double cy = y + pad;
        double textX = x + pad + headSize + (headSize > 0 ? gap : 0);

        // Head frame
        if (headSize > 0) Shapes2D.rect(x + pad - 1, cy + (rowH - headSize) / 2 - 1, headSize + 2, headSize + 2, 5, new Color(255, 255, 255, 40));

        // Friend pill
        double pillX = textX + nameW + gap, pillY = cy + (nameH - pillH) / 2;
        if (showBadge) {
            Shapes2D.rect(pillX, pillY, pillW, pillH, pillH / 2, HudShapes.scaleAlpha(badgeColor, 0.22));
            Shapes2D.circle(pillX + pillPadX + 2, pillY + pillH / 2, 2.5, badgeColor);
        }

        // Health bar (smooth), gold for absorption on top
        double barY = cy + rowH + gap;
        if (barH > 0) healthBar(p.getUuid(), x + pad, barY, innerW, barH, hp, absorb, maxHp, dt);

        // Item slots + durability bars
        double itemsY = (barH > 0 ? barY + barH : cy + rowH) + gap;
        double itemsX = x + pad + (innerW - itemsW) / 2;
        if (showItems) {
            for (int i = 0; i < gear.length; i++) {
                double sx = itemsX + i * (slot + slotGap);
                Shapes2D.rect(sx, itemsY, slot, slot, 6, new Color(255, 255, 255, 14));
                ItemStack s = gear[i];
                if (!s.isEmpty() && s.isDamageable() && s.getMaxDamage() > 0) {
                    double df = 1 - (double) s.getDamage() / s.getMaxDamage();
                    Shapes2D.rect(sx + 3, itemsY + slot + 1, slot - 6, 2, 1, TRACK);
                    Shapes2D.rect(sx + 3, itemsY + slot + 1, (slot - 6) * df, 2, 1, fillColor(df));
                }
            }
        }
        Renderer2D.COLOR.render();

        // ----- Text -----
        Color nameColor = friend ? friendColor.get() : link.text(TEXT);
        double nameY = cy + (infoH > 0 ? (rowH - nameH - 2 - infoH) / 2 : (rowH - nameH) / 2);
        drawText(t, name, textX, nameY, nameColor, NAME_SIZE, shadow);
        if (infoH > 0) drawText(t, info, textX, nameY + nameH + 2, link.dim(DIM), INFO_SIZE, shadow);
        if (showBadge) drawText(t, badge, pillX + pillPadX + 8, pillY + (pillH - badgeH) / 2, badgeColor, BADGE_SIZE, false);

        for (int i = 0; i < gear.length; i++) {
            double sx = itemsX + i * (slot + slotGap);
            double ty = itemsY + slot + 4; // centered under the slot, below the durability bar
            for (Tag tag : tags.get(i)) {
                double tw = textW(t, tag.text, ENCHANT_SIZE, true);
                drawText(t, tag.text, sx + (slot - tw) / 2, ty, tag.color, ENCHANT_SIZE, true);
                ty += tagLineH;
            }
        }

        // ----- Head and items (drawn by Minecraft's GUI renderer, on top of the card) -----
        if (headSize > 0 && p instanceof AbstractClientPlayerEntity cp) {
            PlayerSkinDrawer.draw(dc, cp.getSkin(), (int) Math.round(x + pad), (int) Math.round(cy + (rowH - headSize) / 2), (int) headSize);
        }
        if (showItems) {
            float itemScale = (float) (ITEM_SIZE / 16.0);
            for (int i = 0; i < gear.length; i++) {
                ItemStack s = gear[i];
                if (s.isEmpty()) continue;
                double sx = itemsX + i * (slot + slotGap);
                // Minecraft's own count overlay, only for real stacks (on tools it would add a second durability bar)
                boolean overlay = stackCount.get() && s.getCount() > 1;
                // Last argument false: NametagUtils already undid the GUI scale. The short drawItem(...) version
                // undoes it AGAIN, which made the items tiny and pushed them out of their boxes.
                RenderUtils.drawItem(dc, s,
                    (int) Math.round(sx + (slot - ITEM_SIZE) / 2), (int) Math.round(itemsY + (slot - ITEM_SIZE) / 2),
                    itemScale, overlay, null, false);
            }
        }
    }

    // ----- Other entities: dropped items, item frames, TNT, mobs -----

    /** A smaller glass tag: one text line, plus a health bar for mobs. */
    private void drawOtherTag(Entity e, double dt) {
        TextRenderer t = TextRenderer.get();
        boolean shadow = Config.get().customFont.get();

        String text;
        Color color = link.text(TEXT);
        LivingEntity living = null;

        if (e instanceof ItemEntity item) {
            ItemStack s = item.getStack();
            if (s.isEmpty()) return;
            text = Names.get(s) + (s.getCount() > 1 ? "  x" + s.getCount() : "");
        } else if (e instanceof ItemFrameEntity frame) {
            ItemStack s = frame.getHeldItemStack();
            if (s.isEmpty()) return;
            text = Names.get(s) + (s.getCount() > 1 ? "  x" + s.getCount() : "");
        } else if (e instanceof TntEntity tnt) {
            text = fuse(tnt.getFuse());
            color = HEART;
        } else if (e instanceof TntMinecartEntity cart) {
            if (!cart.isPrimed()) return;
            text = fuse(cart.getFuseTicks());
            color = HEART;
        } else if (e instanceof LivingEntity le) {
            living = le;
            text = e.getType().getName().getString() + "  ·  " + String.format("%.1f HP", le.getHealth() + le.getAbsorptionAmount());
        } else {
            text = e.getType().getName().getString();
        }

        double pad = 7, gap = 4;
        double tw = textW(t, text, INFO_SIZE, shadow), th = textH(t, INFO_SIZE, shadow);
        double barH = living != null && health.get() ? 4 : 0;
        double w = tw + pad * 2;
        double h = pad + th + (barH > 0 ? gap + barH : 0) + pad;
        double x = -w / 2, y = -h;

        Renderer2D.COLOR.begin();
        glass(x, y, w, h, cardRadius(10), RIM);
        if (barH > 0) {
            healthBar(e.getUuid(), x + pad, y + pad + th + gap, tw, barH,
                living.getHealth(), living.getAbsorptionAmount(), Math.max(1, living.getMaxHealth()), dt);
        }
        Renderer2D.COLOR.render();

        drawText(t, text, x + pad, y + pad, color, INFO_SIZE, shadow);
    }

    /** Same fuse format as Meteor's nametags: "3.5 s", "2 m", "1 h". */
    private static String fuse(int ticks) {
        if (ticks > 20 * 3600) return ticks / 20 / 3600 + " h";
        if (ticks > 20 * 60) return ticks / 20 / 60 + " m";
        return ticks / 20 + "." + (ticks % 20) / 2 + " s";
    }

    // ----- Shared drawing -----

    /** Card corner radius: the theme's (a bit larger, like HUD panels) when following it, otherwise the given one. */
    private double cardRadius(double own) {
        var theme = link.theme();
        return theme != null ? theme.cornerRadius.get() * 1.5 : own;
    }

    /** The glass card background (theme colors when following the theme). Call between Renderer2D.COLOR.begin() and render(). */
    private void glass(double x, double y, double w, double h, double radius, Color rim) {
        Color base = link.tint(tint);
        Shapes2D.gradientRect(x, y, w, h, radius, HudShapes.scaleAlpha(HudShapes.lighten(base, 14), 0.85), base);
        Shapes2D.border(x, y, w, h, radius, 1, link.outline(new Color(255, 255, 255, 26)));
        Shapes2D.rimLight(x + radius, y, w - radius * 2, 1.5, rim);
    }

    /** Smooth health bar, gold strip on top for absorption. Call between Renderer2D.COLOR.begin() and render(). */
    private void healthBar(UUID id, double x, double y, double w, double h, float hp, float absorb, float maxHp, double dt) {
        Spring sp = healthSprings.computeIfAbsent(id, k -> new Spring(hp / maxHp));
        double frac = Math.max(0, Math.min(1, sp.update(hp / maxHp, dt, 0.35)));
        Shapes2D.rect(x, y, w, h, h / 2, TRACK);
        if (frac > 0) Shapes2D.rect(x, y, w * frac, h, h / 2, fillColor(frac));
        if (absorb > 0) {
            double af = Math.min(1, absorb / maxHp);
            Shapes2D.rect(x, y + h * 0.25, w * af, h * 0.5, h / 4, new Color(255, 214, 10, 200));
        }
    }

    /** Green when full, yellow at half, red when empty. */
    private static Color fillColor(double frac) {
        return frac > 0.5 ? HudShapes.mix(GOLD, GREEN, (frac - 0.5) / 0.5) : HudShapes.mix(HEART, GOLD, frac / 0.5);
    }

    // ----- Enchant tags -----

    /** Tags for one item: every enchant from the shown-enchantments list (P4, S5, Unb3...), then M1 for Mending. */
    private List<Tag> tagsFor(ItemStack s) {
        if (s.isEmpty()) return List.of();
        ItemEnchantmentsComponent ench = EnchantmentHelper.getEnchantments(s);

        List<RegistryEntry<Enchantment>> shown = new ArrayList<>();
        boolean mending = false;
        for (RegistryEntry<Enchantment> e : ench.getEnchantments()) {
            if (e.matchesKey(Enchantments.MENDING)) {
                mending = true; // always its own "M1" line, never doubled
                continue;
            }
            if (e.getKey().isPresent() && shownEnchants.get().contains(e.getKey().get())) shown.add(e);
        }
        shown.sort(Comparator.comparingInt(AppleNametags::order));

        List<Tag> out = new ArrayList<>();
        for (RegistryEntry<Enchantment> e : shown) {
            Color c = e.isIn(EnchantmentTags.CURSE) ? CURSE : GOLD;
            out.add(new Tag(shortName(e) + ench.getLevel(e), c));
        }
        if (mending) out.add(new Tag("M1", GOLD));
        return out;
    }

    private static String shortName(RegistryEntry<Enchantment> e) {
        if (e.getKey().isPresent()) {
            String s = SHORT.get(e.getKey().get());
            if (s != null) return s;
        }
        String name = Names.get(e); // enchant without a short name (e.g. from a datapack): first 3 letters, like Meteor
        return name.length() > 3 ? name.substring(0, 3) : name;
    }

    /** Position in the SHORT list, so tags always appear in the same order. Unknown enchants go last. */
    private static int order(RegistryEntry<Enchantment> e) {
        if (e.getKey().isEmpty()) return Integer.MAX_VALUE;
        int i = 0;
        for (RegistryKey<Enchantment> k : SHORT.keySet()) {
            if (k.equals(e.getKey().get())) return i;
            i++;
        }
        return Integer.MAX_VALUE;
    }

    // ----- Text helpers (Meteor's text renderer must be begun before measuring or drawing) -----

    private static double textW(TextRenderer t, String s, double scale, boolean shadow) {
        t.begin(scale, true, true);
        double w = t.getWidth(s, shadow);
        t.end();
        return w;
    }

    private static double textH(TextRenderer t, double scale, boolean shadow) {
        t.begin(scale, true, true);
        double h = t.getHeight(shadow);
        t.end();
        return h;
    }

    private static void drawText(TextRenderer t, String s, double x, double y, Color c, double scale, boolean shadow) {
        t.begin(scale, false, true);
        t.render(s, x, y, c, shadow);
        t.end();
    }
}
