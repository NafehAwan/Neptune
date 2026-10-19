package net.tuffclient.legacy;

import net.fabricmc.api.ClientModInitializer;
import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.legacyfabric.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.legacyfabric.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.tuffclient.legacy.gui.MenuScreen;
import net.tuffclient.legacy.module.ModuleManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

/** Glade for Minecraft 1.8.9 (Legacy Fabric). */
public final class GladeLegacy implements ClientModInitializer {
	public static final String MOD_ID = "tuff";
	public static final Logger LOGGER = LogManager.getLogger("Glade");
	private static ModuleManager modules;
	private static KeyBinding menuKey, zoomKey, freelookKey;

	public static ModuleManager modules() {
		return modules;
	}

	@Override
	public void onInitializeClient() {
		modules = new ModuleManager();
		menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("Glade menu", Keyboard.KEY_RSHIFT, "Glade"));
		zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("Zoom", Keyboard.KEY_C, "Glade"));
		freelookKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("Freelook", Keyboard.KEY_LMENU, "Glade"));

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> Config.load(modules));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Config.save(modules));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			modules.tick(client);
			while (menuKey.wasPressed()) {
				if (client.currentScreen == null) client.setScreen(new MenuScreen());
			}
			modules.zoom.setHeld(zoomKey.isPressed() && client.currentScreen == null);
			modules.freelook.update(client, freelookKey.isPressed() && modules.freelook.isEnabled());
		});
		HudRenderCallback.EVENT.register((client, tickDelta) -> modules.renderHud(client));
		LOGGER.info("Glade loaded for Minecraft 1.8.9 with {} modules", modules.all().size());
	}
}
