package com.david.gundi.mixin;

import com.david.gundi.gui.BackdropRenderer;
import com.david.gundi.gui.GundiTheme;
import com.david.gundi.gui.ThemeStyles;
import com.david.gundi.util.ErrorReporter;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.systems.hud.screens.HudEditorScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two hooks into Meteor's GUI screens while a Gundi theme is selected:
 *
 * 1. Slower screen fade, so close animations have time to play (Meteor's own fade is ~0.07s).
 *    In WidgetScreen.renderCustom Meteor does  animProgress += (delta / 20 * 14) * (closing ? -1 : 1)
 *    and we replace that 14.
 *
 * 2. The animated backdrop. Right after Meteor sets the screen's fade alpha (the first setAlpha call) and
 *    before it draws the windows, we draw the theme's backdrop, so it sits behind everything and fades with the GUI.
 *    Only Meteor-owned types are in the target, so remap = false is right here too.
 *
 * remap = false: WidgetScreen is Meteor's class, not Minecraft's, so its names never change.
 * require = 0: if a future Meteor version changes this code, the hook just does nothing instead of crashing.
 */
@Mixin(value = WidgetScreen.class, remap = false)
public abstract class WidgetScreenMixin {
    @Shadow
    @Final
    private static GuiRenderer RENDERER;

    @Shadow
    @Final
    protected GuiTheme theme;

    @ModifyConstant(method = "renderCustom", constant = @Constant(floatValue = 14.0F), require = 0)
    private float addon$screenFadeSpeed(float original) {
        if (GuiThemes.get() instanceof GundiTheme gundi) return gundi.screenFadeSpeed();
        return original;
    }

    @Inject(method = "renderCustom", at = @At(value = "INVOKE",
        target = "Lmeteordevelopment/meteorclient/gui/renderer/GuiRenderer;setAlpha(D)V", ordinal = 0, shift = At.Shift.AFTER),
        require = 0)
    private void addon$backdrop(CallbackInfo ci) {
        try {
            if (!(theme instanceof GundiTheme gundi)) return;
            if ((Object) this instanceof HudEditorScreen) return; // keep the HUD editor clear
            if (gundi.backdrop.get() == ThemeStyles.Backdrop.None && !gundi.vignette.get() && !gundi.cursorGlow.get()) return;
            BackdropRenderer.render(RENDERER, gundi);
        } catch (Throwable e) {
            ErrorReporter.report("gui-backdrop", "mixin", e);
        }
    }
}
