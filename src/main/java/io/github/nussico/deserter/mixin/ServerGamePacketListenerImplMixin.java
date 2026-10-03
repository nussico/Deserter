package io.github.nussico.deserter.mixin;

import io.github.nussico.deserter.Bodies;
import io.github.nussico.deserter.Deserter;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps a player who disconnects during combat in the world instead of removing them. */
@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
	private void deserter$keepBody(DisconnectionDetails details, CallbackInfo ci) {
		if (Bodies.isReleasing()) return;
		// A body's connection is already closed; ignore repeat disconnects (e.g. kicks) until we release it.
		if (Bodies.isBody(this.player) || Deserter.onDisconnect(this.player)) {
			ci.cancel();
		}
	}
}
