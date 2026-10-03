package io.github.nussico.deserter;

import net.minecraft.ChatFormatting;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Players who disconnected while in combat. Their {@link ServerPlayer} is kept in
 * the world (vanilla removal is cancelled in the mixin) until combat ends, they die,
 * or they log back in.
 */
public final class Bodies {
	/** Scoreboard team that gives bodies their name tag prefix and red glow. */
	public static final String TEAM_NAME = "deserter_bodies";

	/** What we changed on the player, so it can be undone before they're saved. */
	private record BodyState(boolean hadGlow, boolean hadNoGravity, @Nullable String previousTeam) {
	}

	private static final Map<ServerPlayer, BodyState> BODIES = new IdentityHashMap<>();
	/** Players being kicked by the server rather than leaving themselves. */
	private static final Set<UUID> KICKED = new HashSet<>();
	/** Set while we run vanilla's disconnect logic ourselves, so the mixin lets it through. */
	private static boolean releasing;

	private Bodies() {
	}

	public static void add(ServerPlayer player) {
		ServerScoreboard scoreboard = player.level().getServer().getScoreboard();
		PlayerTeam previousTeam = scoreboard.getPlayersTeam(player.getScoreboardName());
		BODIES.put(player, new BodyState(player.hasGlowingTag(), player.isNoGravity(), previousTeam == null ? null : previousTeam.getName()));

		// Stop whatever the player was doing when they left.
		player.setLastClientInput(Input.EMPTY);
		player.xxa = 0;
		player.zza = 0;
		player.setJumping(false);
		player.setSprinting(false);
		player.setShiftKeyDown(false);

		if (Deserter.config.body.glow) player.setGlowingTag(true);
		if (!Deserter.config.body.gravity) player.setNoGravity(true);
		if (Deserter.config.body.nameTag) scoreboard.addPlayerToTeam(player.getScoreboardName(), bodyTeam(scoreboard));
	}

	public static boolean isBody(ServerPlayer player) {
		return BODIES.containsKey(player);
	}

	public static List<ServerPlayer> all() {
		return List.copyOf(BODIES.keySet());
	}

	public static boolean isReleasing() {
		return releasing;
	}

	public static void markKicked(ServerPlayer player) {
		KICKED.add(player.getUUID());
	}

	/** Returns whether the player was kicked, clearing the flag. */
	public static boolean consumeKicked(ServerPlayer player) {
		return KICKED.remove(player.getUUID());
	}

	/** Undoes our changes and runs the vanilla disconnect we cancelled, which saves and removes the player. */
	public static void release(ServerPlayer body) {
		BodyState state = BODIES.remove(body);
		if (state == null) return;
		restore(body, state);
		releasing = true;
		try {
			body.connection.onDisconnect(new DisconnectionDetails(Component.translatable("multiplayer.disconnect.generic")));
		} finally {
			releasing = false;
		}
	}

	public static void releaseAll(UUID uuid) {
		for (ServerPlayer body : all()) {
			if (body.getUUID().equals(uuid)) release(body);
		}
	}

	public static void releaseAll() {
		all().forEach(Bodies::release);
	}

	/**
	 * Cleans up a joining player who is still marked as a body, which only happens
	 * if the server crashed while their body was in the world.
	 */
	public static void cleanUpAfterCrash(ServerPlayer player) {
		ServerScoreboard scoreboard = player.level().getServer().getScoreboard();
		PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
		if (team != null && team.getName().equals(TEAM_NAME)) {
			scoreboard.removePlayerFromTeam(player.getScoreboardName(), team);
			player.setGlowingTag(false);
		}
	}

	/** Ticks bodies the way their (now closed) connection used to, and releases finished ones. */
	public static void tick(CombatManager combat, long nowTick) {
		for (ServerPlayer body : all()) {
			if (!body.isAlive() || !combat.isInCombat(body.getUUID(), nowTick)) {
				release(body);
				continue;
			}
			double x = body.getX(), y = body.getY(), z = body.getZ();
			body.xo = x;
			body.yo = y;
			body.zo = z;
			body.doTick();
			// Normally the client reports landing; without one, apply fall damage ourselves.
			body.doCheckFallDamage(body.getX() - x, body.getY() - y, body.getZ() - z, body.onGround());
		}
	}

	private static void restore(ServerPlayer body, BodyState state) {
		body.setGlowingTag(state.hadGlow());
		body.setNoGravity(state.hadNoGravity());
		ServerScoreboard scoreboard = body.level().getServer().getScoreboard();
		PlayerTeam current = scoreboard.getPlayersTeam(body.getScoreboardName());
		if (current == null || !current.getName().equals(TEAM_NAME)) return;
		scoreboard.removePlayerFromTeam(body.getScoreboardName(), current);
		PlayerTeam previous = state.previousTeam() == null ? null : scoreboard.getPlayerTeam(state.previousTeam());
		if (previous != null) scoreboard.addPlayerToTeam(body.getScoreboardName(), previous);
	}

	private static PlayerTeam bodyTeam(ServerScoreboard scoreboard) {
		PlayerTeam team = scoreboard.getPlayerTeam(TEAM_NAME);
		if (team == null) team = scoreboard.addPlayerTeam(TEAM_NAME);
		// Refreshed every time so config reloads apply.
		team.setPlayerPrefix(TextFormat.format(Deserter.config.messages.bodyNameTag));
		setRed(team);
		return team;
	}

	/** The team color also colors the glow. setColor changed signature in 26.3, so support both. */
	private static void setRed(PlayerTeam team) {
		try {
			team.setColor(ChatFormatting.RED);
		} catch (NoSuchMethodError newerVersion) {
			try {
				Object red = Class.forName("net.minecraft.world.scores.TeamColor").getField("RED").get(null);
				PlayerTeam.class.getMethod("setColor", Optional.class).invoke(team, Optional.of(red));
			} catch (ReflectiveOperationException e) {
				Deserter.LOGGER.warn("Couldn't color the body team", e);
			}
		}
	}
}
