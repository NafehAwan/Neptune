package net.tuffclient.legacy;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.tuffclient.legacy.gui.MenuScreen;
import net.tuffclient.legacy.module.ModuleManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Glade for Minecraft 1.8.8 and 1.8.9 (Legacy Fabric). It needs only the
 * loader: its own mixins hook the tick, the HUD and the key bindings.
 */
public final class GladeLegacy implements ClientModInitializer {
	public static final String MOD_ID = "tuff";
	public static final Logger LOGGER = LogManager.getLogger("Glade");
	private static ModuleManager modules;

	public static ModuleManager modules() {
		return modules;
	}

	@Override
	public void onInitializeClient() {
		modules = new ModuleManager();
		Config.load(modules);
		LOGGER.info("Glade loaded for Minecraft 1.8 with {} modules", modules.all().size());
		HookCheck.runIfAsked();
	}

	/** End of every client tick (MinecraftClientMixin). */
	public static void tick(MinecraftClient client) {
		if (modules == null) return;
		unbindOldDefaultsOnce(client);
		modules.tick(client);
		while (Keys.MENU.wasPressed()) {
			if (client.currentScreen == null) client.setScreen(new MenuScreen());
		}
		modules.zoom.setHeld(Keys.ZOOM.isPressed() && client.currentScreen == null);
		modules.freelook.update(client, Keys.FREELOOK.isPressed() && modules.freelook.isEnabled());
	}

	/** The game is closing (MinecraftClientMixin). */
	public static void stopping() {
		if (modules != null) Config.save(modules);
	}

	private static boolean keysChecked;

	/** Zoom starts unbound (it was C) and Freelook is F4 (it was Left Alt, then unbound). Moves players over once. */
	private static void unbindOldDefaultsOnce(MinecraftClient client) {
		if (keysChecked) return;
		keysChecked = true;
		java.io.File dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("tuff").toFile();
		java.io.File v1 = new java.io.File(dir, "keys-unbound-1.8"), v2 = new java.io.File(dir, "keys-f4-1.8");
		if (v2.exists()) return;
		boolean fromOld = !v1.exists();
		if (fromOld) Keys.ZOOM.setCode(0);
		if (fromOld || Keys.FREELOOK.getCode() == 0) Keys.FREELOOK.setCode(org.lwjgl.input.Keyboard.KEY_F4);
		net.minecraft.client.option.KeyBinding.updateKeysByCode();
		client.options.save();
		try {
			dir.mkdirs();
			v1.createNewFile();
			v2.createNewFile();
		} catch (java.io.IOException e) {
			LOGGER.warn("Couldn't remember Glade's key change", e);
		}
	}
}
