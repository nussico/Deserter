package io.github.nussico.deserter.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/** Events other mods can listen to. Fired on the server thread. */
public final class DeserterEvents {
	private DeserterEvents() {
	}

	/** Fired when a player disconnects during combat and leaves their body behind. */
	public static final Event<Deserted> DESERTED = EventFactory.createArrayBacked(Deserted.class, listeners -> (body, recentDesertions) -> {
		for (Deserted listener : listeners) listener.onDeserted(body, recentDesertions);
	});

	@FunctionalInterface
	public interface Deserted {
		/**
		 * @param body             the player entity left in the world
		 * @param recentDesertions how often they deserted within {@code punishments.memoryDays}, including now
		 */
		void onDeserted(ServerPlayer body, int recentDesertions);
	}
}
