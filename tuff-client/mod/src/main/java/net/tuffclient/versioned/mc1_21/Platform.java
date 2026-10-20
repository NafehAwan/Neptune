// glade: minecraft <1.21.6
package net.tuffclient;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
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

	/** Menu, zoom, waypoint, freelook and clip keys, in that order. */
	public static KeyMapping[] registerKeys() {
		String category = "key.categories.tuff";
		return new KeyMapping[] {
				KeyBindingHelper.registerKeyBinding(new KeyMapping("key.tuff.menu", InputConstants.KEY_RSHIFT, category)),
				KeyBindingHelper.registerKeyBinding(new KeyMapping("key.tuff.zoom", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), category)),
				KeyBindingHelper.registerKeyBinding(new KeyMapping("key.tuff.waypoint", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), category)),
				KeyBindingHelper.registerKeyBinding(new KeyMapping("key.tuff.clip", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), category)),
				KeyBindingHelper.registerKeyBinding(new KeyMapping("key.tuff.freelook", InputConstants.KEY_F4, category)),
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
		HudRenderCallback.EVENT.register((graphics, delta) -> {
			CpsModule.sample(Minecraft.getInstance());
			modules.renderHud(Gfx.of(graphics));
		});
	}
}
