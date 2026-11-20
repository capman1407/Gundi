package com.david.gundi.util;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keeps saved settings when settings move to a different group or get renamed.
 *
 * Meteor saves settings per group ("Look" -> "scale"), and when loading only looks for a setting inside the
 * group with the same name. So moving "scale" from "Look" to "Style" would silently reset it.
 * regroup() rewrites the saved data before Meteor reads it: every saved setting goes into whichever group
 * holds a setting with that name now.
 *
 * - Names that exist in more than one group stay in their own group (we can't tell which one was meant).
 * - renames: "OldGroup/old-name" -> "new-name", for settings that were renamed.
 */
public final class SettingsCompat {
    private SettingsCompat() {}

    public static NbtCompound regroup(NbtCompound tag, Settings settings, Map<String, String> renames) {
        try {
            // Where every setting lives now; names used in more than one group map to null
            Map<String, String> groupOf = new HashMap<>();
            for (SettingGroup g : settings) {
                for (Setting<?> s : g) {
                    if (groupOf.containsKey(s.name)) groupOf.put(s.name, null);
                    else groupOf.put(s.name, g.name);
                }
            }

            Map<String, NbtList> settingsByGroup = new LinkedHashMap<>();
            Map<String, Boolean> expanded = new HashMap<>();
            for (SettingGroup g : settings) settingsByGroup.put(g.name, new NbtList());

            for (NbtElement e : tag.getListOrEmpty("groups")) {
                if (!(e instanceof NbtCompound groupTag)) continue;
                String oldGroup = groupTag.getString("name", "");
                if (settingsByGroup.containsKey(oldGroup)) expanded.put(oldGroup, groupTag.getBoolean("sectionExpanded", false));

                for (NbtElement se : groupTag.getListOrEmpty("settings")) {
                    if (!(se instanceof NbtCompound settingTag)) continue;
                    String name = settingTag.getString("name", "");
                    String renamed = renames.get(oldGroup + "/" + name);
                    if (renamed != null) {
                        settingTag = settingTag.copy();
                        settingTag.putString("name", renamed);
                        name = renamed;
                    }

                    String target;
                    if (groupOf.containsKey(name) && groupOf.get(name) == null) target = oldGroup; // ambiguous: keep
                    else target = groupOf.get(name);
                    if (target == null || !settingsByGroup.containsKey(target)) continue; // setting no longer exists

                    settingsByGroup.get(target).add(settingTag);
                }
            }

            NbtList groups = new NbtList();
            for (SettingGroup g : settings) {
                NbtCompound gt = new NbtCompound();
                gt.putString("name", g.name);
                // Groups that are new (nothing saved for them yet) open the way the code says
                gt.putBoolean("sectionExpanded", expanded.getOrDefault(g.name, g.sectionExpanded));
                NbtList list = settingsByGroup.get(g.name);
                if (!list.isEmpty()) gt.put("settings", list);
                groups.add(gt);
            }

            NbtCompound out = tag.copy();
            out.put("groups", groups);
            return out;
        } catch (Exception e) {
            ErrorReporter.report("settings", "regroup", e);
            return tag;
        }
    }

    /** regroup() for a whole saved module / HUD element / theme, whose settings sit under "settings". */
    public static NbtCompound regroupOwner(NbtCompound ownerTag, Settings settings, Map<String, String> renames) {
        if (!(ownerTag.get("settings") instanceof NbtCompound s)) return ownerTag;
        NbtCompound out = ownerTag.copy();
        out.put("settings", regroup(s, settings, renames));
        return out;
    }
}
