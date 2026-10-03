package io.github.nussico.deserter;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.nussico.deserter.compat.PermissionCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * {@code /deserter} admin commands. Each subcommand has a permission node
 * ({@code deserter.command.<name>}) that falls back to an op level.
 */
final class DeserterCommands {
	private DeserterCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("deserter")
			.requires(source -> permission("reload", PermissionLevel.ADMINS).test(source)
				|| permission("tag", PermissionLevel.GAMEMASTERS).test(source)
				|| permission("list", PermissionLevel.MODERATORS).test(source)
				|| permission("stats", PermissionLevel.MODERATORS).test(source))
			.then(Commands.literal("reload")
				.requires(permission("reload", PermissionLevel.ADMINS))
				.executes(DeserterCommands::reload))
			.then(Commands.literal("tag")
				.requires(permission("tag", PermissionLevel.GAMEMASTERS))
				.then(Commands.argument("targets", EntityArgument.players())
					.executes(ctx -> tag(ctx, Deserter.config.combatSeconds))
					.then(Commands.argument("seconds", IntegerArgumentType.integer(1))
						.executes(ctx -> tag(ctx, IntegerArgumentType.getInteger(ctx, "seconds"))))))
			.then(Commands.literal("untag")
				.requires(permission("tag", PermissionLevel.GAMEMASTERS))
				.then(Commands.argument("targets", EntityArgument.players())
					.executes(DeserterCommands::untag)))
			.then(Commands.literal("list")
				.requires(permission("list", PermissionLevel.MODERATORS))
				.executes(DeserterCommands::list))
			.then(Commands.literal("stats")
				.requires(permission("stats", PermissionLevel.MODERATORS))
				.then(Commands.argument("player", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Deserter.STATS.names(), builder))
					.executes(DeserterCommands::stats))));
	}

	private static Predicate<CommandSourceStack> permission(String node, PermissionLevel fallback) {
		Identifier id = Identifier.fromNamespaceAndPath(Deserter.MOD_ID, "command." + node);
		return source -> PermissionCompat.check(source, source.permissions(), id, fallback);
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		Deserter.config = DeserterConfig.load();
		Deserter.COMBAT.resetDisplay();
		ctx.getSource().sendSuccess(() -> Component.literal("Deserter config reloaded."), true);
		return 1;
	}

	private static int tag(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
		long now = CombatManager.now(ctx.getSource().getServer());
		for (ServerPlayer player : targets) {
			Deserter.COMBAT.tag(player, now, seconds * 20L);
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Put " + targets.size() + " player(s) in combat for " + seconds + "s."), true);
		return targets.size();
	}

	private static int untag(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
		for (ServerPlayer player : targets) {
			Deserter.COMBAT.untag(ctx.getSource().getServer(), player.getUUID());
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Took " + targets.size() + " player(s) out of combat."), true);
		return targets.size();
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		long now = CombatManager.now(server);
		var tagged = Deserter.COMBAT.taggedPlayers();
		if (tagged.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("Nobody is in combat.").withStyle(ChatFormatting.GREEN), false);
			return 0;
		}
		MutableComponent text = Component.literal("In combat (" + tagged.size() + "):").withStyle(ChatFormatting.GOLD);
		for (UUID uuid : tagged) {
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			String name = player != null ? player.getPlainTextName() : uuid.toString();
			boolean body = player != null && Bodies.isBody(player);
			text.append(Component.literal("\n- " + name + ": " + CombatManager.seconds(Deserter.COMBAT.remainingTicks(uuid, now)) + "s")
				.withStyle(ChatFormatting.WHITE));
			if (body) text.append(Component.literal(" [body]").withStyle(ChatFormatting.RED));
		}
		ctx.getSource().sendSuccess(() -> text, false);
		return tagged.size();
	}

	private static int stats(CommandContext<CommandSourceStack> ctx) {
		String name = StringArgumentType.getString(ctx, "player");
		DeserterStats.Entry entry = Deserter.STATS.findByName(name);
		if (entry == null) {
			ctx.getSource().sendFailure(Component.literal(name + " has never deserted or killed a deserter."));
			return 0;
		}
		int memoryDays = Deserter.config.punishments.memoryDays;
		ctx.getSource().sendSuccess(() -> Component.literal("Deserter stats for " + entry.name).withStyle(ChatFormatting.GOLD)
			.append(Component.literal(
				"\nDesertions: " + entry.desertions.size() + " total, " + entry.recentDesertions(memoryDays) + " in the last " + memoryDays + " days"
					+ "\nBody killed: " + entry.bodyDeaths + " times"
					+ "\nDeserters killed: " + entry.desertersKilled).withStyle(ChatFormatting.WHITE)), false);
		return 1;
	}
}
