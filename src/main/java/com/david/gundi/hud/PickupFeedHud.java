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
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Apple notification-style pickup feed: when items enter your inventory, a glass card slides in:
 *   [icon]  +12  Obsidian
 * Picking up more of the same item while its card is still showing adds to it (with a little bump).
 * Cards stack, glide into place when others disappear, and slide out + fade when they expire.
 */
public class PickupFeedHud extends GundiHud {
    public static final HudElementInfo<PickupFeedHud> INFO = new HudElementInfo<>(Gundi.HUD_GROUP, "pickup-feed", "Notification cards for items entering your inventory.", PickupFeedHud::new);

    private static final String NAME = "pickup-feed";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    // Follow the selected GUI theme (first setting in General). See util/ThemeLink.
    private final ThemeLink link = new ThemeLink(sgGeneral);
    private final SettingGroup sgLook = settings.createGroup("Style");

    private final Setting<Double> lifetime = sgGeneral.add(new DoubleSetting.Builder()
        .name("lifetime")
        .description("Seconds a card stays.")
        .defaultValue(3)
        .range(1, 10)
        .build()
    );

    private final Setting<Integer> maxCards = sgGeneral.add(new IntSetting.Builder()
        .name("max-cards")
        .description("How many cards can show at once.")
        .defaultValue(5)
        .range(1, 10)
        .build()
    );

    private final Setting<Boolean> showRemoved = sgGeneral.add(new BoolSetting.Builder()
        .name("show-removed")
        .description("Also show items leaving your inventory (dropped, used, placed) in red.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> scale = sgLook.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Size.")
        .defaultValue(1)
        .range(0.5, 3)
        .build()
    );

