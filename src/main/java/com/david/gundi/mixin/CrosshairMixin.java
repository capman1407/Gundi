package com.david.gundi.mixin;

import com.david.gundi.modules.Crosshair;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips Minecraft's own crosshair (and its attack indicator) while the Crosshair module draws its own.
 * With the module off, or its style set to Vanilla, nothing changes.
 *
 * require = 0: if a Minecraft update renames this method, the vanilla crosshair just stays instead of crashing.
 */
@Mixin(InGameHud.class)
public abstract class CrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void addon$customCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        try {
            if (Crosshair.replacesVanilla()) ci.cancel();
        } catch (Throwable ignored) {
            // Never let this break the HUD: worst case the vanilla crosshair shows
        }
    }
}
