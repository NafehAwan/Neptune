package net.tuffclient.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.tuffclient.TuffClient;
import net.tuffclient.gui.ColorScreen;
import net.tuffclient.gui.GladeMenuScreen;
import net.tuffclient.gui.HudLayoutScreen;
import net.tuffclient.gui.ModuleScreen;
import net.tuffclient.module.Module;

/** Opens every Glade screen and the kept modules in the real game, with screenshots. */
public class TuffClientGameTest implements FabricClientGameTest {
	private static final java.util.List<String> HUD = java.util.List.of("fps", "keystrokes", "armor", "ping", "day_time", "reach");

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1920, 1080);
		// the game's own title screen: Glade doesn't replace it any more
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(20);
		context.takeScreenshot("title");

		String problems = context.computeOnClient(client -> {
			StringBuilder bad = new StringBuilder();
			var m = TuffClient.modules();
			if (m.all().size() != 21) bad.append(" modules=").append(m.all().size());
			if (!Module.hasMixin("HitboxStyleMixin")) bad.append(" no-hitbox-style");
			if (!Module.hasMixin("ArmorTintMixin")) bad.append(" no-armor-tint");
			for (Module mod : m.all()) if (mod.hidden()) bad.append(" hidden:").append(mod.id);
			return bad.toString();
		});
		if (!problems.isEmpty()) throw new AssertionError("Glade setup:" + problems);

		context.setScreen(() -> new GladeMenuScreen(new TitleScreen()));
		context.getInput().setCursorPos(960, 300);
		context.waitTicks(10);
		context.takeScreenshot("glade-menu");

		context.runOnClient(client -> TuffClient.modules().crosshair.style.set(7));
		context.setScreen(() -> new ModuleScreen(new TitleScreen(), TuffClient.modules().crosshair));
		context.waitTicks(10);
		context.takeScreenshot("glade-crosshair-options");
		context.runOnClient(client -> TuffClient.modules().crosshair.style.set(0));

		context.setScreen(() -> new ModuleScreen(new TitleScreen(), TuffClient.modules().keystrokes));
		context.waitTicks(10);
		context.takeScreenshot("glade-keystrokes-options");

		context.setScreen(() -> new ColorScreen(new TitleScreen(), TuffClient.modules().hitboxes.aimed));
		context.waitTicks(10);
		context.takeScreenshot("glade-color");

		context.setScreen(TitleScreen::new);
		context.waitForScreen(TitleScreen.class);
		// turning a resource pack on and off must finish loading (players saw it get stuck)
		togglePack(context, "glade-pack-title");

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			waitForChunks(world);
			world.getServer().runCommand("/tp @a 0.5 -60 0.5 0 0");
			world.getServer().runCommand("/item replace entity @a armor.head with minecraft:iron_helmet");
			world.getServer().runCommand("/item replace entity @a armor.chest with minecraft:diamond_chestplate");
			context.runOnClient(client -> {
				for (Module m : TuffClient.modules().all()) if (HUD.contains(m.id) || m.id.equals("crosshair")) m.setEnabled(true);
			});
			context.waitTicks(20);
			context.takeScreenshot("glade-hud");

			// Right Shift in game opens Glade's menu
			context.getInput().pressKey(TuffClient.menuKey());
			context.waitForScreen(GladeMenuScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("glade-menu-ingame");

			context.setScreen(() -> new HudLayoutScreen(null));
			context.waitTicks(5);
			context.takeScreenshot("glade-hud-layout");
			context.setScreen(() -> null);

			// Hitboxes (thick, no look arrow or eye line, red when aimed at) and Damage Tint on armor
			world.getServer().runCommand("/summon minecraft:zombie 0.5 -60 3 {NoAI:1b,Rotation:[180f,0f],equipment:{feet:{id:\"minecraft:diamond_boots\"},legs:{id:\"minecraft:diamond_leggings\"},chest:{id:\"minecraft:diamond_chestplate\"},head:{id:\"minecraft:diamond_helmet\"}}}");
			context.runOnClient(client -> {
				var m = TuffClient.modules();
				m.hitboxes.setEnabled(true);
				m.hitboxes.lookLine.set(false);
				m.hitboxes.eyeLine.set(false);
				m.hitboxes.width.set(5.0);
				m.hitColor.setEnabled(true);
			});
			context.waitTicks(20);
			waitForChunks(world);
			world.getServer().runCommand("/damage @e[type=minecraft:zombie,limit=1] 1");
			context.waitTicks(2);
			context.takeScreenshot("glade-hitbox-armor-tint");
			String aimed = context.computeOnClient(client -> client.crosshairPickEntity == null ? "none" : client.crosshairPickEntity.getType().toShortString());
			System.out.println("[glade-test] aiming at: " + aimed);
		}
	}

	private static void togglePack(ClientGameTestContext context, String screenshot) {
		for (boolean on : new boolean[] {true, false}) {
			boolean changed = context.computeOnClient(client -> {
				var repo = client.getResourcePackRepository();
				repo.reload();
				if (!repo.getAvailableIds().contains("programmer_art")) return false;
				var ids = new java.util.ArrayList<>(repo.getSelectedIds());
				if (on) ids.add("programmer_art");
				else ids.remove("programmer_art");
				repo.setSelected(ids);
				client.options.updateResourcePacks(repo);
				return true;
			});
			if (!changed) {
				System.out.println("[glade-test] no programmer_art pack in this version, skipping the pack check");
				return;
			}
			context.waitTicks(2);
			long start = System.currentTimeMillis();
			context.waitFor(client -> net.tuffclient.Mc.overlay() == null, 20 * 120);
			System.out.println("[glade-test] resource reload with programmer_art " + (on ? "on" : "off") + " took " + (System.currentTimeMillis() - start) + " ms");
			if (on) context.takeScreenshot(screenshot);
		}
	}

	private static void waitForChunks(TestSingleplayerContext world) {
		try {
			// look methods up on the public interfaces: the implementations aren't public
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
