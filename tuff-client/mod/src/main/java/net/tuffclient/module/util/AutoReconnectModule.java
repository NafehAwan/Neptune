package net.tuffclient.module.util;

import net.tuffclient.gui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.NumberSetting;

/** Rejoins the last server a few seconds after being disconnected. Click anywhere to cancel. */
public final class AutoReconnectModule extends Module {
	private final NumberSetting delay = add(new NumberSetting("delay", "Wait (seconds)", 5, 2, 60, 1));
	private final NumberSetting attempts = add(new NumberSetting("attempts", "Give up after (tries)", 3, 1, 10, 1));
	private ServerData last;
	private Screen countingOn;
	private long reconnectAt;
	private int tries;
	private boolean cancelled;

	public AutoReconnectModule() {
		super("auto_reconnect", "Auto Reconnect", "Rejoins the server a few seconds after you get disconnected.", Category.UTILITY, false);
	}

	@Override
	public void tick(Minecraft mc) {
		if (mc.getCurrentServer() != null && mc.level != null) {
			last = mc.getCurrentServer();
			tries = 0;
		}
		Screen screen = mc.gui.screen();
		if (!(screen instanceof DisconnectedScreen) || last == null) {
			countingOn = null;
			return;
		}
		if (countingOn != screen) {
			countingOn = screen;
			cancelled = tries >= attempts.get();
			reconnectAt = System.currentTimeMillis() + Math.round(delay.get() * 1000);
		}
		if (mc.mouseHandler.isLeftPressed() && System.currentTimeMillis() > reconnectAt - Math.round(delay.get() * 1000) + 300) cancelled = true;
		if (!cancelled && System.currentTimeMillis() >= reconnectAt) {
			tries++;
			countingOn = null;
			ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, ServerAddress.parseString(last.ip), last, false, null);
		}
	}

	/** Cancels the countdown (any click on the disconnected screen). */
	public void cancel() {
		cancelled = true;
	}

	/** The countdown line on the disconnected screen. */
	public void renderOn(Screen screen, Gfx g) {
		if (screen != countingOn || last == null) return;
		var font = Minecraft.getInstance().font;
		String text;
		if (cancelled) text = tries >= attempts.get() ? "Auto reconnect gave up after " + tries + " tries" : "Auto reconnect cancelled";
		else text = "Reconnecting to " + last.name + " in " + Math.max(0, (reconnectAt - System.currentTimeMillis() + 999) / 1000) + "s  (click to cancel)";
		g.centeredText(font, text, screen.width / 2, 12, 0xFFE3E8E1);
	}
}
