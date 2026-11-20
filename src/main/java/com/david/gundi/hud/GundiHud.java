package com.david.gundi.hud;

import com.david.gundi.util.SettingsCompat;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import net.minecraft.nbt.NbtCompound;

import java.util.Map;

/**
 * Base for every Gundi HUD element. Loads saved settings through SettingsCompat, so settings that were
 * moved to another group (or renamed, see renamedSettings) keep their saved values.
 */
public abstract class GundiHud extends HudElement {
    protected GundiHud(HudElementInfo<?> info) {
        super(info);
    }

    /** "OldGroup/old-name" -> "new-name" for settings that were renamed. */
    protected Map<String, String> renamedSettings() {
        return Map.of();
    }

    @Override
    public HudElement fromTag(NbtCompound tag) {
        return super.fromTag(SettingsCompat.regroupOwner(tag, settings, renamedSettings()));
    }
}
