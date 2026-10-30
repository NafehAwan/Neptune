package net.tuffclient;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.tuffclient.gui.Gfx;
import com.mojang.blaze3d.platform.InputConstants;

/** Registers Glade's key bindings, HUD and commands with this Minecraft version's Fabric API. */
public final class Platform {
	private Platform() {
	}

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "tuff"));

	/** Menu and freelook keys, in that order. */
	public static KeyMapping[] registerKeys() {
		return new KeyMapping[] {
				KeyBindingHelper.registerKeyBinding(new KeyMapping("key.tuff.menu", InputConstants.KEY_RSHIFT, CATEGORY)),
				KeyBindingHelper.registerKeyBinding(new KeyMapping("key.tuff.freelook", InputConstants.KEY_F4, CATEGORY)),
		};
	}

	public static void registerHud(ModuleManager modules) {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "hud"), (graphics, delta) -> {
			net.tuffclient.module.hud.Clicks.sample(Minecraft.getInstance());
			modules.renderHud(Gfx.of(graphics));
		});
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, delta) -> {
			Minecraft client = Minecraft.getInstance();
			if (!modules.crosshair.isEnabled()) {
				original.render(graphics, delta);
			} else if (modules.crosshair.shows(client) && !Mc.hudHidden()) {
				modules.crosshair.render(Gfx.of(graphics));
			}
		});
	}
}
