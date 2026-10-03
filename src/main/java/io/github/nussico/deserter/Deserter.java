package io.github.nussico.deserter;

import io.github.nussico.deserter.api.DeserterEvents;
import io.github.nussico.deserter.compat.PermissionCompat;
import io.github.nussico.deserter.compat.PlaceholderCompat;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class Deserter implements ModInitializer {
	public static final String MOD_ID = "deserter";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Players with this permission (or op level 2 when no permission mod is installed) are never tagged. */
	public static final Identifier BYPASS_PERMISSION = Identifier.fromNamespaceAndPath(MOD_ID, "bypass");

	public static DeserterConfig config;
	public static final CombatManager COMBAT = new CombatManager();
	public static final DeserterStats STATS = new DeserterStats();

	private static boolean stopping;

	@Override
	public void onInitialize() {
		config = DeserterConfig.load();

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			stopping = false;
			STATS.load(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// Everyone is disconnected on shutdown; that's not desertion.
			stopping = true;
			Bodies.releaseAll();
			COMBAT.clear();
			STATS.save();
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register(Deserter::onDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(Deserter::onDeath);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player, server));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Bodies.tick(COMBAT, CombatManager.now(server));
			COMBAT.tick(server);
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> DeserterCommands.register(dispatcher));

		if (FabricLoader.getInstance().isModLoaded("placeholder-api")) {
			PlaceholderCompat.register();
		}
	}

	private static void onDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (!(entity instanceof ServerPlayer victim)) return;
		ServerPlayer attacker = resolveAttacker(source);
		if (attacker == null || attacker == victim) return;
		if (isInSafeZone(victim) || isInSafeZone(attacker)) return;

		long now = CombatManager.now(victim.level().getServer());
		tag(victim, now);
		tag(attacker, now);
	}

	/** The player responsible for the damage: direct hits, projectiles, and optionally tamed pets. */
	private static ServerPlayer resolveAttacker(DamageSource source) {
		Entity cause = source.getEntity();
		if (cause instanceof ServerPlayer player) return player;
		if (config.petsTriggerCombat && cause instanceof OwnableEntity pet && pet.getOwner() instanceof ServerPlayer owner) {
			return owner;
		}
		return null;
	}

	private static boolean isInSafeZone(ServerPlayer player) {
		String dimension = player.level().dimension().identifier().toString();
		return config.safeZones.disabledDimensions.contains(dimension)
			|| config.safeZones.zones.stream().anyMatch(zone -> zone.contains(player));
	}

	private static void tag(ServerPlayer player, long now) {
		if (player.isCreative() || player.isSpectator() || canBypass(player)) return;
		COMBAT.tag(player, now, config.combatSeconds * 20L);
	}

	public static boolean canBypass(ServerPlayer player) {
		return PermissionCompat.check(player, player.permissions(), BYPASS_PERMISSION, PermissionLevel.GAMEMASTERS);
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer player)) return;
		if (Bodies.isBody(player)) {
			STATS.get(player).bodyDeaths++;
			ServerPlayer killer = resolveAttacker(source);
			if (killer != null) STATS.get(killer).desertersKilled++;
			STATS.save();
			STATS.log(player.getPlainTextName() + "'s body was killed" + (killer != null ? " by " + killer.getPlainTextName() : ""));
		}
		COMBAT.untag(player.level().getServer(), player.getUUID());
	}

	/**
	 * Called from the disconnect mixin. Returns true if the player deserted combat and
	 * should be kept in the world as a body instead of being removed.
	 */
	public static boolean onDisconnect(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		boolean kicked = Bodies.consumeKicked(player);
		if (stopping || !server.isRunning() || !player.isAlive()) return false;
		// The LAN host leaving must still shut the integrated server down.
		if (server.isSingleplayerOwner(player.nameAndId())) return false;
		if (kicked && !config.kicksLeaveBody) return false;
		UUID uuid = player.getUUID();
		long now = CombatManager.now(server);
		if (!COMBAT.isInCombat(uuid, now)) return false;

		DeserterConfig.Punishments punishments = config.punishments;
		int recent = STATS.recordDesertion(player, punishments.memoryDays);
		int extraSeconds = Math.min(punishments.maxExtraBodySeconds, Math.max(0, recent - 1) * punishments.extraBodySecondsPerDesertion);
		if (extraSeconds > 0) COMBAT.extend(uuid, extraSeconds * 20L);
		long seconds = CombatManager.seconds(COMBAT.remainingTicks(uuid, now));

		Bodies.add(player);
		STATS.log(player.getPlainTextName() + " deserted combat (" + recent + " in the last " + punishments.memoryDays + " days), body stays for " + seconds + "s");
		if (!config.messages.deserted.isEmpty()) {
			server.getPlayerList().broadcastSystemMessage(
				TextFormat.format(config.messages.deserted, "player", player.getPlainTextName(), "seconds", seconds), false);
		}
		if (punishments.banAfterDesertions > 0 && recent >= punishments.banAfterDesertions) {
			ban(server, player, recent);
		}
		DeserterEvents.DESERTED.invoker().onDeserted(player, recent);
		return true;
	}

	private static void ban(MinecraftServer server, ServerPlayer player, int recent) {
		Date expires = new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(config.punishments.banMinutes));
		String reason = ChatFormatting.stripFormatting(TextFormat.format(config.messages.banReason, "count", recent).getString());
		server.getPlayerList().getBans().add(new UserBanListEntry(player.nameAndId(), null, "Deserter", expires, reason));
		STATS.log(player.getPlainTextName() + " was banned for " + config.punishments.banMinutes + " minutes: " + reason);
	}

	private static void onJoin(ServerPlayer player, MinecraftServer server) {
		Bodies.cleanUpAfterCrash(player);
		long now = CombatManager.now(server);
		long remaining = COMBAT.remainingTicks(player.getUUID(), now);
		// Rejoined before their body's timer ran out: show the countdown again.
		if (remaining > 0) COMBAT.tag(player, now, remaining);
	}
}
