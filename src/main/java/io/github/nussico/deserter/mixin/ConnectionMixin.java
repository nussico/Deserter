package io.github.nussico.deserter.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla queues packets sent to a closed connection forever. Bodies keep receiving
 * packets after their client is gone, so drop them instead of leaking memory.
 */
@Mixin(Connection.class)
public class ConnectionMixin {
	@Shadow
	private Channel channel;

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
	private void deserter$dropIfClosed(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
		if (this.channel != null && !this.channel.isOpen()) {
			ci.cancel();
		}
	}
}
