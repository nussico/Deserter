package io.github.nussico.deserter.mixin;

import io.github.nussico.deserter.Bodies;
import io.github.nussico.deserter.Deserter;
import io.github.nussico.deserter.TextFormat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** "B was slain by A" becomes "B was slain by A while deserting" when B's body is killed. */
@Mixin(CombatTracker.class)
public class CombatTrackerMixin {
	@Shadow
	@Final
	private LivingEntity mob;

	@Inject(method = "getDeathMessage", at = @At("RETURN"), cancellable = true)
	private void deserter$markDeserter(CallbackInfoReturnable<Component> cir) {
		if (this.mob instanceof ServerPlayer player && Bodies.isBody(player) && !Deserter.config.messages.deathSuffix.isEmpty()) {
			cir.setReturnValue(Component.empty().append(cir.getReturnValue()).append(TextFormat.format(Deserter.config.messages.deathSuffix)));
		}
	}
}
