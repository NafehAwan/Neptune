// glade: minecraft >=26.1 <26.3
package net.tuffclient;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.tuffclient.gui.Gfx;
import net.tuffclient.module.hud.CpsModule;
import net.tuffclient.module.util.ScreenshotToolsModule;
import com.mojang.blaze3d.platform.InputConstants;

/** Registers Glade's key bindings, HUD and commands with this Minecraft version's Fabric API. */
public final class Platform {
	private Platform() {
	}

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "tuff"));

	/** An extra Glade key, unbound until the player sets one. */
	public static KeyMapping registerKey(String name) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.tuff." + name, InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), CATEGORY));
	}

	/** Menu, zoom, waypoint, freelook and clip keys, in that order. */
	public static KeyMapping[] registerKeys() {
		return new KeyMapping[] {
				KeyMappingHelper.registerKeyMapping(new KeyMapping("key.tuff.menu", InputConstants.KEY_RSHIFT, CATEGORY)),
				KeyMappingHelper.registerKeyMapping(new KeyMapping("key.tuff.zoom", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), CATEGORY)),
				KeyMappingHelper.registerKeyMapping(new KeyMapping("key.tuff.waypoint", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), CATEGORY)),
				KeyMappingHelper.registerKeyMapping(new KeyMapping("key.tuff.freelook", InputConstants.KEY_F4, CATEGORY)),
				KeyMappingHelper.registerKeyMapping(new KeyMapping("key.tuff.clip", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), CATEGORY)),
		};
	}

	public static void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registries) -> dispatcher.register(
				ClientCommands.literal("glade").then(ClientCommands.literal("screenshot").then(ClientCommands.literal("delete")
						.then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
							ctx.getSource().sendFeedback(Component.literal(ScreenshotToolsModule.delete(StringArgumentType.getString(ctx, "name"))));
							return 1;
						}))))));
	}

	public static void registerHud(ModuleManager modules) {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "hud"), (graphics, delta) -> {
			CpsModule.sample(Minecraft.getInstance());
			modules.renderHud(Gfx.of(graphics));
		});
		HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, original -> (graphics, delta) -> {
			if (!modules.scoreboard.hidden()) original.extractRenderState(graphics, delta);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, delta) -> {
			Minecraft client = Minecraft.getInstance();
			if (!modules.crosshair.isEnabled()) {
				original.extractRenderState(graphics, delta);
			} else if (modules.crosshair.shows(client) && !Mc.hudHidden()) {
				modules.crosshair.render(Gfx.of(graphics));
			}
		});
	}
}
