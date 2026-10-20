// glade: minecraft >=1.21.9 <26.1
package net.tuffclient;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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

	/** Menu, zoom, waypoint and freelook keys, in that order. */
	public static KeyMapping[] registerKeys() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "tuff"));
		return new KeyMapping[] {
				KeyBindingHelper.registerKeyBinding(new KeyMapping("key.tuff.menu", InputConstants.KEY_RSHIFT, category)),
				KeyBindingHelper.registerKeyBinding(new KeyMapping("key.tuff.zoom", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), category)),
				KeyBindingHelper.registerKeyBinding(new KeyMapping("key.tuff.waypoint", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), category)),
				KeyBindingHelper.registerKeyBinding(new KeyMapping("key.tuff.freelook", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), category)),
		};
	}

	public static void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registries) -> dispatcher.register(
				ClientCommandManager.literal("glade").then(ClientCommandManager.literal("screenshot").then(ClientCommandManager.literal("delete")
						.then(ClientCommandManager.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
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
			if (!modules.hideScoreboard.isEnabled()) original.render(graphics, delta);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, delta) -> {
			Minecraft client = Minecraft.getInstance();
			if (!modules.crosshair.isEnabled()) {
				original.render(graphics, delta);
			} else if (client.options.getCameraType().isFirstPerson() && !Mc.hudHidden()) {
				modules.crosshair.render(Gfx.of(graphics));
			}
		});
	}
}
