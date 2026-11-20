package com.david.gundi.hud.chat;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Remembers what kind of message every chat line is (player chat, whisper or system) and who sent it.
 *
 * The exact answer comes from where Minecraft receives the message (mixin/MessageHandlerMixin sets `pending`
 * while a chat packet is being added). Servers that send player chat as plain server text
 * ("[VIP] Steve » hi") are recognised by matching names from the tab list.
 */
public final class ChatTagger {
    public enum Kind { PLAYER, WHISPER, SYSTEM }

    public record Pending(Kind kind, String sender, UUID uuid, boolean outgoing) {}

    public static final class Tag {
        public final Kind kind;
        public final String sender;
        public final UUID uuid;
        public final boolean outgoing;
        public final long arrivedMs;   // 0 = arrived before Gundi saw it
        public final int arrivedTick;

        Tag(Pending p, long arrivedMs, int arrivedTick) {
            this.kind = p.kind;
            this.sender = p.sender;
            this.uuid = p.uuid;
            this.outgoing = p.outgoing;
            this.arrivedMs = arrivedMs;
            this.arrivedTick = arrivedTick;
        }
    }

    /** Set by MessageHandlerMixin while Minecraft adds a received message; null otherwise. */
    public static Pending pending;

    private static final Map<ChatHudLine, Tag> TAGS = new IdentityHashMap<>();
    private static final ArrayDeque<ChatHudLine> FRESH = new ArrayDeque<>();

    private static final Pending SYSTEM = new Pending(Kind.SYSTEM, null, null, false);

    private static final int I = Pattern.CASE_INSENSITIVE;
    private static final Pattern[] WHISPER_IN = {
        Pattern.compile("^(\\w{3,16}) whispers(?: to you)?:", I),
        Pattern.compile("^\\[?(\\w{3,16}) ?(?:->|→|») ?(?:me|you)\\]?", I),
        Pattern.compile("^From (\\w{3,16}) ?:", I),
        Pattern.compile("^(\\w{3,16}) messages you:", I),
    };
    private static final Pattern[] WHISPER_OUT = {
        Pattern.compile("^You whisper to (\\w{3,16}) ?:", I),
        Pattern.compile("^\\[?(?:me|you) ?(?:->|→|») ?(\\w{3,16})\\]?", I),
        Pattern.compile("^To (\\w{3,16}) ?:", I),
    };
    private static final Pattern ANGLE = Pattern.compile("^<(?:\\[[^\\]]{1,24}\\] ?)*([\\w]{3,16})>");
    private static final String SEPARATORS = ":>»›|→➡";

    private ChatTagger() {}

    /** Called when Minecraft adds a message to its chat list. */
    public static void onAdd(ChatHudLine line) {
        Pending p = pending != null ? pending : classify(line.content());
        TAGS.put(line, new Tag(p, System.currentTimeMillis(), line.creationTick()));
        FRESH.addLast(line);
        while (FRESH.size() > 200) FRESH.removeFirst();
    }

    /** The tag of a line; lines from before Gundi loaded are classified now. */
    public static Tag get(ChatHudLine line) {
        Tag t = TAGS.get(line);
        if (t == null) {
            t = new Tag(classify(line.content()), 0, line.creationTick());
            TAGS.put(line, t);
        }
        return t;
    }

    private static final Tag PREVIEW = new Tag(SYSTEM, 0, 0);

    /** A system tag for the HUD editor's sample lines (not stored). */
    public static Tag previewTag() {
        return PREVIEW;
    }

    /** Lines added since the last call (oldest first). */
    public static List<ChatHudLine> drainFresh() {
        if (FRESH.isEmpty()) return List.of();
        List<ChatHudLine> out = new ArrayList<>(FRESH);
        FRESH.clear();
        return out;
    }

    /** Forgets tags of lines Minecraft no longer keeps. */
    public static void prune(Collection<ChatHudLine> live) {
        if (TAGS.size() < 400) return;
        Set<ChatHudLine> keep = Collections.newSetFromMap(new IdentityHashMap<>());
        keep.addAll(live);
        TAGS.keySet().retainAll(keep);
    }

    /** Works out the kind of a message from its text alone (server/plugin formatted chat). */
    public static Pending classify(Text text) {
        String plain = Formatting.strip(text.getString());
        if (plain == null) return SYSTEM;
        plain = plain.stripLeading();
        if (plain.startsWith("[Meteor]") || plain.startsWith("[Gundi]") || plain.startsWith("[Baritone]")) return SYSTEM;

        for (Pattern p : WHISPER_IN) {
            Matcher m = p.matcher(plain);
            if (m.find()) return new Pending(Kind.WHISPER, m.group(1), uuidOf(m.group(1)), false);
        }
        for (Pattern p : WHISPER_OUT) {
            Matcher m = p.matcher(plain);
            if (m.find()) return new Pending(Kind.WHISPER, m.group(1), uuidOf(m.group(1)), true);
        }

        Matcher angle = ANGLE.matcher(plain);
        if (angle.find()) return new Pending(Kind.PLAYER, angle.group(1), uuidOf(angle.group(1)), false);

        // "[VIP] Steve » hi", "Steve: hi", "★ Steve > hi": a tab-list name right before a separator
        String head = plain.length() > 48 ? plain.substring(0, 48) : plain;
        if (mc.getNetworkHandler() != null) {
            for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                String name = e.getProfile().name();
                if (name == null || name.length() < 3) continue;
                int idx = head.indexOf(name);
                while (idx >= 0) {
                    boolean startOk = idx == 0 || !isNameChar(head.charAt(idx - 1));
                    String after = head.substring(idx + name.length()).stripLeading();
                    // allow a closing bracket after the name: "[Steve] hi" isn't chat, but "<Steve>" and "Steve]:" are
                    if (after.startsWith("]")) after = after.substring(1).stripLeading();
                    if (startOk && !after.isEmpty() && SEPARATORS.indexOf(after.charAt(0)) >= 0) {
                        return new Pending(Kind.PLAYER, name, e.getProfile().id(), false);
                    }
                    idx = head.indexOf(name, idx + 1);
                }
            }
        }
        return SYSTEM;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static UUID uuidOf(String name) {
        if (mc.getNetworkHandler() == null || name == null) return null;
        PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(name);
        return e != null ? e.getProfile().id() : null;
    }
}
