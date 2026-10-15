package net.tuffclient.test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

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
	/** Counts gold-block-coloured pixels (bright yellow) in a screenshot. */
	private static long goldPixels(Path screenshot) {
		try {
			BufferedImage image = ImageIO.read(screenshot.toFile());
			long count = 0;
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int rgb = image.getRGB(x, y);
					int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
					if (r > 200 && g > 160 && b < 110 && r - b > 120) count++;
				}
			}
			return count;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

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

			// Zoom: a gold block 12 blocks ahead must cover far more pixels while zoomed,
			// proving the narrower FOV reaches the world projection.
			world.getServer().runCommand("/tp @a 0.5 -60 0.5 0 0");
			world.getServer().runCommand("/setblock 0 -59 12 minecraft:gold_block");
			context.waitTicks(20);
			world.getConnection().waitForChunksRender();
			long normalGold = goldPixels(context.takeScreenshot("tuff-zoom-before"));
			float normalFov = context.computeOnClient(client -> client.gameRenderer.mainCamera().getFov());
			context.getInput().holdKey(TuffClient.zoomKey());
			context.waitTicks(30);
			float zoomedFov = context.computeOnClient(client -> client.gameRenderer.mainCamera().getFov());
			long zoomedGold = goldPixels(context.takeScreenshot("tuff-zoom"));
			context.getInput().releaseKey(TuffClient.zoomKey());
			if (!(zoomedFov < normalFov * 0.5f)) {
				throw new AssertionError("Zoom did not narrow the FOV: " + normalFov + " -> " + zoomedFov);
			}
			if (normalGold == 0 || zoomedGold < normalGold * 4) {
				throw new AssertionError("Zoom did not magnify the world: gold pixels " + normalGold + " -> " + zoomedGold);
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