    private final Setting<Boolean> lifetimeBar = sgLook.add(new BoolSetting.Builder()
        .name("lifetime-bar")
        .description("Thin bar along the bottom of each card that shrinks until it disappears.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> flash = sgLook.add(new BoolSetting.Builder()
        .name("flash")
        .description("New / updated cards glow briefly in green (gained) or red (lost).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> iconTile = sgLook.add(new BoolSetting.Builder()
        .name("icon-tile")
        .description("Put the item icon on its own small rounded tile.")
        .defaultValue(true)
        .build()
    );

    // Panel settings (card glass). Corner radius 40 = fully rounded pills; lower it for rounded cards.
    private final GlassStyle panel = new GlassStyle(settings, link, new GlassStyle.Defaults(
        new SettingColor(24, 25, 30, 195), 40, false, new SettingColor(48, 209, 88, 60)));

    private static final Color GAIN = new Color(48, 209, 88);
    private static final Color LOSS = new Color(255, 69, 58);
    private static final Color TEXT = new Color(245, 245, 247);

    private static class Card {
        ItemStack icon;
        String name;
        Item item;
        int amount;            // positive = gained, negative = lost
        long createdAt, refreshedAt;
        final Spring slot = new Spring(-1);   // animated position in the stack
        final Spring appear = new Spring(0);  // 0 -> 1 slide/fade in
        final Spring bump = new Spring(0);    // little pop when the count increases
    }

    private final List<Card> cards = new ArrayList<>();
    private Map<Item, Integer> lastCounts;
    private final Map<Item, ItemStack> samples = new HashMap<>();
    private ClientWorld trackedWorld;

    public PickupFeedHud() {
        super(INFO);
    }

    @Override
    public void tick(HudRenderer renderer) {
        try {
            if (mc.player == null || mc.world == null) {
                lastCounts = null;
                return;
            }

            // New world / respawn into a new world: start fresh instead of "picking up" your whole inventory
            if (mc.world != trackedWorld) {
                trackedWorld = mc.world;
                lastCounts = null;
            }

            Map<Item, Integer> counts = new HashMap<>();
            PlayerInventory inv = mc.player.getInventory();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (stack.isEmpty()) continue;
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
                samples.putIfAbsent(stack.getItem(), stack.copyWithCount(1));
            }

            if (lastCounts != null) {
                Set<Item> all = new HashSet<>(counts.keySet());
                all.addAll(lastCounts.keySet());
                for (Item item : all) {
                    int diff = counts.getOrDefault(item, 0) - lastCounts.getOrDefault(item, 0);
                    if (diff > 0 || (diff < 0 && showRemoved.get())) add(item, diff);
                }
            }

            lastCounts = counts;
        } catch (Exception e) {
            ErrorReporter.report(NAME, "tick", e);
        }
    }

    private void add(Item item, int diff) {
        long now = System.nanoTime();

        // Same item still on screen (and same direction): add to it
        for (Card c : cards) {
            if (c.item == item && Integer.signum(c.amount) == Integer.signum(diff) && !expired(c, now)) {
                c.amount += diff;
                c.refreshedAt = now;
                c.bump.velocity += 8; // kick the spring for a small pop
                return;
            }
        }

        Card c = new Card();
        c.item = item;
        ItemStack sample = samples.get(item);
        c.icon = sample != null ? sample : new ItemStack(item);
        c.name = c.icon.getName().getString();
        c.amount = diff;
        c.createdAt = c.refreshedAt = now;
        cards.add(0, c); // newest on top

        while (cards.size() > maxCards.get()) cards.remove(cards.size() - 1);
    }

    private boolean expired(Card c, long now) {
        return (now - c.refreshedAt) / 1e9 > lifetime.get();
    }

    @Override
    public void render(HudRenderer renderer) {
        try {
            link.begin();
            double s = scale.get();
            double dt = link.dt(renderer);
            double cardH = 30 * s, gap = 6 * s, pad = 10 * s, textSize = 14 * s;
            double boxW = 220 * s;
            setSize(boxW, maxCards.get() * (cardH + gap));

            // On the right half of the screen, cards line up against the box's right edge instead of the left.
            boolean right = x + boxW / 2 > Utils.getWindowWidth() / 2.0;

            if (isInEditor() && cards.isEmpty()) {
                drawCard(renderer, x, y, s, new ItemStack(Items.OBSIDIAN), "Obsidian", 12, 1, 0, right, boxW, 0.7, 0);
                drawCard(renderer, x, y + cardH + gap, s, new ItemStack(Items.TOTEM_OF_UNDYING), "Totem of Undying", 1, 0.8, 0, right, boxW, 0.4, 0);
                return;
            }

            long now = System.nanoTime();
            int index = 0;
            for (Iterator<Card> it = cards.iterator(); it.hasNext(); ) {
                Card c = it.next();
                boolean gone = expired(c, now);

                c.appear.update(gone ? 0 : 1, dt, 0.35);
                c.bump.update(0, dt, 0.25);
                if (c.slot.value < 0) c.slot.snap(index); // first frame: start in its slot
                c.slot.update(index, dt, 0.35);

                if (gone && c.appear.value < 0.02) {
                    it.remove();
                    continue;
                }

                double a = Math.max(0, Math.min(1, c.appear.value));
                // Slides in from (and out to) the side of the screen the feed is on
                double slideX = (1 - a) * 30 * s * (right ? 1 : -1);
                double cy = y + c.slot.value * (cardH + gap);
                double life = Math.max(0, 1 - (now - c.refreshedAt) / 1e9 / lifetime.get());
                double fl = Math.max(0, 1 - (now - c.refreshedAt) / 1e9 / 0.6); // flash for 0.6s after a change
                drawCard(renderer, x + slideX, cy, s, c.icon, c.name, c.amount, a, c.bump.value, right, boxW, life, fl);

                if (!gone) index++;
            }
        } catch (Exception e) {
            ErrorReporter.report(NAME, "render", e);
        }
    }

    /** cx = left edge of the box; with alignRight the card is moved so its right edge meets the box's right edge. */
    /**
     * life = 1..0 remaining time (for the lifetime bar), fl = 1..0 flash strength right after a change.
     */
    private void drawCard(HudRenderer renderer, double cx, double cy, double s, ItemStack icon, String name, int amount,
                          double alpha, double bump, boolean alignRight, double boxW, double life, double fl) {
        double cardH = 30 * s, pad = 8 * s, textSize = 14 * s;
        String count = (amount > 0 ? "+" : "") + amount;
        double countSize = textSize * (1 + 0.15 * Math.max(0, bump));
        Color tone = amount > 0 ? link.gain(GAIN) : link.loss(LOSS);

        // Whole-number item scale so the 16x16 pixel-art icon stays sharp
        int itemScale = (int) Math.max(1, Math.round(s));
        double iconSize = 16 * itemScale;
        double tileSize = iconTile.get() ? Math.max(iconSize + 4 * s, cardH - 8 * s) : iconSize;
        double countW = CrispText.width(renderer, count, countSize);
        double nameW = CrispText.width(renderer, name, textSize);
        double w = pad + tileSize + 8 * s + countW + 6 * s + nameW + pad + 4 * s;
        if (alignRight) cx += boxW - w;

        if (flash.get() && fl > 0) HudShapes.outerGlow(renderer, cx, cy, w, cardH, panel.radius(s, w, cardH), 8 * s, HudShapes.scaleAlpha(tone, 0.6 * fl * alpha));
        panel.draw(renderer, cx, cy, w, cardH, s, alpha);
        if (alpha < 0.05) return;

        // Icon tile
        double tileX = cx + pad, tileY = cy + (cardH - tileSize) / 2;
        if (iconTile.get()) HudShapes.rect(renderer, tileX, tileY, tileSize, tileSize, tileSize * 0.3, link.tile(new Color(255, 255, 255, (int) (18 * alpha))));

        // Item icons can't be faded, so hide the icon for the second half of the fade instead of letting it float alone
        if (alpha > 0.5) {
            renderer.item(icon, (int) Math.round(tileX + (tileSize - iconSize) / 2), (int) Math.round(tileY + (tileSize - iconSize) / 2), itemScale, false);
        }

        // Lifetime bar: thin line along the bottom, shrinking towards the center
        if (lifetimeBar.get() && life > 0) {
            double barW = (w - cardH) * life, barH = Math.max(1, 1.5 * s);
            HudShapes.rect(renderer, cx + (w - barW) / 2, cy + cardH - barH - 2 * s, barW, barH, barH / 2, HudShapes.scaleAlpha(tone, 0.55 * alpha));
        }

        double tx = cx + pad + tileSize + 8 * s;
        double ch = CrispText.height(renderer, countSize), th = CrispText.height(renderer, textSize);
        CrispText.draw(renderer, count, tx, cy + (cardH - ch) / 2, HudShapes.scaleAlpha(tone, alpha), false, countSize);
        CrispText.draw(renderer, name, tx + countW + 6 * s, cy + (cardH - th) / 2, HudShapes.scaleAlpha(link.text(TEXT), alpha), false, textSize);
    }
}
