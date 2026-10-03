package io.github.nussico.deserter.mixin;

import io.github.nussico.deserter.Bodies;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * When a deserter logs back in, remove their body first (saving its current state)
 * so they rejoin exactly where the body was, still in combat. Without this, vanilla
 * would wait on the body's dead connection until the login timed out.
 */
@Mixin(PlayerList.class)
public class PlayerListMixin {
	@Inject(method = "disconnectAllPlayersWithProfile", at = @At("HEAD"))
	private void deserter$releaseBody(UUID playerId, CallbackInfoReturnable<Boolean> cir) {
		Bodies.releaseAll(playerId);
	}
}
