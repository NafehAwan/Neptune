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
		// A window a little shorter than the screen (title bar, taskbar): still pixel-exact.
		context.getInput().resizeWindow(1920, 1009);
		context.waitTicks(10);
		context.takeScreenshot("tuff-title-short");
		context.getInput().resizeWindow(1920, 1080);
		context.waitTicks(10);

		// The UI fonts must actually be loaded (their widths differ from the game font).
		boolean customFont = context.computeOnClient(client ->
				Ui.width("Glade menu", Ui.Font.BODY) != client.font.width("Glade menu")
						&& Ui.width("Glade", Ui.Font.HEAD_30) != client.font.width("Glade"));
		if (!customFont) throw new AssertionError("Glade UI fonts did not load");

		// The account menu: 3D heads, and offline accounts switch on the spot and back again.
		context.getInput().setCursorPos(4, 400);
		context.runOnClient(client -> ((TuffTitleScreen) net.tuffclient.Mc.screen()).setAccountMenuOpen(true));
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
			((TuffTitleScreen) net.tuffclient.Mc.screen()).setAccountMenuOpen(false);
		});
		if (!original.equals(context.computeOnClient(client -> client.getUser().getName()))) {
			throw new AssertionError("Switching back did not restore the original account");
		}
		context.waitTicks(8);

		// Right Shift opens Glade's settings from the title screen.
		context.getInput().pressKey(TuffClient.menuKey());
		context.waitForScreen(TuffMenuScreen.class);
		context.runOnClient(client -> net.tuffclient.Mc.screen().onClose());
		context.waitForScreen(TuffTitleScreen.class);
		// Without Mod Menu installed, Mods opens Glade's own mods page.
		context.runOnClient(client -> ((TuffTitleScreen) net.tuffclient.Mc.screen()).openMods());
		context.waitForScreen(TuffMenuScreen.class);
		context.runOnClient(client -> net.tuffclient.Mc.screen().onClose());
		context.waitForScreen(TuffTitleScreen.class);
		context.waitTicks(10);

		Screen title = context.computeOnClient(client -> net.tuffclient.Mc.screen());
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
		// Turning a resource pack on and off must finish loading (players saw it get stuck).
		togglePack(context, "glade-pack-title");

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			waitForChunks(world);
			togglePack(context, "glade-pack-world");
			waitForChunks(world);
			context.runOnClient(client -> {
				for (Module m : TuffClient.modules().all()) {
					if (m.id.equals("keystrokes") || m.id.equals("effects") || m.id.equals("clock") || m.id.equals("crosshair")) m.setEnabled(true);
				}
			});
			context.waitTicks(20);
			context.takeScreenshot("tuff-hud");

			// Right Shift in game opens the HUD editor
			context.getInput().pressKey(TuffClient.menuKey());
			context.waitForScreen(HudEditorScreen.class);
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
				chat(client, "Hello from Glade");
				chat(client, "Hello from Glade");
				chat(client, "Hello from Glade");
			});
			context.waitTicks(20);
			waitForChunks(world);
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
				// Glade's keys start unbound; bind them like a player would
				TuffClient.freelookKey().setKey(client.options.keySwapOffhand.getDefaultKey());
				TuffClient.zoomKey().setKey(client.options.keyDrop.getDefaultKey());
				net.minecraft.client.KeyMapping.resetMapping();
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

			// Tiers: sample ranks on your own nametag, seen from the front
			world.getServer().runCommand("/tp @a 0.5 -60 0.5 0 0");
			context.runOnClient(client -> {
				var m = TuffClient.modules();
				m.nametags.setEnabled(true);
				m.tiers.setEnabled(true);
				m.tiers.pvptiers.set(6); // Neth Pot, on the left
				m.tiers.pvptiersSide.set(0);
				m.tiers.mctiers.set(1); // best tier, on the right
				m.tiers.mctiersSide.set(1);
				String me = client.player.getScoreboardName();
				net.tuffclient.tiers.TierService.put(net.tuffclient.tiers.TierService.Site.PVPTIERS, me,
						java.util.Map.of("neth_pot", new net.tuffclient.tiers.TierService.Rank(1, true, false)));
				net.tuffclient.tiers.TierService.put(net.tuffclient.tiers.TierService.Site.MCTIERS, me,
						java.util.Map.of("sword", new net.tuffclient.tiers.TierService.Rank(2, false, false), "axe", new net.tuffclient.tiers.TierService.Rank(3, true, false)));
				client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
			});
			context.waitTicks(15);
			context.takeScreenshot("glade-tiers");
			// the tab list shows the leaf badge next to your own name
			boolean tabBadge = context.computeOnClient(client -> {
				var info = client.getConnection().getPlayerInfo(client.player.getUUID());
				return net.tuffclient.Mc.tabList().getNameForDisplay(info).getString().contains("\uE200");
			});
			if (!tabBadge) throw new AssertionError("Tab list name has no Glade badge");
			context.runOnClient(client -> {
				client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
				TuffClient.modules().tiers.setEnabled(false);
				TuffClient.modules().nametags.setEnabled(false);
			});
			context.waitTicks(5);

			// Lunar-style modules: low health, saturation, cooldowns, TNT timer, shiny pots,
			// a new glint color, hitboxes, the sprint status line and a custom crosshair.
			world.getServer().runCommand("/tp @a 0.5 -60 0.5 0 0");
			world.getServer().runCommand("/gamemode survival @a");
			world.getServer().runCommand("/item replace entity @a hotbar.0 with minecraft:splash_potion");
			world.getServer().runCommand("/item replace entity @a hotbar.1 with minecraft:ender_pearl 16");
			world.getServer().runCommand("/item replace entity @a hotbar.2 with minecraft:diamond_sword");
			world.getServer().runCommand("/enchant @a minecraft:sharpness 5");
			world.getServer().runCommand("/damage @a 13");
			world.getServer().runCommand("/summon minecraft:tnt 0.5 -60 5.5 {fuse:400}");
			world.getServer().runCommand("/summon minecraft:zombie 2.5 -60 6.5 {NoAI:1b}");
			context.runOnClient(client -> {
				var m = TuffClient.modules();
				for (Module mod : m.all()) {
					if (java.util.List.of("toggle_sprint", "cooldowns", "saturation", "low_health", "tnt_timer", "shiny_pots", "glint_color", "hitboxes", "nick_hider", "crosshair").contains(mod.id)) mod.setEnabled(true);
				}
				m.crosshair.style.set(4);
				m.crosshair.dot.set(true);
				client.player.getInventory().setSelectedSlot(2);
				client.player.getCooldowns().addCooldown(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL), 200);
			});
			context.waitTicks(30);
			waitForChunks(world);
			context.takeScreenshot("glade-lunar-modules");
			String problems = context.computeOnClient(client -> {
				var m = TuffClient.modules();
				StringBuilder bad = new StringBuilder();
				if (!new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SPLASH_POTION).hasFoil()) bad.append(" shiny-pots");
				String me = client.getUser().getName();
				if (!m.nickHider.tab(net.minecraft.network.chat.Component.literal("[VIP] " + me)).getString().equals("[VIP] You")) bad.append(" nick-hider");
				if (net.tuffclient.compat.Cooldowns.left(client.player, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL)) <= 0) bad.append(" cooldowns");
				return bad.toString();
			});
			if (!problems.isEmpty()) throw new AssertionError("Lunar-style modules failed:" + problems);
			context.runOnClient(client -> {
				for (Module mod : TuffClient.modules().all()) {
					if (java.util.List.of("toggle_sprint", "cooldowns", "saturation", "low_health", "tnt_timer", "shiny_pots", "glint_color", "hitboxes", "nick_hider", "crosshair").contains(mod.id)) mod.setEnabled(false);
				}
			});
			world.getServer().runCommand("/kill @e[type=minecraft:zombie]");
			world.getServer().runCommand("/kill @e[type=minecraft:tnt]");
			world.getServer().runCommand("/effect give @a minecraft:instant_health 1 5");
			world.getServer().runCommand("/gamemode creative @a");

			// Motion Blur: Glade's post effect must load (shader and JSON) and blend while turning
			context.runOnClient(client -> TuffClient.modules().motionBlur.setEnabled(true));
			for (int i = 0; i < 12; i++) {
				final int step = i;
				context.runOnClient(client -> client.player.setYRot(step * 6f));
				context.waitTicks(1);
			}
			context.takeScreenshot("glade-motion-blur");
			boolean blurLoaded = context.computeOnClient(client -> TuffClient.modules().motionBlur.loaded());
			context.runOnClient(client -> {
				TuffClient.modules().motionBlur.setEnabled(false);
				client.player.setYRot(0);
			});
			if (!blurLoaded) throw new AssertionError("Motion blur post effect didn't load");

			// Clear Glass: Glade's built-in pack turns on with the module (a resource reload)
			world.getServer().runCommand("/fill -2 -60 3 2 -58 3 minecraft:glass");
			world.getServer().runCommand("/fill -2 -57 3 2 -57 3 minecraft:red_stained_glass");
			context.runOnClient(client -> TuffClient.modules().clearGlass.setEnabled(true));
			context.waitTicks(5);
			context.waitFor(client -> net.tuffclient.Mc.overlay() == null, 20 * 120);
			waitForChunks(world);
			context.waitTicks(20);
			context.takeScreenshot("glade-clear-glass");
			boolean clearOn = context.computeOnClient(client -> client.getResourcePackRepository().getSelectedIds().stream().anyMatch(id -> id.endsWith("clear_glass")));
			context.runOnClient(client -> TuffClient.modules().clearGlass.setEnabled(false));
			context.waitTicks(5);
			context.waitFor(client -> net.tuffclient.Mc.overlay() == null, 20 * 120);
			if (!clearOn) throw new AssertionError("Clear Glass pack wasn't turned on");
			world.getServer().runCommand("/fill -2 -60 3 2 -57 3 minecraft:air");
			waitForChunks(world);

			// Zoom: a gold block 12 blocks ahead must cover far more pixels while zoomed,
			// proving the narrower FOV reaches the world projection.
			world.getServer().runCommand("/tp @a 0.5 -60 0.5 0 0");
			world.getServer().runCommand("/setblock 0 -59 12 minecraft:gold_block");
			context.waitTicks(20);
			waitForChunks(world);
			long normalGold = goldPixels(context.takeScreenshot("tuff-zoom-before"));
			float normalFov = context.computeOnClient(TuffClientGameTest::cameraFov);
			context.getInput().holdKey(TuffClient.zoomKey());
			context.waitTicks(30);
			float zoomedFov = context.computeOnClient(TuffClientGameTest::cameraFov);
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

			// New visuals together: evening and rain on our screen only, flat dropped items,
			// a small shield, a moved main hand and the armor HUD with worn armor.
			world.getServer().runCommand("/tp @a 0.5 -60 0.5 0 35");
			world.getServer().runCommand("/item replace entity @a weapon.mainhand with minecraft:diamond_sword");
			world.getServer().runCommand("/item replace entity @a weapon.offhand with minecraft:shield");
			world.getServer().runCommand("/item replace entity @a armor.head with minecraft:diamond_helmet[damage=300]");
			world.getServer().runCommand("/item replace entity @a armor.chest with minecraft:diamond_chestplate");
			world.getServer().runCommand("/item replace entity @a armor.legs with minecraft:iron_leggings[damage=150]");
			world.getServer().runCommand("/item replace entity @a armor.feet with minecraft:golden_boots[damage=88]");
			world.getServer().runCommand("/summon item 0.5 -60 2.5 {Item:{id:\"minecraft:diamond\",count:1},PickupDelay:32767}");
			world.getServer().runCommand("/summon item 1.3 -60 3.2 {Item:{id:\"minecraft:apple\",count:1},PickupDelay:32767}");
			context.runOnClient(client -> {
				var m = TuffClient.modules();
				m.timeWeather.setEnabled(true);
				m.timeWeather.hour.set(18.5);
				m.timeWeather.weather.set(1);
				m.itemPhysics.setEnabled(true);
				m.smallShield.setEnabled(true);
				m.handView.setEnabled(true);
				m.handView.mainScale.set(80.0);
				m.handView.mainY.set(-0.2);
				m.oldAnimations.setEnabled(true);
				for (var h : m.hud()) if (h.id.equals("armor")) h.setEnabled(true);
			});
			context.waitTicks(40);
			waitForChunks(world);
			context.takeScreenshot("glade-visuals");
			float rain = context.computeOnClient(client -> client.level.getRainLevel(1f));
			context.runOnClient(client -> TuffClient.modules().timeWeather.weather.set(3));
			float serverRain = context.computeOnClient(client -> client.level.getRainLevel(1f));
			context.runOnClient(client -> TuffClient.modules().timeWeather.setEnabled(false));
			if (rain != 1f || serverRain != 0f) throw new AssertionError("Weather changer was wrong: rain=" + rain + " server=" + serverRain);
		}

		// Leaving the world lands on the Tuff title screen again; the test
		// framework requires the vanilla one at the end, so switch it off.
		context.waitForScreen(TuffTitleScreen.class);
		context.runOnClient(client -> TuffClient.modules().titleScreen.setEnabled(false));
		context.setScreen(TitleScreen::new);
	}

	// ---- calls that differ between Minecraft versions (1.21.11 and 26.x), found by name

	/** Waits for the world's chunks to render: getConnection() on 26.x, getClientWorld() before. */
	/** Turns the built-in Programmer Art pack on, then off again, the way Glade's Packs page and
	 *  the vanilla pack screen do, and fails if either resource reload doesn't finish. */
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

	/** Puts a line in your own chat: sendSystemMessage on 26.x, displayClientMessage before. */
	private static void chat(net.minecraft.client.Minecraft client, String text) {
		var line = net.minecraft.network.chat.Component.literal(text);
		for (java.lang.reflect.Method m : client.player.getClass().getMethods()) {
			try {
				if (m.getName().equals("sendSystemMessage") && m.getParameterCount() == 1) {
					m.invoke(client.player, line);
					return;
				}
				if (m.getName().equals("displayClientMessage") && m.getParameterCount() == 2) {
					m.invoke(client.player, line, false);
					return;
				}
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("Couldn't send a chat line", e);
			}
		}
		throw new AssertionError("No chat method on LocalPlayer");
	}

	/** The camera's field of view: Camera.getFov() on 26.x, GameRenderer.getFov(Camera, float, boolean) before. */
	private static float cameraFov(net.minecraft.client.Minecraft client) {
		try {
			Object renderer = client.gameRenderer;
			java.lang.reflect.Method cam;
			try {
				cam = renderer.getClass().getMethod("mainCamera");
			} catch (NoSuchMethodException e) {
				cam = renderer.getClass().getMethod("getMainCamera");
			}
			Object camera = cam.invoke(renderer);
			try {
				return ((Number) camera.getClass().getMethod("getFov").invoke(camera)).floatValue();
			} catch (NoSuchMethodException e) {
				for (Class<?> c = renderer.getClass(); c != null; c = c.getSuperclass()) {
					for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
						Class<?>[] p = m.getParameterTypes();
						if (m.getName().equals("getFov") && p.length == 3 && p[1] == float.class && p[2] == boolean.class) {
							m.setAccessible(true);
							return ((Number) m.invoke(renderer, camera, 1f, true)).floatValue();
						}
					}
				}
			}
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Couldn't read the camera FOV", e);
		}
		throw new AssertionError("No FOV method on this Minecraft version");
	}
}
