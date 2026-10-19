package net.tuffclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.tuffclient.gui.TuffMenuScreen;
import net.tuffclient.module.hud.CpsModule;
import net.tuffclient.module.util.ScreenshotToolsModule;
import org.lwjgl.sdl.SDLScancode;
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

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "tuff"));
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.tuff.menu", SDLScancode.SDL_SCANCODE_RSHIFT, category));
		zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.tuff.zoom", SDLScancode.SDL_SCANCODE_C, category));
		waypointKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.tuff.waypoint", SDLScancode.SDL_SCANCODE_B, category));
		freelookKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.tuff.freelook", SDLScancode.SDL_SCANCODE_LALT, category));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registries) -> dispatcher.register(
				ClientCommands.literal("glade").then(ClientCommands.literal("screenshot").then(ClientCommands.literal("delete")
						.then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
							ctx.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(ScreenshotToolsModule.delete(StringArgumentType.getString(ctx, "name"))));
							return 1;
						}))))));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			modules.tick(client);
			while (menuKey.consumeClick()) {
				if (client.gui.screen() == null) client.gui.setScreen(new TuffMenuScreen(null));
			}
			modules.zoom.setHeld(zoomKey.isDown() && client.gui.screen() == null);
			while (waypointKey.consumeClick()) {
				if (client.gui.screen() == null && modules.waypoints.isEnabled()) modules.waypoints.addHere();
			}
			modules.freelook.update(client, freelookKey.isDown() && modules.freelook.isEnabled());
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> TuffConfig.save(modules));

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), (graphics, delta) -> {
			CpsModule.sample(Minecraft.getInstance());
			modules.renderHud(graphics);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, original -> (graphics, delta) -> {
			if (!modules.hideScoreboard.isEnabled()) original.extractRenderState(graphics, delta);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, delta) -> {
			Minecraft client = Minecraft.getInstance();
			if (!modules.crosshair.isEnabled()) {
				original.extractRenderState(graphics, delta);
			} else if (client.options.getCameraType().isFirstPerson() && !client.gui.hud.isHidden()) {
				modules.crosshair.render(graphics);
			}
		});
		LOGGER.info("Glade loaded with {} modules", modules.all().size());
	}
}
