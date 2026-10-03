package io.github.nussico.deserter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Per-player desertion records, stored in {@code <world>/deserter/stats.json}. */
public final class DeserterStats {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	public static class Entry {
		public String name = "";
		/** Epoch millis of every desertion. */
		public List<Long> desertions = new ArrayList<>();
		/** How often this player's body was killed. */
		public int bodyDeaths;
		/** How many deserters' bodies this player killed. */
		public int desertersKilled;

		public int recentDesertions(int days) {
			long since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
			return (int) desertions.stream().filter(time -> time >= since).count();
		}
	}

	private Map<String, Entry> entries = new HashMap<>();
	private @Nullable Path dir;

	public void load(MinecraftServer server) {
		dir = server.getWorldPath(LevelResource.ROOT).resolve("deserter");
		entries = new HashMap<>();
		Path file = dir.resolve("stats.json");
		try {
			if (Files.exists(file)) {
				Map<String, Entry> loaded = GSON.fromJson(Files.readString(file), new TypeToken<Map<String, Entry>>() {}.getType());
				if (loaded != null) entries = loaded;
			}
		} catch (Exception e) {
			Deserter.LOGGER.error("Failed to load {}", file, e);
		}
	}

	public void save() {
		if (dir == null) return;
		try {
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("stats.json"), GSON.toJson(entries));
		} catch (IOException e) {
			Deserter.LOGGER.error("Failed to save Deserter stats", e);
		}
	}

	public Entry get(ServerPlayer player) {
		Entry entry = entries.computeIfAbsent(player.getUUID().toString(), uuid -> new Entry());
		entry.name = player.getPlainTextName();
		return entry;
	}

	public @Nullable Entry get(UUID uuid) {
		return entries.get(uuid.toString());
	}

	public @Nullable Entry findByName(String name) {
		return entries.values().stream().filter(entry -> entry.name.equalsIgnoreCase(name)).findFirst().orElse(null);
	}

	public List<String> names() {
		return entries.values().stream().map(entry -> entry.name).sorted().toList();
	}

	/** Records a desertion and returns how many happened within {@code memoryDays}, including this one. */
	public int recordDesertion(ServerPlayer player, int memoryDays) {
		Entry entry = get(player);
		entry.desertions.add(System.currentTimeMillis());
		save();
		return entry.recentDesertions(memoryDays);
	}

	/** Appends a line to {@code <world>/deserter/desertions.log}. */
	public void log(String message) {
		Deserter.LOGGER.info(message);
		if (dir == null) return;
		try {
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("desertions.log"), "[" + LocalDateTime.now().format(LOG_TIME) + "] " + message + System.lineSeparator(),
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			Deserter.LOGGER.error("Failed to write desertion log", e);
		}
	}
}
