package com.david.gundi.modules;

import com.david.gundi.Gundi;
import com.david.gundi.util.ErrorReporter;
import com.david.gundi.util.SettingsCompat;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.nbt.NbtCompound;

import java.util.Map;

/**
 * Base for every Gundi module: puts it in the Gundi category, logs when it's turned on/off, and loads saved
 * settings through SettingsCompat so settings moved to another group keep their saved values.
 * Modules that override onActivate/onDeactivate call super first.
 */
public abstract class GundiModule extends Module {
    protected GundiModule(String name, String description) {
        super(Gundi.CATEGORY, name, description);
    }

    @Override
    public void onActivate() {
        ErrorReporter.step(name, "enabled");
    }

    @Override
    public void onDeactivate() {
        ErrorReporter.step(name, "disabled");
    }

    @Override
    public Module fromTag(NbtCompound tag) {
        return super.fromTag(SettingsCompat.regroupOwner(tag, settings, Map.of()));
    }
}
