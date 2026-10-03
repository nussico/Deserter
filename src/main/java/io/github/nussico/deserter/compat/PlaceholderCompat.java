package io.github.nussico.deserter.compat;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import io.github.nussico.deserter.CombatManager;
import io.github.nussico.deserter.Deserter;
import io.github.nussico.deserter.DeserterStats;
import net.minecraft.resources.Identifier;

/**
 * Placeholders for Text Placeholder API. Only loaded when that mod is installed:
 * {@code %deserter:combat_time%}, {@code %deserter:in_combat%}, {@code %deserter:desertions%}.
 */
public final class PlaceholderCompat {
	private PlaceholderCompat() {
	}

	public static void register() {
		Placeholders.registerServer(id("combat_time"), (ctx, arg) -> {
			if (!ctx.hasServerPlayer()) return PlaceholderResult.invalid("No player!");
			long remaining = Deserter.COMBAT.remainingTicks(ctx.serverPlayer().getUUID(), CombatManager.now(ctx.server()));
			return PlaceholderResult.value(String.valueOf(CombatManager.seconds(remaining)));
		});
		Placeholders.registerServer(id("in_combat"), (ctx, arg) -> {
			if (!ctx.hasServerPlayer()) return PlaceholderResult.invalid("No player!");
			return PlaceholderResult.value(String.valueOf(Deserter.COMBAT.isInCombat(ctx.serverPlayer().getUUID(), CombatManager.now(ctx.server()))));
		});
		Placeholders.registerServer(id("desertions"), (ctx, arg) -> {
			if (!ctx.hasServerPlayer()) return PlaceholderResult.invalid("No player!");
			DeserterStats.Entry entry = Deserter.STATS.get(ctx.serverPlayer().getUUID());
			return PlaceholderResult.value(String.valueOf(entry == null ? 0 : entry.desertions.size()));
		});
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(Deserter.MOD_ID, path);
	}
}
