package io.github.nussico.deserter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Combat tags keyed by UUID (not entity), so a tag survives respawns and
 * carries over when a deserter's body is replaced by their rejoining player.
 */
public final class CombatManager {
	private final Map<UUID, Long> combatUntil = new HashMap<>();
	private final CombatDisplay display = new CombatDisplay();

	public static long now(MinecraftServer server) {
		return server.getTickCount();
	}

	public static long seconds(long ticks) {
		return (ticks + 19) / 20;
	}

	public boolean isInCombat(UUID uuid, long nowTick) {
		return remainingTicks(uuid, nowTick) > 0;
	}

	/** Remaining combat ticks, or 0 if not in combat. */
	public long remainingTicks(UUID uuid, long nowTick) {
		Long until = combatUntil.get(uuid);
		return until == null ? 0 : Math.max(0, until - nowTick);
	}

	/** Puts the player in combat for at least {@code ticks}; never shortens a longer timer. */
	public void tag(ServerPlayer player, long nowTick, long ticks) {
		UUID uuid = player.getUUID();
		boolean isNew = !isInCombat(uuid, nowTick);
		combatUntil.merge(uuid, nowTick + ticks, Math::max);
		display.update(player, remainingTicks(uuid, nowTick), true);
		if (isNew) playSound(player, Deserter.config.sounds.enterCombat);
	}

	/** Extends a tag without any feedback; used to keep repeat deserters' bodies around longer. */
	public void extend(UUID uuid, long extraTicks) {
		combatUntil.computeIfPresent(uuid, (id, until) -> until + extraTicks);
	}

	/** Ends combat for the player, telling them if they're online. */
	public void untag(MinecraftServer server, UUID uuid) {
		if (combatUntil.remove(uuid) == null) return;
		ServerPlayer player = server.getPlayerList().getPlayer(uuid);
		if (player != null && !Bodies.isBody(player)) {
			display.end(player);
			playSound(player, Deserter.config.sounds.leaveCombat);
		} else {
			display.clear(uuid);
		}
	}

	public void tick(MinecraftServer server) {
		long now = now(server);
		List<UUID> expired = new ArrayList<>();
		combatUntil.forEach((uuid, until) -> {
			long remaining = until - now;
			if (remaining <= 0) {
				expired.add(uuid);
				return;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			if (player != null && !Bodies.isBody(player)) display.update(player, remaining, false);
		});
		expired.forEach(uuid -> untag(server, uuid));
	}

	public List<UUID> taggedPlayers() {
		return List.copyOf(combatUntil.keySet());
	}

	/** Called on config reload, in case the display mode changed. */
	public void resetDisplay() {
		display.clearAll();
	}

	public void clear() {
		combatUntil.clear();
		display.clearAll();
	}

	private static void playSound(ServerPlayer player, String soundId) {
		Identifier id = Deserter.config.sounds.enabled ? Identifier.tryParse(soundId) : null;
		if (id == null) return;
		BuiltInRegistries.SOUND_EVENT.get(id).ifPresent(sound -> player.connection.send(new ClientboundSoundPacket(
			sound, SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 1f, 1f, player.getRandom().nextLong())));
	}
}
