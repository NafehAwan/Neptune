package net.tuffclient.module.visual;

import net.tuffclient.module.Category;
import net.tuffclient.module.Module;

/** The player list (Tab) shows each player's ping in milliseconds instead of bars. */
public final class TabPingModule extends Module {
	public TabPingModule() {
		super("tab_ping", "Tab Ping", "Shows ping as a number in the player list, colored by how good it is.", Category.VISUAL, false);
		requires("TabPingMixin");
	}

	public static int color(int ms) {
		return ms <= 0 ? 0xFFAAAAAA : ms <= 75 ? 0xFF55FF55 : ms <= 150 ? 0xFFFFFF55 : ms <= 300 ? 0xFFFFAA00 : 0xFFFF5555;
	}
}
