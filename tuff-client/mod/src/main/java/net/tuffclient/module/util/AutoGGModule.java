package net.tuffclient.module.util;

import net.minecraft.client.Minecraft;
import net.tuffclient.TuffClient;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.NumberSetting;
import net.tuffclient.setting.TextSetting;

/** Says "gg" when a game ends on servers like Hypixel. Only use it where the server allows it. */
public final class AutoGGModule extends Module {
	private static final String[] ENDINGS = {"1st Killer", "1st Place", "Winner:", "WINNER!", "Winners:", "Victory!", "won the game", "Reward Summary", "Top Killer", "1st -"};
	private final TextSetting message = add(new TextSetting("message", "Message", "gg", 64));
	private final TextSetting trigger = add(new TextSetting("trigger", "Extra trigger phrase (optional)", "", 64));
	private final NumberSetting delay = add(new NumberSetting("delay", "Delay (seconds)", 1, 0, 5, 0.5));
	private long sendAt = -1;
	private long lastSent;

	public AutoGGModule() {
		super("auto_gg", "Auto GG", "Says gg when a game ends. Only on servers that allow it.", Category.UTILITY, false);
	}

	/** Every chat line passes through here. */
	public static void onChat(String text) {
		AutoGGModule m = TuffClient.modules() == null ? null : TuffClient.modules().autoGG;
		if (m == null || !m.isEnabled() || Minecraft.getInstance().getCurrentServer() == null) return;
		boolean ended = false;
		for (String e : ENDINGS) ended |= text.contains(e);
		String extra = m.trigger.get().trim();
		if (!extra.isEmpty() && text.contains(extra)) ended = true;
		long now = System.currentTimeMillis();
		if (ended && now - m.lastSent > 10_000 && m.sendAt < 0) m.sendAt = now + Math.round(m.delay.get() * 1000);
	}

	@Override
	public void tick(Minecraft client) {
		if (sendAt < 0 || System.currentTimeMillis() < sendAt) return;
		sendAt = -1;
		lastSent = System.currentTimeMillis();
		String text = message.get().trim();
		if (!text.isEmpty() && client.getConnection() != null) client.getConnection().sendChat(text);
	}
}
