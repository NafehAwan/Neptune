// glade: minecraft <26.2
package net.tuffclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Small things that moved between Minecraft versions. */
public final class Mc {
	private Mc() {
	}

	public static Minecraft get() {
		return Minecraft.getInstance();
	}

	/** The open screen, or null in game. */
	public static Screen screen() {
		return get().screen;
	}

	public static void setScreen(Screen screen) {
		get().setScreen(screen);
	}

	/** F1: the HUD is hidden. */
	public static boolean hudHidden() {
		return get().options.hideGui;
	}

	/** The tab list overlay. */
	public static net.minecraft.client.gui.components.PlayerTabOverlay tabList() {
		return get().gui.getTabList();
	}
}
