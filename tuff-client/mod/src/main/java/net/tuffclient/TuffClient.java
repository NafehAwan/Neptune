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
		Platform.registerCommands();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			unbindOldDefaultsOnce(client);
			modules.tick(client);
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
	 * Glade used to bind Zoom (C), Waypoint (B) and Freelook (Left Alt) for you;
	 * now they start unbound and you pick them in Controls. Clears the old
	 * bindings once, after the options have loaded.
	 */
	private static void unbindOldDefaultsOnce(net.minecraft.client.Minecraft client) {
		if (keysChecked) return;
		keysChecked = true;
		java.nio.file.Path marker = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("tuff").resolve("keys-unbound");
		if (java.nio.file.Files.exists(marker)) return;
		try {
			for (KeyMapping key : new KeyMapping[] {zoomKey, waypointKey, freelookKey}) {
				key.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
			}
			KeyMapping.resetMapping();
			client.options.save();
			java.nio.file.Files.createDirectories(marker.getParent());
			java.nio.file.Files.writeString(marker, "Glade's zoom, waypoint and freelook keys start unbound.\n");
		} catch (Exception e) {
			LOGGER.warn("Couldn't clear Glade's old key bindings", e);
		}
	}
}
