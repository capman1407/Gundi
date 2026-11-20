package com.david.gundi.mixin;

import com.david.gundi.hud.GundiChatHud;
import com.david.gundi.util.ErrorReporter;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While Gundi Chat replaces Minecraft's chat, this keeps Minecraft's chat screen for typing (commands, history,
 * tab completion all stay vanilla) but:
 * - moves its text field into the window's input bar and drops the black input background,
 * - sends clicks, scrolling and hover inside the window to Gundi Chat.
 *
 * require = 0 everywhere: if a Minecraft update renames something, that part falls back to vanilla.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    @Shadow protected TextFieldWidget chatField;

    protected ChatScreenMixin(Text title) {
        super(title);
    }

    private static final String FEATURE = "gundi-chat";
    private boolean addon$movedField;

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void addon$placeField(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            GundiChatHud chat = GundiChatHud.get();
            int[] rect = chat != null && GundiChatHud.showing() ? chat.inputRect() : null;
            if (rect != null) {
                chatField.setX(rect[0]);
                chatField.setY(rect[1]);
                chatField.setWidth(rect[2]);
                chatField.setDrawsBackground(false);
                addon$movedField = true;
            } else if (addon$movedField) {
                // Gundi Chat turned off while open: put the field back where Minecraft has it.
                chatField.setX(4);
                chatField.setY(height - 12);
                chatField.setWidth(width - 4);
                chatField.setDrawsBackground(false);
                addon$movedField = false;
            }
        } catch (Throwable e) {
            ErrorReporter.report(FEATURE, "input", e);
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"), require = 0)
    private void addon$inputBackground(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        boolean hide = false;
        try {
            hide = GundiChatHud.showing();
        } catch (Throwable ignored) {
        }
        if (!hide) context.fill(x1, y1, x2, y2, color);
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void addon$hover(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            GundiChatHud chat = GundiChatHud.get();
            if (chat == null || !GundiChatHud.showing()) return;
            Style style = chat.styleAt(mouseX, mouseY);
            if (style != null && style.getHoverEvent() != null) context.drawHoverEvent(textRenderer, style, mouseX, mouseY);
        } catch (Throwable e) {
            ErrorReporter.report(FEATURE, "hover", e);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void addon$click(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        try {
            GundiChatHud chat = GundiChatHud.get();
            if (chat == null || !GundiChatHud.showing()) return;
            double gx = click.x(), gy = click.y();
            if (!chat.contains(gx, gy)) return;
            if (click.button() == 1) {
                // right click: copy the message
                if (chat.copyAt(gx, gy)) cir.setReturnValue(true);
                return;
            }
            if (click.button() != 0) return;
            if (chat.clickControls(gx, gy)) {
                cir.setReturnValue(true);
                return;
            }
            Style style = chat.styleAt(gx, gy);
            ChatScreenInvoker self = (ChatScreenInvoker) this;
            if (style != null) self.addon$handleClickEvent(style, self.addon$shouldInsert());
            cir.setReturnValue(true);
        } catch (Throwable e) {
            ErrorReporter.report(FEATURE, "click", e);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, require = 0)
    private void addon$scroll(double mouseX, double mouseY, double horizontal, double vertical, CallbackInfoReturnable<Boolean> cir) {
        try {
            GundiChatHud chat = GundiChatHud.get();
            if (chat == null || !GundiChatHud.showing()) return;
            chat.scroll(client != null && client.isShiftPressed() ? vertical * 7 : vertical);
            cir.setReturnValue(true);
        } catch (Throwable e) {
            ErrorReporter.report(FEATURE, "scroll", e);
        }
    }
}
