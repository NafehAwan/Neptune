package net.tuffclient.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.effect.MobEffects;
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

			// Zoom: holding the key must narrow the camera's field of view.
			float normalFov = context.computeOnClient(client -> client.gameRenderer.mainCamera().getFov());
			context.getInput().holdKey(TuffClient.zoomKey());
			context.waitTicks(30);
			float zoomedFov = context.computeOnClient(client -> client.gameRenderer.mainCamera().getFov());
			context.takeScreenshot("tuff-zoom");
			context.getInput().releaseKey(TuffClient.zoomKey());
			if (!(zoomedFov < normalFov * 0.5f)) {
				throw new AssertionError("Zoom did not narrow the FOV: " + normalFov + " -> " + zoomedFov);
			}

			// Fullbright applies a hidden night vision effect and removes it again.
			context.runOnClient(client -> TuffClient.modules().fullbright.setEnabled(true));
			context.waitTicks(5);
			boolean lit = context.computeOnClient(client -> client.player.hasEffect(MobEffects.NIGHT_VISION));
			context.runOnClient(client -> TuffClient.modules().fullbright.setEnabled(false));
			context.waitTicks(2);
			boolean cleared = context.computeOnClient(client -> !client.player.hasEffect(MobEffects.NIGHT_VISION));
			if (!lit || !cleared) throw new AssertionError("Fullbright effect state was wrong: on=" + lit + " cleared=" + cleared);
		}

		// Leaving the world lands on the Tuff title screen again; the test
		// framework requires the vanilla one at the end, so switch it off.
		context.waitForScreen(TuffTitleScreen.class);
		context.runOnClient(client -> TuffClient.modules().titleScreen.setEnabled(false));
		context.setScreen(TitleScreen::new);
	}
}
