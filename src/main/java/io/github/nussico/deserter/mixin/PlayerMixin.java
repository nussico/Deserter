package io.github.nussico.deserter.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.nussico.deserter.Bodies;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * After knocking back a player, vanilla sends the knockback to their client and resets
 * it on the server. Bodies have no client, so keep the knockback on the server instead.
 */
@Mixin(Player.class)
public class PlayerMixin {
	@WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
	private boolean deserter$keepBodyKnockback(Entity target, Vec3 oldMovement) {
		return !(target instanceof ServerPlayer player && Bodies.isBody(player));
	}
}
