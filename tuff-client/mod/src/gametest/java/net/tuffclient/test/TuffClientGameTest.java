package net.tuffclient.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.tuffclient.TuffClient;
import net.tuffclient.gui.HudEditorScreen;
import net.tuffclient.gui.TuffMenuScreen;
import net.tuffclient.gui.TuffTitleScreen;
import net.tuffclient.module.Module;

public class TuffClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		// The title screen mixin must have replaced the vanilla menu.
		context.waitForScreen(TuffTitleScreen.class);
		context.waitTicks(40);
		context.takeScreenshot("tuff-title");

		context.setScreen(() -> new TuffMenuScreen(null));
		context.waitTicks(10);
		context.takeScreenshot("tuff-menu");

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.runOnClient(client -> {
				for (Module m : TuffClient.modules().all()) {
					if (m.id.equals("keystrokes") || m.id.equals("effects") || m.id.equals("clock") || m.id.equals("crosshair")) m.setEnabled(true);
				}
			});
			context.waitTicks(20);
			context.takeScreenshot("tuff-hud");

			context.setScreen(() -> new HudEditorScreen(null));
			context.waitTicks(10);
			context.takeScreenshot("tuff-hud-editor");

			context.setScreen(() -> new TuffMenuScreen(null));
			context.waitTicks(10);
			context.takeScreenshot("tuff-menu-ingame");
			context.setScreen(() -> null);

			// Zoom and fullbright must not crash when toggled in a world.
			context.runOnClient(client -> {
				TuffClient.modules().fullbright.setEnabled(true);
				TuffClient.modules().zoom.setHeld(true);
			});
			context.waitTicks(20);
			context.takeScreenshot("tuff-zoom-fullbright");
			context.runOnClient(client -> {
				TuffClient.modules().zoom.setHeld(false);
				TuffClient.modules().fullbright.setEnabled(false);
			});
		}

		// Leaving the world lands on the Tuff title screen again; the test
		// framework requires the vanilla one at the end, so switch it off.
		context.waitForScreen(TuffTitleScreen.class);
		context.runOnClient(client -> TuffClient.modules().titleScreen.setEnabled(false));
		context.setScreen(TitleScreen::new);
	}
}
