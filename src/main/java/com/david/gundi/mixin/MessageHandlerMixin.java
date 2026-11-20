package com.david.gundi.mixin;

import com.david.gundi.hud.chat.ChatTagger;
import com.david.gundi.hud.chat.ChatTagger.Kind;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;
import java.util.UUID;

/**
 * Tells Gundi Chat what kind of message Minecraft is about to add to chat and who sent it:
 * real chat packets carry the sender and the message type (chat, whisper, ...), server text is classified
 * by ChatTagger. The answer is held in ChatTagger.pending only while the message is being added.
 *
 * require = 0: if a Minecraft update renames these, messages are classified from their text alone.
 */
@Mixin(MessageHandler.class)
public abstract class MessageHandlerMixin {
    @Inject(method = "processChatMessageInternal", at = @At("HEAD"), require = 0)
    private void addon$tagChat(MessageType.Parameters params, SignedMessage message, Text decorated, GameProfile sender,
                               boolean onlyShowSecureChat, Instant receptionTimestamp, CallbackInfoReturnable<Boolean> cir) {
        try {
            ChatTagger.pending = new ChatTagger.Pending(kindOf(params), sender.name(), sender.id(), isOutgoing(params));
        } catch (Throwable ignored) {
            ChatTagger.pending = null;
        }
    }

    @Inject(method = "processChatMessageInternal", at = @At("RETURN"), require = 0)
    private void addon$untagChat(MessageType.Parameters params, SignedMessage message, Text decorated, GameProfile sender,
                                 boolean onlyShowSecureChat, Instant receptionTimestamp, CallbackInfoReturnable<Boolean> cir) {
        ChatTagger.pending = null;
    }

    @Inject(method = "onUnverifiedMessage", at = @At("HEAD"), require = 0)
    private void addon$tagUnverified(UUID sender, MessageSignatureData signature, MessageType.Parameters params, CallbackInfo ci) {
        try {
            ChatTagger.pending = new ChatTagger.Pending(kindOf(params), params.name().getString(), sender, isOutgoing(params));
        } catch (Throwable ignored) {
            ChatTagger.pending = null;
        }
    }

    @Inject(method = "onUnverifiedMessage", at = @At("RETURN"), require = 0)
    private void addon$untagUnverified(UUID sender, MessageSignatureData signature, MessageType.Parameters params, CallbackInfo ci) {
        ChatTagger.pending = null;
    }

    @Inject(method = "onProfilelessMessage", at = @At("HEAD"), require = 0)
    private void addon$tagProfileless(Text content, MessageType.Parameters params, CallbackInfo ci) {
        try {
            ChatTagger.pending = new ChatTagger.Pending(kindOf(params), params.name().getString(), null, isOutgoing(params));
        } catch (Throwable ignored) {
            ChatTagger.pending = null;
        }
    }

    @Inject(method = "onProfilelessMessage", at = @At("RETURN"), require = 0)
    private void addon$untagProfileless(Text content, MessageType.Parameters params, CallbackInfo ci) {
        ChatTagger.pending = null;
    }

    @Inject(method = "onGameMessage", at = @At("HEAD"), require = 0)
    private void addon$tagGame(Text message, boolean overlay, CallbackInfo ci) {
        try {
            ChatTagger.pending = overlay ? null : ChatTagger.classify(message);
        } catch (Throwable ignored) {
            ChatTagger.pending = null;
        }
    }

    @Inject(method = "onGameMessage", at = @At("RETURN"), require = 0)
    private void addon$untagGame(Text message, boolean overlay, CallbackInfo ci) {
        ChatTagger.pending = null;
    }

    private static Kind kindOf(MessageType.Parameters params) {
        var key = params.type().getKey().orElse(null);
        if (key == null) return Kind.PLAYER;
        if (key == MessageType.MSG_COMMAND_INCOMING || key == MessageType.MSG_COMMAND_OUTGOING
            || key == MessageType.TEAM_MSG_COMMAND_INCOMING || key == MessageType.TEAM_MSG_COMMAND_OUTGOING) return Kind.WHISPER;
        return Kind.PLAYER;
    }

    private static boolean isOutgoing(MessageType.Parameters params) {
        var key = params.type().getKey().orElse(null);
        return key == MessageType.MSG_COMMAND_OUTGOING || key == MessageType.TEAM_MSG_COMMAND_OUTGOING;
    }
}
