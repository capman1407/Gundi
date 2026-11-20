package com.david.gundi.mixin;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets Gundi Chat run a clicked message's click event exactly the way Minecraft's chat does. */
@Mixin(ChatScreen.class)
public interface ChatScreenInvoker {
    @Invoker("handleClickEvent")
    boolean addon$handleClickEvent(Style style, boolean insert);

    @Invoker("shouldInsert")
    boolean addon$shouldInsert();
}
