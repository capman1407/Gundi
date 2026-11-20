package com.david.gundi.hud;

import com.david.gundi.Gundi;
import com.david.gundi.gui.Spring;
import com.david.gundi.hud.chat.ChatTagger;
import com.david.gundi.hud.chat.ChatTagger.Kind;
import com.david.gundi.hud.chat.ChatTagger.Tag;
import com.david.gundi.mixin.ChatHudAccessor;
import com.david.gundi.util.Colors;
import com.david.gundi.util.ErrorReporter;
import com.david.gundi.util.GlassStyle;
import com.david.gundi.util.HudShapes;
import com.david.gundi.util.ThemeLink;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.message.ChatVisibility;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Matrix3x2fStack;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * A whole chat window that follows the selected Gundi GUI theme, in place of Minecraft's chat.
 *
 * Messages are read live from Minecraft's own chat list; ChatTagger says what each one is (player chat,
 * whisper, system) and who sent it. Typing still happens in Minecraft's chat screen: its text field is moved
 * into this window's input bar (mixin/ChatScreenMixin), so commands, history and tab completion work as usual.
 *
 * Closed: the newest lines, fading out like vanilla. Open (T): the full window with tabs (+ unread badges),
 * history, smooth scrolling, clickable links, hover text, right-click to copy and a "new messages" pill.
 *
 * Layout is in "chat units" (1 unit = one GUI pixel at scale 1), drawn at fs physical pixels per unit.
 */
public class GundiChatHud extends GundiHud {
    public static final HudElementInfo<GundiChatHud> INFO = new HudElementInfo<>(Gundi.HUD_GROUP, "gundi-chat", "A chat window that follows the Gundi theme, in place of Minecraft's chat.", GundiChatHud::new);

    private static final String NAME = "gundi-chat";
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    // Layout, in chat units
    private static final int LINE_H = 10, PAD = 5, TITLE_H = 15, INPUT_H = 15, GAP = 3, HEAD = 8;

    public enum Tab { All, Players, Whispers, System }

    // ── General ──
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final ThemeLink link = new ThemeLink(sgGeneral);

    private final Setting<Tab> tab = sgGeneral.add(new EnumSetting.Builder<Tab>()
        .name("tab")
        .description("Which messages to show: everything, player chat, private messages, or server/client messages.")
        .defaultValue(Tab.All)
        .build()
    );

