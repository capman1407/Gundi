package com.david.gundi.mixin;

import com.david.gundi.hud.GundiChatHud;
import com.david.gundi.hud.chat.ChatTagger;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips Minecraft's chat while the Gundi Chat window replaces it: both the drawing and the invisible
 * hit-test pass Minecraft runs for clicks, so nothing can be clicked where the old chat used to be.
 *
 * require = 0: if a Minecraft update renames these, vanilla chat just keeps showing.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V",
        at = @At("HEAD"), cancellable = true, require = 0)
    private void addon$hideVanillaChat(DrawContext context, TextRenderer textRenderer, int currentTick, int mouseX, int mouseY,
                                       boolean focused, boolean insert, CallbackInfo ci) {
        try {
            if (GundiChatHud.showing()) ci.cancel();
        } catch (Throwable ignored) {
            // worst case vanilla chat shows
        }
    }

    /** Tags each message (player / whisper / system, and its sender) as Minecraft adds it. */
    @Inject(method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", at = @At("HEAD"), require = 0)
    private void addon$tagMessage(ChatHudLine line, CallbackInfo ci) {
        try {
            ChatTagger.onAdd(line);
        } catch (Throwable ignored) {
            // untagged lines are classified from their text later
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/font/DrawnTextConsumer;IIZ)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void addon$skipVanillaHitTest(DrawnTextConsumer consumer, int windowHeight, int currentTick, boolean expanded, CallbackInfo ci) {
        try {
            if (GundiChatHud.showing()) ci.cancel();
        } catch (Throwable ignored) {
        }
    }
}
