package io.github.nussico.deserter;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shows the combat countdown in the action bar or a boss bar, depending on the config. */
final class CombatDisplay {
	private final Map<UUID, ServerBossEvent> bossBars = new HashMap<>();

	/**
	 * @param force update the text now instead of on the next whole second
	 */
	void update(ServerPlayer player, long remainingTicks, boolean force) {
		boolean newSecond = force || remainingTicks % 20 == 0;
		switch (Deserter.config.display) {
			case actionbar -> {
				// Refreshed every second; action bar text fades after a few seconds.
				if (newSecond) player.sendSystemMessage(countdown(remainingTicks), true);
			}
			case bossbar -> {
				ServerBossEvent bar = bossBars.computeIfAbsent(player.getUUID(), uuid -> new ServerBossEvent(
					UUID.randomUUID(), countdown(remainingTicks), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS));
				// Respawning or rejoining creates a new ServerPlayer object.
				if (!bar.getPlayers().contains(player)) {
					bar.removeAllPlayers();
					bar.addPlayer(player);
				}
				bar.setProgress(Math.min(1f, remainingTicks / (Deserter.config.combatSeconds * 20f)));
				if (newSecond) bar.setName(countdown(remainingTicks));
			}
		}
	}

	void end(ServerPlayer player) {
		clear(player.getUUID());
		if (Deserter.config.display != DeserterConfig.Display.none) {
			player.sendSystemMessage(TextFormat.format(Deserter.config.messages.combatEnded), true);
		}
	}

	void clear(UUID uuid) {
		ServerBossEvent bar = bossBars.remove(uuid);
		if (bar != null) bar.removeAllPlayers();
	}

	void clearAll() {
		bossBars.values().forEach(ServerBossEvent::removeAllPlayers);
		bossBars.clear();
	}

	private static Component countdown(long remainingTicks) {
		return TextFormat.format(Deserter.config.messages.combatCountdown, "seconds", CombatManager.seconds(remainingTicks));
	}
}
