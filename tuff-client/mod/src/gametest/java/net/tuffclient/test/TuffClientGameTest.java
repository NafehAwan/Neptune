package net.tuffclient.test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.effect.MobEffects;
import net.tuffclient.TuffClient;
import net.tuffclient.account.Accounts;
import net.tuffclient.gui.HudEditorScreen;
import net.tuffclient.gui.TuffMenuScreen;
import net.tuffclient.gui.TuffTitleScreen;
import net.tuffclient.gui.Ui;
import net.tuffclient.module.Module;

public class TuffClientGameTest implements FabricClientGameTest {
	/** Counts gold-coloured pixels in the middle of a screenshot (away from the hand and HUD). */
	private static long goldPixels(Path screenshot) {
		try {
			BufferedImage image = ImageIO.read(screenshot.toFile());
			long count = 0;
			int w = image.getWidth(), h = image.getHeight();
			for (int y = h * 3 / 10; y < h * 7 / 10; y++) {
				for (int x = w * 3 / 10; x < w * 7 / 10; x++) {
					int rgb = image.getRGB(x, y);
					int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
					if (r > 100 && r >= g && g > b && r - b > 50) count++;
				}
			}
			return count;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		// A common 1080p window, so screenshots match what players see.
		context.getInput().resizeWindow(1920, 1080);
		// The title screen mixin must have replaced the vanilla menu.
		context.waitForScreen(TuffTitleScreen.class);
		context.waitTicks(40);
		context.takeScreenshot("tuff-title");

		// The UI fonts must actually be loaded (their widths differ from the game font).
		boolean customFont = context.computeOnClient(client ->
				Ui.width("Glade menu", Ui.Font.BODY) != client.font.width("Glade menu")
						&& Ui.width("Glade", Ui.Font.HEAD_30) != client.font.width("Glade"));
		if (!customFont) throw new AssertionError("Glade UI fonts did not load");

		// The account menu: 3D heads, and offline accounts switch on the spot and back again.
		context.getInput().setCursorPos(4, 400);
		context.runOnClient(client -> ((TuffTitleScreen) client.gui.screen()).setAccountMenuOpen(true));
		context.waitTicks(12);
		context.takeScreenshot("glade-account-menu");
		String original = context.computeOnClient(client -> client.getUser().getName());
		context.runOnClient(client -> Accounts.addOffline("GladeTester"));
		context.waitTicks(8);
		if (!"GladeTester".equals(context.computeOnClient(client -> client.getUser().getName()))) {
			throw new AssertionError("Adding an offline account did not switch to it");
		}
		context.takeScreenshot("glade-account-switched");
		context.runOnClient(client -> {
			Accounts.Account tester = Accounts.current();
			String why = Accounts.switchTo(Accounts.launchAccount());
			if (why != null) throw new AssertionError("Could not switch back: " + why);
			Accounts.remove(tester);
			((TuffTitleScreen) client.gui.screen()).setAccountMenuOpen(false);
		});
		if (!original.equals(context.computeOnClient(client -> client.getUser().getName()))) {
			throw new AssertionError("Switching back did not restore the original account");
		}
		context.waitTicks(8);

		// Right Shift opens Glade's settings from the title screen.
		context.getInput().pressKey(TuffClient.menuKey());
		context.waitForScreen(TuffMenuScreen.class);
		context.runOnClient(client -> client.gui.screen().onClose());
		context.waitForScreen(TuffTitleScreen.class);
		// Without Mod Menu installed, Mods opens Glade's own mods page.
		context.runOnClient(client -> ((TuffTitleScreen) client.gui.screen()).openMods());
		context.waitForScreen(TuffMenuScreen.class);
		context.runOnClient(client -> client.gui.screen().onClose());
		context.waitForScreen(TuffTitleScreen.class);
		context.waitTicks(10);

		Screen title = context.computeOnClient(client -> client.gui.screen());
		TuffMenuScreen menu = context.computeOnClient(client -> new TuffMenuScreen(title));
		context.setScreen(() -> menu);
		// Park the cursor in a corner so screenshots show no hover state or tooltip.
		context.getInput().setCursorPos(4, 4);
		context.waitTicks(15);
		context.takeScreenshot("tuff-menu-mods");

		// Clicking a mod's switch toggles the module.
		Module scoreboard = context.computeOnClient(client -> TuffClient.modules().fullbright);
		boolean before = context.computeOnClient(client -> scoreboard.isEnabled());
		double[] bar = context.computeOnClient(client -> menu.switchPosition(scoreboard.id));
		if (bar == null) throw new AssertionError("Fullbright row was not drawn");
		context.getInput().setCursorPos(bar[0], bar[1]);
		context.waitTicks(1);
		context.getInput().pressMouse(0);
		context.waitTicks(2);
		if (context.computeOnClient(client -> scoreboard.isEnabled()) == before) throw new AssertionError("Clicking the switch did not toggle the module");
		context.getInput().setCursorPos(4, 4);
		context.waitTicks(12);
		context.takeScreenshot("tuff-menu-toggled");
		context.runOnClient(client -> scoreboard.setEnabled(before));

		// The heart marks a favorite.
		Module fps = context.computeOnClient(client -> TuffClient.modules().zoom);
		double[] heart = context.computeOnClient(client -> menu.heartPosition(fps.id));
		if (heart == null) throw new AssertionError("Zoom card was not drawn");
		context.getInput().setCursorPos(heart[0], heart[1]);
		context.waitTicks(1);
		context.getInput().pressMouse(0);
		context.waitTicks(2);
		if (!context.computeOnClient(client -> fps.favorite)) throw new AssertionError("Clicking the heart did not favorite the module");
		context.getInput().setCursorPos(4, 4);
		context.runOnClient(client -> menu.setListView(true));
		context.waitTicks(10);
		context.takeScreenshot("tuff-menu-list");
		context.runOnClient(client -> {
			menu.setListView(false);
			fps.favorite = false;
		});
		context.runOnClient(client -> menu.openSettings(TuffClient.modules().crosshair));
		context.waitTicks(15);
		context.takeScreenshot("tuff-menu-drawer");
		// The colour wheel picker, and the shared HUD style settings.
		context.runOnClient(client -> menu.openColorPicker(TuffClient.modules().hitColor, "color"));
		context.waitTicks(15);
		context.takeScreenshot("glade-color-picker");
		context.runOnClient(client -> {
			Module fpsModule = TuffClient.modules().all().stream().filter(m -> m.id.equals("fps")).findFirst().orElseThrow();
			menu.openSettings(fpsModule);
			menu.scrollDrawer(260);
		});
		context.waitTicks(15);
		context.takeScreenshot("glade-hud-style");
		for (TuffMenuScreen.Page page : new TuffMenuScreen.Page[] {TuffMenuScreen.Page.THEME, TuffMenuScreen.Page.PACKS, TuffMenuScreen.Page.SETTINGS}) {
			context.runOnClient(client -> menu.setPage(page));
			context.waitTicks(12);
			context.takeScreenshot("tuff-menu-" + page.name().toLowerCase());
		}
		context.runOnClient(client -> menu.onClose());
		context.waitForScreen(TuffTitleScreen.class);

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
			context.getInput().setCursorPos(4, 4);
			context.waitTicks(15);
			context.takeScreenshot("tuff-menu-ingame");
			context.setScreen(() -> null);

			// The new HUD modules, a waypoint ahead, merged chat lines and a clean F3.
			world.getServer().runCommand("/tp @a 0.5 -60 0.5 0 0");
			context.waitTicks(10);
			context.runOnClient(client -> {
				for (Module m : TuffClient.modules().all()) {
					if (java.util.List.of("compass", "biome_light", "item_counter", "session_stats", "day_time", "durability").contains(m.id)) m.setEnabled(true);
				}
				TuffClient.modules().waypoints.here().clear();
			});
			world.getServer().runCommand("/tp @a 0.5 -60 8.5 0 0");
			context.waitTicks(5);
			context.runOnClient(client -> TuffClient.modules().waypoints.addHere());
			world.getServer().runCommand("/tp @a 0.5 -60 0.5 0 0");
			context.waitTicks(5);
			context.runOnClient(client -> {
				client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Hello from Glade"));
				client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Hello from Glade"));
				client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Hello from Glade"));
			});
			context.waitTicks(20);
			world.getConnection().waitForChunksRender();
			context.takeScreenshot("glade-new-hud");
			// Chroma wave text, chroma keys and the Clean look.
			context.runOnClient(client -> {
				for (var h : TuffClient.modules().hud()) {
					if (h.id.equals("fps") || h.id.equals("coordinates")) {
						h.textColor.chroma = true;
						h.labelColor.chroma = true;
						h.applyLook("Clean");
					}
					if (h.id.equals("keystrokes")) {
						for (var setting : h.settings()) {
							if (setting instanceof net.tuffclient.setting.ColorSetting c && (c.id.equals("key_text") || c.id.equals("key_color"))) c.chroma = true;
						}
					}
				}
			});
			context.waitTicks(5);
			context.takeScreenshot("glade-chroma");
			int waypoints = context.computeOnClient(client -> TuffClient.modules().waypoints.here().size());
			if (waypoints != 1) throw new AssertionError("Adding a waypoint failed: " + waypoints);

			context.runOnClient(client -> {
				TuffClient.modules().cleanF3.setEnabled(true);
				client.debugEntries.setOverlayVisible(true);
			});
			context.waitTicks(10);
			context.takeScreenshot("glade-clean-f3");
			context.runOnClient(client -> {
				client.debugEntries.setOverlayVisible(false);
				TuffClient.modules().cleanF3.setEnabled(false);
				var sky = TuffClient.modules().customSky;
				sky.setEnabled(true);
				for (var setting : sky.settings()) {
					if (setting instanceof net.tuffclient.setting.ColorSetting c && c.id.equals("sky_color")) c.set(0xFFFF66CC);
					if (setting instanceof net.tuffclient.setting.NumberSetting n && n.id.equals("strength")) n.set(90.0);
				}
				TuffClient.modules().blockOutline.setEnabled(true);
			});
			context.getInput().holdKey(TuffClient.freelookKey());
			context.waitTicks(15);
			context.takeScreenshot("glade-freelook-sky");
			boolean freelooking = context.computeOnClient(client -> TuffClient.modules().freelook.active() && !client.options.getCameraType().isFirstPerson());
			context.getInput().releaseKey(TuffClient.freelookKey());
			context.waitTicks(3);
			boolean back = context.computeOnClient(client -> client.options.getCameraType().isFirstPerson());
			context.runOnClient(client -> TuffClient.modules().customSky.setEnabled(false));
			if (!freelooking || !back) throw new AssertionError("Freelook didn't switch the camera: on=" + freelooking + " back=" + back);
			context.waitTicks(5);

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