    private final Setting<Boolean> showTabs = sgGeneral.add(new BoolSetting.Builder()
        .name("tabs")
        .description("Show the tabs at the top while chat is open.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> timestamps = sgGeneral.add(new BoolSetting.Builder()
        .name("timestamps")
        .description("Show the time each message arrived.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> playerHeads = sgGeneral.add(new BoolSetting.Builder()
        .name("player-heads")
        .description("Show the sender's face next to player messages (for players in the tab list).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stackRepeats = sgGeneral.add(new BoolSetting.Builder()
        .name("stack-repeats")
        .description("Show the same message sent several times in a row once, with ×N.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> highlightMentions = sgGeneral.add(new BoolSetting.Builder()
        .name("highlight-mentions")
        .description("Mark messages that contain your name.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> keywords = sgGeneral.add(new StringListSetting.Builder()
        .name("keywords")
        .description("Words that get highlighted like a mention (not case sensitive).")
        .defaultValue(List.of())
        .build()
    );

    private final Setting<Boolean> mentionSound = sgGeneral.add(new BoolSetting.Builder()
        .name("mention-sound")
        .description("Soft ping when someone says your name or a keyword, or whispers to you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> soundVolume = sgGeneral.add(new DoubleSetting.Builder()
        .name("sound-volume")
        .description("How loud the ping is.")
        .defaultValue(0.6)
        .range(0, 1)
        .visible(mentionSound::get)
        .build()
    );

    // ── Layout ──
    private final SettingGroup sgLayout = settings.createGroup("Layout");

    private final Setting<Integer> width = sgLayout.add(new IntSetting.Builder()
        .name("width")
        .description("Window width (Minecraft's chat is 320).")
        .defaultValue(320)
        .range(120, 800)
        .sliderRange(160, 600)
        .build()
    );

    private final Setting<Integer> linesClosed = sgLayout.add(new IntSetting.Builder()
        .name("lines-closed")
        .description("How many recent lines show while chat is closed.")
        .defaultValue(10)
        .range(1, 30)
        .build()
    );

    private final Setting<Integer> linesOpen = sgLayout.add(new IntSetting.Builder()
        .name("lines-open")
        .description("How many lines show while chat is open.")
        .defaultValue(20)
        .range(4, 60)
        .build()
    );

    private final Setting<Double> scale = sgLayout.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Size, relative to Minecraft's GUI scale.")
        .defaultValue(1)
        .range(0.5, 2)
        .build()
    );

    // ── Style ──
    private final SettingGroup sgStyle = settings.createGroup("Style");

    private final Setting<Boolean> animations = sgStyle.add(new BoolSetting.Builder()
        .name("animations")
        .description("Messages slide in, the window eases open, the tab pill glides and scrolling is smooth.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> fadeAfter = sgStyle.add(new DoubleSetting.Builder()
        .name("fade-after")
        .description("Seconds a new message stays while chat is closed.")
        .defaultValue(10)
        .range(1, 60)
        .sliderRange(3, 30)
        .build()
    );

    private final Setting<Boolean> textShadow = sgStyle.add(new BoolSetting.Builder()
        .name("text-shadow")
        .description("Shadow under the text.")
        .defaultValue(true)
        .build()
    );

    // ── Colors ──
    private final SettingGroup sgColors = settings.createGroup("Colors", false);

    private final Setting<SettingColor> textColor = sgColors.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Messages without their own color.")
        .defaultValue(new SettingColor(245, 245, 247))
        .visible(link.own())
        .build()
    );

    private final Setting<SettingColor> dimColor = sgColors.add(new ColorSetting.Builder()
        .name("dim-color")
        .description("Timestamps, inactive tabs, ×N and hints.")
        .defaultValue(new SettingColor(160, 160, 170))
        .visible(link.own())
        .build()
    );

    private final Setting<SettingColor> accentColor = sgColors.add(new ColorSetting.Builder()
        .name("accent-color")
        .description("Selected tab, badges, input marker and mention bar.")
        .defaultValue(new SettingColor(10, 132, 255))
        .visible(link.own())
        .build()
    );

    private final Setting<SettingColor> keywordColor = sgColors.add(new ColorSetting.Builder()
        .name("keyword-color")
        .description("Bar next to messages with one of your keywords.")
        .defaultValue(new SettingColor(255, 159, 10))
        .visible(link.own())
        .build()
    );

    private final Setting<SettingColor> tileColor = sgColors.add(new ColorSetting.Builder()
        .name("tile-color")
        .description("Tab chips and the input bar; alpha = opacity.")
        .defaultValue(new SettingColor(255, 255, 255, 22))
        .visible(link.own())
        .build()
    );

    // ── Panel ──
    private final GlassStyle panel = new GlassStyle(settings, link, new GlassStyle.Defaults(
        new SettingColor(24, 25, 30, 190), 10, false, new SettingColor(10, 132, 255, 60)));

    // ── state ──
    private static GundiChatHud current;
    private long lastDrawn;
    private boolean failed;
    private boolean wasOpen;

    private int scrollLines;
    private final Spring scrollAnim = new Spring(0);
    private final Spring openAnim = new Spring(0);
    private final Spring pillX = new Spring(-1), pillW = new Spring(0);

    private final EnumMap<Tab, Integer> unread = new EnumMap<>(Tab.class);
    private int newWhileScrolled;
    private ChatHudLine copiedLine;
    private long copiedAt;

    /** What was drawn this frame, in GUI coordinates, for clicks and hover. */
    private record Hit(double x, double y, double w, double h, OrderedText text, double textX, double unit, ChatHudLine line) {}
    private record Box(double x, double y, double w, double h) {
        boolean has(double px, double py) { return px >= x && px <= x + w && py >= y && py <= y + h; }
    }
    private final List<Hit> hits = new ArrayList<>();
    private final Map<Tab, Box> tabBoxes = new EnumMap<>(Tab.class);
    private Box pillBox;
    private double winX, winY, winW, winH;
    private int inX, inY, inW;
    private boolean hasInput;

    private record Wrapped(int width, List<OrderedText> lines) {}
    private final Map<ChatHudLine, Wrapped> wrapCache = new IdentityHashMap<>();

    public GundiChatHud() {
        super(INFO);
        current = this;
    }

    // ═════════════════════════════════════════════════════════════════════
    // For the mixins
    // ═════════════════════════════════════════════════════════════════════

    /** True while this window replaces Minecraft's chat. False if it isn't on or failed to draw: then vanilla chat shows. */
    public static boolean showing() {
        GundiChatHud c = current;
        return c != null && c.isActive() && Hud.get().active && !c.failed
            && System.currentTimeMillis() - c.lastDrawn < 1000;
    }

    public static GundiChatHud get() {
        return current;
    }

    /** Where Minecraft's chat text field goes (GUI coords), or null before the first open frame. */
    public int[] inputRect() {
        return hasInput ? new int[]{inX, inY, inW} : null;
    }

    public boolean contains(double gx, double gy) {
        return gx >= winX && gx <= winX + winW && gy >= winY && gy <= winY + winH;
    }

    /** Left click inside the window: tabs, the new-messages pill. Returns true when it hit one. */
    public boolean clickControls(double gx, double gy) {
        for (Map.Entry<Tab, Box> e : tabBoxes.entrySet()) {
            if (e.getValue().has(gx, gy)) {
                tab.set(e.getKey());
                scrollLines = 0;
                newWhileScrolled = 0;
                return true;
            }
        }
        if (pillBox != null && pillBox.has(gx, gy)) {
            scrollLines = 0;
            newWhileScrolled = 0;
            return true;
        }
        return false;
    }

    /** Right click: copies the message under the mouse. Returns true when it copied one. */
    public boolean copyAt(double gx, double gy) {
        for (Hit h : hits) {
            if (gx < h.x || gx > h.x + h.w || gy < h.y || gy > h.y + h.h) continue;
            String plain = Formatting.strip(h.line.content().getString());
            if (plain == null) return false;
            mc.keyboard.setClipboard(plain);
            copiedLine = h.line;
            copiedAt = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    public void scroll(double amount) {
        if (amount == 0) return;
        int step = (int) Math.signum(amount) * Math.max(1, (int) Math.round(Math.abs(amount)));
        scrollLines = Math.max(0, scrollLines + step); // up = older
        if (scrollLines == 0) newWhileScrolled = 0;
    }

    /** The text style under the mouse (GUI coords), or null. */
    public Style styleAt(double gx, double gy) {
        for (Hit h : hits) {
            if (gx < h.x || gx > h.x + h.w || gy < h.y || gy > h.y + h.h) continue;
            double local = (gx - h.textX) / h.unit; // in font units
            if (local < 0) return null;
            Style[] found = {null};
            double[] at = {0};
            h.text.accept((index, style, codePoint) -> {
                int cw = mc.textRenderer.getWidth(OrderedText.styledForwardsVisitedString(new String(Character.toChars(codePoint)), style));
                if (local < at[0] + cw) {
                    found[0] = style;
                    return false;
                }
                at[0] += cw;
                return true;
            });
            return found[0];
        }
        return null;
    }

    // ═════════════════════════════════════════════════════════════════════
    // Drawing
    // ═════════════════════════════════════════════════════════════════════

    /** A message after stacking: the newest copy, how many copies, and what kind of highlight it gets. */
    private record Entry(ChatHudLine line, Tag tag, String plain, int count, int mark) {}

    /** One wrapped line of an entry, newest first. */
    private record Row(Entry entry, OrderedText text, boolean first, float alpha, double appear) {}

    @Override
    public void render(HudRenderer r) {
        current = this;
        hits.clear();
        tabBoxes.clear();
        pillBox = null;
        hasInput = false;
        try {
            link.begin();
            double dt = link.dt(r);
            double gs = mc.getWindow().getScaleFactor();
            double unitGui = scale.get();
            double fs = unitGui * gs;
            double panelScale = fs / 2;
            boolean open = mc.currentScreen instanceof ChatScreen && !isInEditor();
            boolean anim = animations.get();

            double wPx = width.get() * fs;
            double fullH = (TITLE_H + GAP + linesOpen.get() * LINE_H + PAD * 2 + GAP + INPUT_H) * fs;
            setSize(wPx, fullH);

            if (!open) {
                scrollLines = 0;
                newWhileScrolled = 0;
            }
            if (open && !wasOpen) openAnim.snap(anim ? 0 : 1);
            wasOpen = open;
            openAnim.update(open ? 1 : 0, dt, 0.25);
            if (!anim) openAnim.snap(open ? 1 : 0);

            handleFresh(open);
            if (open) unread.put(tab.get(), 0);

            if (mc.options.getChatVisibility().getValue() == ChatVisibility.HIDDEN && !isInEditor()) {
                markDrawn();
                return;
            }

            List<Row> rows = collectRows(open);
            int maxLines = open ? linesOpen.get() : linesClosed.get();
            int maxScroll = Math.max(0, rows.size() - maxLines);
            scrollLines = Math.min(scrollLines, maxScroll);
            scrollAnim.update(scrollLines, dt, 0.18);
            if (!anim || !open) scrollAnim.snap(scrollLines);

            double bottom = y + fullH;
            if (open) drawOpen(r, rows, maxLines, maxScroll, wPx, bottom, fs, gs, unitGui, panelScale, dt);
            else drawClosed(r, rows, maxLines, wPx, bottom, fs, gs, unitGui, panelScale);
            markDrawn();
        } catch (Exception e) {
            failed = true;
            ErrorReporter.report(NAME, "render", e);
        }
    }

    private void markDrawn() {
        lastDrawn = System.currentTimeMillis();
        failed = false;
    }

    /** New messages: unread badges, the "new" pill and the mention ping. */
    private void handleFresh(boolean open) {
        List<ChatHudLine> fresh = ChatTagger.drainFresh();
        if (fresh.isEmpty()) return;
        String me = myName();
        boolean ping = false;
        for (ChatHudLine line : fresh) {
            Tag tag = ChatTagger.get(line);
            Tab own = tabOf(tag.kind);
            for (Tab t : new Tab[]{Tab.All, own}) {
                if (!(open && tab.get() == t)) unread.merge(t, 1, Integer::sum);
            }
            if (open && scrollLines > 0 && matchesTab(tag)) newWhileScrolled++;

            String plain = plainOf(line);
            boolean mine = me != null && me.equalsIgnoreCase(tag.sender);
            boolean incomingWhisper = tag.kind == Kind.WHISPER && !tag.outgoing && !mine;
            if (!mine && (markOf(plain, tag, me) > 0 || incomingWhisper)) ping = true;
        }
        if (ping && mentionSound.get() && soundVolume.get() > 0) {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.6f, soundVolume.get().floatValue()));
        }
    }

    private static Tab tabOf(Kind kind) {
        return switch (kind) {
            case PLAYER -> Tab.Players;
            case WHISPER -> Tab.Whispers;
            case SYSTEM -> Tab.System;
        };
    }

    private boolean matchesTab(Tag tag) {
        return tab.get() == Tab.All || tab.get() == tabOf(tag.kind);
    }

    private static String myName() {
        return mc.getSession() != null ? mc.getSession().getUsername() : null;
    }

    private static String plainOf(ChatHudLine line) {
        String s = Formatting.strip(line.content().getString());
        return s == null ? "" : s;
    }

    /** 0 = nothing, 1 = mentions you, 2 = has a keyword. Never for your own messages. */
    private int markOf(String plain, Tag tag, String me) {
        if (me != null && me.equalsIgnoreCase(tag.sender)) return 0;
        String low = plain.toLowerCase(Locale.ROOT);
        if (highlightMentions.get() && me != null && low.contains(me.toLowerCase(Locale.ROOT))) return 1;
        for (String k : keywords.get()) {
            if (!k.isBlank() && low.contains(k.toLowerCase(Locale.ROOT))) return 2;
        }
        return 0;
    }

    private List<Row> collectRows(boolean open) {
        List<Row> rows = new ArrayList<>();
        if (isInEditor() || mc.inGameHud == null) {
            if (isInEditor()) {
                Tag sys = ChatTagger.previewTag();
                String[] samples = {"<Steve> anyone near spawn?", "<Alex> come look at this", "[Meteor] Gundi Chat follows your theme"};
                for (String s : samples) {
                    ChatHudLine line = new ChatHudLine(0, Text.literal(s), null, null);
                    Entry entry = new Entry(line, sys, s, 1, 0);
                    List<OrderedText> lines = mc.textRenderer.wrapLines(Text.literal(s), width.get() - PAD * 2);
                    for (int i = lines.size() - 1; i >= 0; i--) rows.add(new Row(entry, lines.get(i), i == 0, 1, 1));
                }
            }
            return rows;
        }

        List<ChatHudLine> messages = ((ChatHudAccessor) mc.inGameHud.getChatHud()).addon$messages();
        ChatTagger.prune(messages);
        if (wrapCache.size() > 400) {
            Set<ChatHudLine> live = Collections.newSetFromMap(new IdentityHashMap<>());
            live.addAll(messages);
            wrapCache.keySet().retainAll(live);
        }

        String me = myName();
        int now = mc.inGameHud.getTicks();
        long nowMs = System.currentTimeMillis();
        double fadeTicks = fadeAfter.get() * 20;
        double chatOpacity = mc.options.getChatOpacity().getValue() * 0.9 + 0.1;
        int wrapW = Math.max(40, width.get() - PAD * 2 - prefixWidth());
        int needed = open ? linesOpen.get() + scrollLines + 2 : linesClosed.get() + 1;

        // 1. messages → entries (filtered by tab, repeats stacked); newest first
        List<Entry> entries = new ArrayList<>();
        for (ChatHudLine m : messages) {
            Tag tag = ChatTagger.get(m);
            if (!matchesTab(tag)) continue;
            String plain = plainOf(m);
            if (stackRepeats.get() && !entries.isEmpty()) {
                Entry last = entries.get(entries.size() - 1);
                if (last.plain.equals(plain)) {
                    entries.set(entries.size() - 1, new Entry(last.line, last.tag, last.plain, last.count + 1, last.mark));
                    continue;
                }
            }
            if (!open && entries.size() >= needed) break;
            entries.add(new Entry(m, tag, plain, 1, markOf(plain, tag, me)));
            if (open && entries.size() > needed + 300) break;
        }

        // 2. entries → wrapped rows
        for (Entry e : entries) {
            float alpha = 1;
            if (!open) {
                double age = now - e.tag.arrivedTick;
                if (age >= fadeTicks) break;
                alpha = (float) (Math.min(1, (fadeTicks - age) / 20.0) * chatOpacity);
            }
            double appear = 1;
            if (animations.get() && e.tag.arrivedMs > 0) {
                double t = Math.min(1, (nowMs - e.tag.arrivedMs) / 250.0);
                appear = 1 - Math.pow(1 - t, 3);
            }
            Wrapped w = wrapCache.get(e.line);
            if (w == null || w.width != wrapW) {
                w = new Wrapped(wrapW, e.line.breakLines(mc.textRenderer, wrapW));
                wrapCache.put(e.line, w);
            }
            for (int i = w.lines.size() - 1; i >= 0; i--) {
                rows.add(new Row(e, w.lines.get(i), i == 0, alpha, appear));
            }
            if (!open && rows.size() >= needed) break;
        }
        return rows;
    }

    /** Space before the message text: mention bar, head, timestamp (chat units). */
    private int prefixWidth() {
        int w = 0;
        if (highlightMentions.get() || !keywords.get().isEmpty()) w += 4;
        if (playerHeads.get()) w += HEAD + 3;
        if (timestamps.get()) w += mc.textRenderer.getWidth("00:00 ");
        return w;
    }

    private void drawClosed(HudRenderer r, List<Row> rows, int maxLines, double wPx, double bottom, double fs, double gs, double unitGui, double panelScale) {
        List<Row> shown = rows.subList(0, Math.min(rows.size(), maxLines));
        if (shown.isEmpty()) return;
        double hPx = (shown.size() * LINE_H + PAD * 2) * fs;
        double top = bottom - (INPUT_H + GAP) * fs - hPx;
        float panelAlpha = 0;
        for (Row row : shown) panelAlpha = Math.max(panelAlpha, row.alpha);
        panel.draw(r, x, top, wPx, hPx, panelScale, panelAlpha);
        double listTop = top + PAD * fs;
        for (int i = 0; i < shown.size(); i++) {
            Row row = shown.get(shown.size() - 1 - i);
            drawRow(r, row, listTop + i * LINE_H * fs, wPx, fs, gs, unitGui, false, 1);
        }
    }

    private void drawOpen(HudRenderer r, List<Row> rows, int maxLines, int maxScroll, double wPx, double bottom,
                          double fs, double gs, double unitGui, double panelScale, double dt) {
        double a = Math.max(0, Math.min(1, openAnim.value));
        double slide = (1 - a) * 6 * fs;

        double inputTop = bottom - INPUT_H * fs + slide;
        double winBottom = inputTop - GAP * fs;
        double winTop = y + slide;
        double hPx = winBottom - winTop;
        panel.draw(r, x, winTop, wPx, hPx, panelScale, a);

        winX = x / gs;
        winY = winTop / gs;
        winW = wPx / gs;
        winH = hPx / gs;

        // ── title row: "Chat", tab chips with a gliding accent pill and unread badges ──
        double ty = winTop + PAD * fs;
        drawString(r, "Chat", x + PAD * fs, ty + 3 * fs, fs, gs, Colors.alpha(link.text(textColor), a), true);
        if (showTabs.get()) {
            Tab[] tabs = Tab.values();
            double[] cxs = new double[tabs.length], cws = new double[tabs.length];
            double cx = x + wPx - PAD * fs;
            for (int i = tabs.length - 1; i >= 0; i--) {
                double tw = (mc.textRenderer.getWidth(tabs[i].name()) + 10) * fs;
                cx -= tw;
                cxs[i] = cx;
                cws[i] = tw;
                cx -= 3 * fs;
            }
            double chipH = 13 * fs;
            for (int i = 0; i < tabs.length; i++) {
                HudShapes.rect(r, cxs[i], ty, cws[i], chipH, chipH / 2, Colors.alpha(link.tile(tileColor), a));
            }
            int sel = tab.get().ordinal();
            if (pillX.value < 0 || !animations.get()) {
                pillX.snap(cxs[sel]);
                pillW.snap(cws[sel]);
            }
            pillX.update(cxs[sel], dt, 0.2);
            pillW.update(cws[sel], dt, 0.2);
            HudShapes.rect(r, pillX.value, ty, pillW.value, chipH, chipH / 2, Colors.alpha(link.accent(accentColor), 0.85 * a));
            for (int i = 0; i < tabs.length; i++) {
                Tab t = tabs[i];
                boolean selected = t.ordinal() == sel;
                drawString(r, t.name(), cxs[i] + 5 * fs, ty + 3 * fs, fs, gs,
                    Colors.alpha(selected ? new Color(255, 255, 255) : link.dim(dimColor), a), false);
                tabBoxes.put(t, new Box(cxs[i] / gs, ty / gs, cws[i] / gs, chipH / gs));
                int n = unread.getOrDefault(t, 0);
                if (!selected && n > 0) {
                    String label = n > 9 ? "9+" : String.valueOf(n);
                    double bw = Math.max(7, mc.textRenderer.getWidth(label) * 0.75 + 3) * fs, bh = 7 * fs;
                    double bx = cxs[i] + cws[i] - bw * 0.6, by = ty - bh * 0.35;
                    HudShapes.rect(r, bx, by, bw, bh, bh / 2, Colors.alpha(link.accent(accentColor), a));
                    drawStringScaled(r, label, bx + (bw - mc.textRenderer.getWidth(label) * 0.75 * fs) / 2, by + 1 * fs, fs * 0.75, gs, Colors.alpha(new Color(255, 255, 255), a));
                }
            }
        }

        // ── messages, bottom-aligned, smooth scrolling ──
        double listTop = winTop + (PAD + TITLE_H + GAP) * fs;
        double listBottom = winBottom - PAD * fs;
        double s = Math.max(0, scrollAnim.value);
        int base = (int) Math.floor(s);
        double frac = s - base;
        int count = Math.min(rows.size() - base, maxLines + 1);
        for (int k = 0; k < count; k++) {
            Row row = rows.get(base + k);
            double ly = listBottom - (k + 1) * LINE_H * fs + frac * LINE_H * fs;
            if (ly < listTop - 0.5 || ly + LINE_H * fs > listBottom + 0.5) continue;
            drawRow(r, row, ly, wPx, fs, gs, unitGui, true, a);
        }

        // scrollbar
        if (maxScroll > 0) {
            double trackH = listBottom - listTop;
            double thumbH = Math.max(8 * fs, trackH * maxLines / Math.max(1, rows.size()));
            double pos = 1 - s / maxScroll;
            double thumbY = listTop + (trackH - thumbH) * Math.max(0, Math.min(1, pos));
            HudShapes.rect(r, x + wPx - 3 * fs, thumbY, 1.5 * fs, thumbH, 0.75 * fs, Colors.alpha(link.dim(dimColor), 0.6 * a));
        }

        // "↓ N new" pill while scrolled up
        if (scrollLines > 0 && newWhileScrolled > 0) {
            String label = "↓ " + newWhileScrolled + " new";
            double pw = (mc.textRenderer.getWidth(label) + 12) * fs, ph = 12 * fs;
            double px = x + (wPx - pw) / 2, py = listBottom - ph - 2 * fs;
            HudShapes.rect(r, px, py, pw, ph, ph / 2, Colors.alpha(link.accent(accentColor), 0.95 * a));
            drawString(r, label, px + 6 * fs, py + 2.5 * fs, fs, gs, Colors.alpha(new Color(255, 255, 255), a), false);
            pillBox = new Box(px / gs, py / gs, pw / gs, ph / gs);
        }

        // ── input bar: Minecraft's text field is moved inside it ──
        panel.draw(r, x, inputTop, wPx, INPUT_H * fs, panelScale, a);
        HudShapes.rect(r, x + 2 * fs, inputTop + 2 * fs, wPx - 4 * fs, (INPUT_H - 4) * fs, 3 * fs, Colors.alpha(link.tile(tileColor), a));
        drawString(r, "▸", x + 6 * fs, inputTop + 4 * fs, fs, gs, Colors.alpha(link.accent(accentColor), a), false);
        double fieldX = x + 16 * fs;
        inX = (int) Math.round(fieldX / gs);
        inY = (int) Math.round((inputTop + (INPUT_H * fs - 12 * gs) / 2) / gs) + 2;
        inW = (int) Math.round((wPx - 22 * fs) / gs);
        hasInput = true;
    }

    /** One line of a message at physical y ly. */
    private void drawRow(HudRenderer r, Row row, double ly, double wPx, double fs, double gs, double unitGui, boolean record, double windowAlpha) {
        Entry e = row.entry;
        double alpha = row.alpha * windowAlpha * row.appear;
        if (alpha < 0.03) return;
        double slideIn = (1 - row.appear) * 12 * fs;
        double rx = x + slideIn;

        // highlight: mention (accent) or keyword (keyword color)
        if (e.mark > 0) {
            Color c = e.mark == 1 ? link.accent(accentColor) : link.accent2(keywordColor);
            HudShapes.rect(r, rx + 2 * fs, ly, 1.5 * fs, LINE_H * fs, 0.75 * fs, Colors.alpha(c, alpha));
            HudShapes.rect(r, rx + 2 * fs, ly, wPx - 4 * fs, LINE_H * fs, 2 * fs, Colors.alpha(c, 0.10 * alpha));
        }
        // copied flash
        boolean copied = e.line == copiedLine && System.currentTimeMillis() - copiedAt < 800;
        if (copied) {
            double f = 1 - (System.currentTimeMillis() - copiedAt) / 800.0;
            HudShapes.rect(r, rx + 2 * fs, ly, wPx - 4 * fs, LINE_H * fs, 2 * fs, Colors.alpha(link.accent(accentColor), 0.25 * f * alpha));
        }

        double tx = rx + PAD * fs;
        if (highlightMentions.get() || !keywords.get().isEmpty()) tx += 4 * fs;
        if (playerHeads.get()) {
            if (row.first && e.tag.kind != Kind.SYSTEM && alpha > 0.5) drawHead(r, e.tag, tx, ly + 1 * fs, fs, gs);
            tx += (HEAD + 3) * fs;
        }
        if (timestamps.get()) {
            if (row.first) drawString(r, timeOf(e.tag), tx, ly + 1 * fs, fs, gs, Colors.alpha(link.dim(dimColor), alpha), false);
            tx += mc.textRenderer.getWidth("00:00 ") * fs;
        }
        drawText(r, row.text, tx, ly + 1 * fs, fs, gs, Colors.alpha(link.text(textColor), alpha));

        // ×N after the first line of a stacked message
        if (e.count > 1 && row.first) {
            String label = "×" + e.count;
            double lw = mc.textRenderer.getWidth(label) * fs;
            double after = tx + mc.textRenderer.getWidth(row.text) * fs + 4 * fs;
            double cx = Math.min(after, x + wPx - lw - 8 * fs);
            HudShapes.rect(r, cx - 2 * fs, ly + 0.5 * fs, lw + 4 * fs, (LINE_H - 1) * fs, 3 * fs, Colors.alpha(link.tile(tileColor), alpha));
            drawString(r, label, cx, ly + 1 * fs, fs, gs, Colors.alpha(link.dim(dimColor), alpha), false);
        }
        if (copied && row.first) {
            String label = "Copied";
            double lw = mc.textRenderer.getWidth(label) * fs;
            double cx = x + wPx - lw - 10 * fs;
            HudShapes.rect(r, cx - 3 * fs, ly + 0.5 * fs, lw + 6 * fs, (LINE_H - 1) * fs, 4 * fs, Colors.alpha(link.accent(accentColor), 0.9 * alpha));
            drawString(r, label, cx, ly + 1 * fs, fs, gs, Colors.alpha(new Color(255, 255, 255), alpha), false);
        }

        if (record) hits.add(new Hit(x / gs, ly / gs, wPx / gs, LINE_H * fs / gs, row.text, tx / gs, unitGui, e.line));
    }

    private String timeOf(Tag tag) {
        if (tag.arrivedMs <= 0) return "--:--";
        return LocalTime.ofInstant(Instant.ofEpochMilli(tag.arrivedMs), ZoneId.systemDefault()).format(CLOCK);
    }

    private void drawHead(HudRenderer r, Tag tag, double px, double py, double fs, double gs) {
        if (mc.getNetworkHandler() == null) return;
        PlayerListEntry entry = null;
        if (tag.uuid != null) entry = mc.getNetworkHandler().getPlayerListEntry(tag.uuid);
        if (entry == null && tag.sender != null) entry = mc.getNetworkHandler().getPlayerListEntry(tag.sender);
        if (entry == null) return;
        Matrix3x2fStack m = r.drawContext.getMatrices();
        m.pushMatrix();
        m.scale((float) (1 / gs));
        m.translate((float) px, (float) py);
        m.scale((float) fs);
        PlayerSkinDrawer.draw(r.drawContext, entry.getSkinTextures(), 0, 0, HEAD);
        m.popMatrix();
    }

    private void drawString(HudRenderer r, String s, double px, double py, double fs, double gs, Color c, boolean bold) {
        Text t = bold ? Text.literal(s).styled(st -> st.withBold(true)) : Text.literal(s);
        drawText(r, t.asOrderedText(), px, py, fs, gs, c);
    }

    private void drawStringScaled(HudRenderer r, String s, double px, double py, double fs, double gs, Color c) {
        drawText(r, Text.literal(s).asOrderedText(), px, py, fs, gs, c);
    }

    /** Draws formatted text at physical pixel (px, py), fs pixels per font unit. */
    private void drawText(HudRenderer r, OrderedText text, double px, double py, double fs, double gs, Color c) {
        if (c.a < 8) return; // Minecraft draws near-zero alpha as fully opaque
        Matrix3x2fStack m = r.drawContext.getMatrices();
        m.pushMatrix();
        m.scale((float) (1 / gs));
        m.translate((float) px, (float) py);
        m.scale((float) fs);
        r.drawContext.drawText(mc.textRenderer, text, 0, 0, c.getPacked(), textShadow.get());
        m.popMatrix();
    }
}
