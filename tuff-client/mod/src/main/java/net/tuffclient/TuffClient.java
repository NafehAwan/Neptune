package net.tuffclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.tuffclient.gui.TuffMenuScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TuffClient implements ClientModInitializer {
	public static final String MOD_ID = "tuff";
	public static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
	public static final Logger LOGGER = LoggerFactory.getLogger("Glade");
	private static ModuleManager modules;
	private static KeyMapping menuKey;
	private static KeyMapping zoomKey;
	private static KeyMapping waypointKey;
	private static KeyMapping freelookKey;

	public static KeyMapping freelookKey() {
		return freelookKey;
	}

	public static KeyMapping waypointKey() {
		return waypointKey;
	}

	public static ModuleManager modules() {
		return modules;
	}

	/** Right Shift by default: opens Glade's settings. */
	public static KeyMapping menuKey() {
		return menuKey;
	}

	private static KeyMapping clipKey;

	public static KeyMapping clipKey() {
		return clipKey;
	}

	public static KeyMapping zoomKey() {
		return zoomKey;
	}

	@Override
	public void onInitializeClient() {
		modules = new ModuleManager();
		TuffConfig.load(modules);
		modules.ui.applyAccent();

		KeyMapping[] keys = Platform.registerKeys();
		menuKey = keys[0];
		zoomKey = keys[1];
		waypointKey = keys[2];
		freelookKey = keys[3];
		clipKey = keys[4];
		Platform.registerCommands();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			unbindOldDefaultsOnce(client);
			modules.tick(client);
			modules.clips.update(client);
			while (clipKey.consumeClick()) modules.clips.save("key", "");
			net.tuffclient.module.util.ClipsModule.presence(client);
			while (menuKey.consumeClick()) {
				if (net.tuffclient.Mc.screen() == null) net.tuffclient.Mc.setScreen(new TuffMenuScreen(null));
			}
			modules.zoom.setHeld(zoomKey.isDown() && net.tuffclient.Mc.screen() == null);
			while (waypointKey.consumeClick()) {
				if (net.tuffclient.Mc.screen() == null && modules.waypoints.isEnabled()) modules.waypoints.addHere();
			}
			modules.freelook.update(client, freelookKey.isDown() && modules.freelook.isEnabled());
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> TuffConfig.save(modules));

		Platform.registerHud(modules);
		LOGGER.info("Glade loaded with {} modules", modules.all().size());
		HookCheck.runIfAsked();
	}

	private static boolean keysChecked;

	/**
	 * Key defaults changed: Zoom and Waypoint start unbound (they used to be C
	 * and B) and Freelook is F4 (it used to be Left Alt, then unbound). Moves
	 * existing players over once, after the options have loaded.
	 */
	private static void unbindOldDefaultsOnce(net.minecraft.client.Minecraft client) {
		if (keysChecked) return;
		keysChecked = true;
		java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("tuff");
		java.nio.file.Path v1 = dir.resolve("keys-unbound"), v2 = dir.resolve("keys-f4");
		if (java.nio.file.Files.exists(v2)) return;
		try {
			boolean fromOld = !java.nio.file.Files.exists(v1);
			if (fromOld) {
				zoomKey.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
				waypointKey.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
			}
			if (fromOld || freelookKey.isUnbound()) freelookKey.setKey(freelookKey.getDefaultKey());
			KeyMapping.resetMapping();
			client.options.save();
			java.nio.file.Files.createDirectories(dir);
			java.nio.file.Files.writeString(v1, "Glade's zoom and waypoint keys start unbound.\n");
			java.nio.file.Files.writeString(v2, "Glade's freelook key defaults to F4.\n");
		} catch (Exception e) {
			LOGGER.warn("Couldn't update Glade's key bindings", e);
		}
	}
}
