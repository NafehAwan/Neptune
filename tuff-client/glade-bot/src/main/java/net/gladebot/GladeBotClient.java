package net.gladebot;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** The B key opens the bot settings. */
public final class GladeBotClient implements ClientModInitializer {
	private static KeyMapping menuKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("gladebot", "gladebot"));
		menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.gladebot.menu", InputConstants.KEY_B, category));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (menuKey.consumeClick()) {
				if (client.screen == null && client.player != null) client.setScreen(new BotScreen());
			}
		});
	}
}
