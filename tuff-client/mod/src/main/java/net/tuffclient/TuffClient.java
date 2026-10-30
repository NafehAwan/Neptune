package net.tuffclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.tuffclient.gui.GladeMenuScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TuffClient implements ClientModInitializer {
	public static final String MOD_ID = "tuff";
	public static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
	public static final Logger LOGGER = LoggerFactory.getLogger("Glade");
	private static ModuleManager modules;
	private static KeyMapping menuKey;
	private static KeyMapping freelookKey;

	public static KeyMapping freelookKey() {
		return freelookKey;
	}

	public static ModuleManager modules() {
		return modules;
	}

	/** Right Shift by default: opens Glade's settings. */
	public static KeyMapping menuKey() {
		return menuKey;
	}

	@Override
	public void onInitializeClient() {
		modules = new ModuleManager();
		TuffConfig.load(modules);

		KeyMapping[] keys = Platform.registerKeys();
		menuKey = keys[0];
		freelookKey = keys[1];
		net.tuffclient.module.visual.ClearGlassModule.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			modules.tick(client);
			while (menuKey.consumeClick()) {
				if (net.tuffclient.Mc.screen() == null) net.tuffclient.Mc.setScreen(new GladeMenuScreen(null));
			}
			modules.freelook.update(client, freelookKey.isDown() && modules.freelook.isEnabled());
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			if (modules.hitboxes.isEnabled()) net.tuffclient.compat.Hitboxes.show(false);
			TuffConfig.save(modules);
		});

		Platform.registerHud(modules);
		LOGGER.info("Glade loaded with {} modules", modules.all().size());
		HookCheck.runIfAsked();
	}
}
