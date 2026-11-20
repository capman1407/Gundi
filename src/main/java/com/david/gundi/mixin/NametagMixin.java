package com.david.gundi.mixin;

import com.david.gundi.modules.AppleNametags;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides Minecraft's own name text above other players while Apple Nametags is on (and its
 * hide-vanilla-names setting is on), so it doesn't overlap the card. Same spot Meteor's own Nametags uses.
 *
 * require = 0: if a Minecraft update renames this method, the mixin just does nothing instead of crashing.
 */
@Mixin(EntityRenderer.class)
public abstract class NametagMixin {
    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true, require = 0)
    private void addon$hideVanillaName(Entity entity, CallbackInfoReturnable<Text> cir) {
        try {
            if (AppleNametags.hidesVanillaName(entity)) cir.setReturnValue(null);
        } catch (Throwable ignored) {
            // Never let a name check crash entity rendering
        }
    }
}
