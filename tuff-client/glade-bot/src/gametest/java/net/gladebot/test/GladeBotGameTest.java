package net.gladebot.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.gladebot.Kits;
import net.gladebot.Match;
import net.gladebot.Settings;
import net.gladebot.Tier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Starts a Neth Pot match in a fresh world and checks the bot fights, pots and the match ends. */
public class GladeBotGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1920, 1080);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			this.world = world;
			waitForChunks(world);
			context.waitTicks(20);
			MinecraftServer server = world.getServer().computeOnServer(s -> s);

			// a match at HT3 with mend
			String started = onServer(context, server, () -> {
				Settings.tier = Tier.HT3;
				Settings.mend = true;
				return Match.start(you(server));
			});
			System.out.println("[gladebot-test] " + started);
			context.waitTicks(40);
			context.takeScreenshot("bot-countdown");
			boolean kits = onServer(context, server, () -> you(server).getInventory().getItem(0).is(Items.NETHERITE_SWORD)
					&& Match.bot != null && Match.bot.getInventory().getItem(0).is(Items.NETHERITE_SWORD)
					&& Match.bot.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE));
			if (!kits) throw new AssertionError("Kits weren't given to both players");

			context.waitTicks(Match.COUNTDOWN);
			if (onServer(context, server, () -> Match.state) != Match.State.FIGHT) throw new AssertionError("The fight didn't start after the countdown");
			context.waitTicks(80);
			context.takeScreenshot("bot-fight");
			int hits = onServer(context, server, () -> Match.bot == null ? -1 : Match.bot.brain.hits);
			System.out.println("[gladebot-test] hits after 4s: " + hits);
			if (hits <= 0 && Match.winner == null) throw new AssertionError("The bot didn't hit anyone");

			// the bot heals itself when low
			if (Match.state == Match.State.FIGHT) {
				onServer(context, server, () -> {
					Match.bot.setHealth(5f);
					return null;
				});
				context.waitTicks(60);
				int pots = onServer(context, server, () -> Match.bot == null ? -1 : Match.bot.brain.pots);
				System.out.println("[gladebot-test] pots when low: " + pots);
				if (pots <= 0 && Match.winner == null) throw new AssertionError("The bot didn't pot when low");
			}

			// you stand still, so the bot should win, and nobody dies
			for (int i = 0; i < 90 && Match.winner == null; i++) context.waitTicks(20);
			context.takeScreenshot("bot-result");
			System.out.println("[gladebot-test] winner: " + Match.winner);
			if (!"bot".equals(Match.winner)) throw new AssertionError("Expected the bot to win against a player standing still, got " + Match.winner);
			if (onServer(context, server, () -> you(server).isDeadOrDying())) throw new AssertionError("The player died instead of the match ending");
			context.waitTicks(80);
			if (onServer(context, server, () -> Match.state) != Match.State.NONE) throw new AssertionError("The match didn't clean up");

			// saving a kit round-trips
			boolean saved = onServer(context, server, () -> {
				ServerPlayer p = you(server);
				p.getInventory().setItem(5, new ItemStack(Items.GOLDEN_APPLE, 7));
				ItemStack[] kit = Kits.capture(p);
				ItemStack[] back = Kits.decode(server.registryAccess(), Kits.encode(server.registryAccess(), kit));
				return back != null && back[5].is(Items.GOLDEN_APPLE) && back[5].getCount() == 7 && back[0].is(Items.NETHERITE_SWORD);
			});
			if (!saved) throw new AssertionError("Saving a kit didn't round-trip");
		}
	}

	private static ServerPlayer you(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) if (!(p instanceof net.gladebot.BotPlayer)) return p;
		throw new AssertionError("No player in the world");
	}

	private TestSingleplayerContext world;

	/** Runs on the server thread and returns the answer. */
	private <T> T onServer(ClientGameTestContext context, MinecraftServer server, java.util.function.Supplier<T> what) {
		return world.getServer().computeOnServer(s -> what.get());
	}

	/** Waits for chunks to render: getConnection() on newer Fabric API, getClientWorld() on older. */
	private static void waitForChunks(TestSingleplayerContext world) {
		try {
			java.lang.reflect.Method get;
			try {
				get = TestSingleplayerContext.class.getMethod("getConnection");
			} catch (NoSuchMethodException e) {
				get = TestSingleplayerContext.class.getMethod("getClientWorld");
			}
			Object target = get.invoke(world);
			for (java.lang.reflect.Method m : get.getReturnType().getMethods()) {
				if (m.getName().startsWith("waitForChunks") && m.getParameterCount() == 0) {
					m.invoke(target);
					return;
				}
			}
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Couldn't wait for chunks", e);
		}
		throw new AssertionError("No waitForChunks method on this Fabric API");
	}
}
