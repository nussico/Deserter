package io.github.nussico.deserter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loaded from {@code config/deserter.json}; missing fields keep their defaults.
 * Messages support {@code &} color codes (e.g. {@code &c}) and {@code {placeholders}}.
 */
public class DeserterConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("deserter.json");

	/** How long a player stays in combat after dealing or taking PvP damage. */
	public int combatSeconds = 15;
	/** Damage from a player's tamed pets (wolves, etc.) counts as PvP. */
	public boolean petsTriggerCombat = true;
	/** Whether being kicked (by an admin, for idling, ...) during combat leaves a body. Timeouts always do. */
	public boolean kicksLeaveBody = false;
	/** Where the combat countdown is shown. Unknown values fall back to actionbar. */
	public Display display = Display.actionbar;

	public enum Display { actionbar, bossbar, none }

	public Sounds sounds = new Sounds();
	public Body body = new Body();
	public SafeZones safeZones = new SafeZones();
	public Punishments punishments = new Punishments();
	public Messages messages = new Messages();

	public static class Sounds {
		public boolean enabled = true;
		public String enterCombat = "minecraft:block.note_block.bass";
		public String leaveCombat = "minecraft:entity.experience_orb.pickup";
	}

	public static class Body {
		/** Bodies glow so everyone can spot them. */
		public boolean glow = true;
		/** Bodies get the {@code messages.bodyNameTag} prefix above their head. */
		public boolean nameTag = true;
	}

	public static class SafeZones {
		/** Dimensions where nobody is tagged, e.g. "minecraft:the_end". */
		public List<String> disabledDimensions = List.of();
		/** Boxes where nobody is tagged. Hits where either player stands in one don't start combat. */
		public List<Zone> zones = List.of();
	}

	public static class Zone {
		public String dimension = "minecraft:overworld";
		public int minX = -50, minY = -64, minZ = -50;
		public int maxX = 50, maxY = 320, maxZ = 50;

		public boolean contains(ServerPlayer player) {
			return player.level().dimension().identifier().toString().equals(dimension)
				&& player.getX() >= Math.min(minX, maxX) && player.getX() <= Math.max(minX, maxX) + 1
				&& player.getY() >= Math.min(minY, maxY) && player.getY() <= Math.max(minY, maxY) + 1
				&& player.getZ() >= Math.min(minZ, maxZ) && player.getZ() <= Math.max(minZ, maxZ) + 1;
		}
	}

	public static class Punishments {
		/** Only desertions within this many days count towards punishments. */
		public int memoryDays = 30;
		/** Each recent desertion after the first keeps the body around this much longer. */
		public int extraBodySecondsPerDesertion = 5;
		public int maxExtraBodySeconds = 60;
		/** Temp-ban players once they reach this many recent desertions. 0 disables bans. */
		public int banAfterDesertions = 0;
		public int banMinutes = 60;
	}

	public static class Messages {
		public String combatCountdown = "&cIn combat: {seconds}s - don't log out!";
		public String combatEnded = "&aYou are no longer in combat";
		/** Broadcast to everyone. Placeholders: {player}, {seconds}. Empty disables it. */
		public String deserted = "&c{player} deserted combat! Their body remains for {seconds} seconds.";
		/** Appended to the vanilla death message when a body is killed. */
		public String deathSuffix = " while deserting";
		public String bodyNameTag = "&4[Deserter] ";
		/** Placeholders: {count}. */
		public String banReason = "Deserted combat {count} times";
	}

	public static DeserterConfig load() {
		DeserterConfig config = new DeserterConfig();
		try {
			if (Files.exists(PATH)) {
				DeserterConfig loaded = GSON.fromJson(Files.readString(PATH), DeserterConfig.class);
				if (loaded != null) config = loaded;
			}
			config.sanitize();
			// Rewrite so new options show up in existing files.
			Files.writeString(PATH, GSON.toJson(config));
		} catch (Exception e) {
			Deserter.LOGGER.error("Failed to load {}, using defaults", PATH, e);
		}
		return config;
	}

	/** Fills in sections deleted from the file and clamps nonsense values. */
	private void sanitize() {
		if (sounds == null) sounds = new Sounds();
		if (body == null) body = new Body();
		if (safeZones == null) safeZones = new SafeZones();
		if (safeZones.disabledDimensions == null) safeZones.disabledDimensions = List.of();
		if (safeZones.zones == null) safeZones.zones = List.of();
		if (punishments == null) punishments = new Punishments();
		if (messages == null) messages = new Messages();
		combatSeconds = Math.max(1, combatSeconds);
		if (display == null) display = Display.actionbar;
	}
}
