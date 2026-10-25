package net.tuffclient.module.util;

import net.minecraft.client.Minecraft;
import net.tuffclient.TuffClient;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.KeybindSetting;
import net.tuffclient.setting.TextSetting;

/** Keys that send a chat message or a command (start it with /). */
public final class ChatMacrosModule extends Module {
	private static final int SLOTS = 6;
	private final TextSetting[] text = new TextSetting[SLOTS];
	private int cooldown;

	public ChatMacrosModule() {
		super("chat_macros", "Chat Macros", "Keys that send a message or a command, like /spawn or gg.", Category.UTILITY, false);
		String[] examples = {"gg", "/spawn", "", "", "", ""};
		for (int i = 0; i < SLOTS; i++) {
			String name = "macro_" + (i + 1);
			text[i] = add(new TextSetting(name + "_text", "Macro " + (i + 1), examples[i], 256));
			add(new KeybindSetting(name + "_key", "Macro " + (i + 1) + " key", () -> TuffClient.key(name)));
		}
	}

	@Override
	public void tick(Minecraft mc) {
		if (cooldown > 0) cooldown--;
		for (int i = 0; i < SLOTS; i++) {
			var k = TuffClient.key("macro_" + (i + 1));
			if (k == null) continue;
			while (k.consumeClick()) {
				String msg = text[i].get().trim();
				// one message every half second, so a held key can't spam the server
				if (msg.isEmpty() || mc.player == null || cooldown > 0) continue;
				if (msg.startsWith("/")) mc.player.connection.sendCommand(msg.substring(1));
				else mc.player.connection.sendChat(msg);
				cooldown = 10;
			}
		}
	}
}
