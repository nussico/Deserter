package io.github.nussico.deserter.test;

import io.github.nussico.deserter.Bodies;
import io.github.nussico.deserter.CombatManager;
import io.github.nussico.deserter.Deserter;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;

// makeMockServerPlayerInLevel is deprecated, but its replacement has no connection, so it can't disconnect into a body.
@SuppressWarnings("removal")
public class DeserterGameTests {
	@GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
	public void bodyFalls(GameTestHelper helper) {
		checkBody(helper, true);
	}

	@GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
	public void bodyFloatsWithoutGravity(GameTestHelper helper) {
		checkBody(helper, false);
	}

	/** Runs the permission check, which uses Fabric's permission API on 26.1.2+ and op levels before. */
	@GameTest(structure = "fabric-gametest-api-v1:empty")
	public void nonOpCannotBypass(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(!Deserter.canBypass(player), "A non-op player must not bypass combat");
		helper.succeed();
	}

	@GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 100)
	public void bodyTakesKnockback(GameTestHelper helper) {
		ServerPlayer body = leaveBody(helper);
		// Mock players are creative; make the body hittable.
		body.getAbilities().invulnerable = false;
		ServerPlayer attacker = helper.makeMockServerPlayerInLevel();
		attacker.setPos(body.getX() - 1, body.getY(), body.getZ());
		double startX = body.getX();

		attacker.attack(body);
		helper.runAfterDelay(5, () -> {
			double moved = body.getX() - startX;
			Bodies.release(body);
			helper.assertTrue(moved > 0.2, "Body should be knocked back, moved " + moved);
			helper.succeed();
		});
	}

	/** Leaves a body 20 blocks up and checks whether it fell after a second. */
	private static void checkBody(GameTestHelper helper, boolean gravity) {
		boolean previous = Deserter.config.body.gravity;
		Deserter.config.body.gravity = gravity;
		ServerPlayer player = leaveBody(helper);
		Deserter.config.body.gravity = previous;

		double startY = player.getY();
		helper.runAfterDelay(20, () -> {
			double y = player.getY();
			Bodies.release(player);
			helper.assertTrue(gravity ? y < startY - 1 : y == startY, "gravity=" + gravity + ": started at " + startY + ", now " + y);
			helper.assertTrue(!player.isNoGravity(), "NoGravity must be restored on release");
			helper.succeed();
		});
	}

	/** Spawns a mock player 20 blocks up, puts them in combat and disconnects them. */
	private static ServerPlayer leaveBody(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		// Real clients send this on join; until then players are invulnerable.
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		BlockPos start = helper.absolutePos(new BlockPos(1, 20, 1));
		player.setPos(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
		Deserter.COMBAT.tag(player, CombatManager.now(helper.getLevel().getServer()), 200);
		player.connection.onDisconnect(new DisconnectionDetails(Component.empty()));
		helper.assertTrue(Bodies.isBody(player), "Disconnecting in combat should leave a body");
		return player;
	}
}
