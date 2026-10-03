package io.github.nussico.deserter.mixin;

import io.github.nussico.deserter.Bodies;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-initiated disconnects (kicks, bans, idle timeout) go through here, unlike a
 * player quitting. Timeouts also do, but those are treated as quitting so pulling
 * the network cable doesn't dodge the body.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public class ServerCommonPacketListenerImplMixin {
	@Inject(method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V", at = @At("HEAD"))
	private void deserter$markKicked(DisconnectionDetails details, CallbackInfo ci) {
		if ((Object) this instanceof ServerGamePacketListenerImpl game
			&& !(details.reason().getContents() instanceof TranslatableContents translatable && translatable.getKey().equals("disconnect.timeout"))) {
			Bodies.markKicked(game.player);
		}
	}
}
