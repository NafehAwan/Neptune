// glade: minecraft >=1.21.11
package net.tuffclient.compat;

import net.minecraft.client.Minecraft;

/** Whether the F3 debug screen is open. */
public final class DebugOverlay {
	private DebugOverlay() {
	}

	public static boolean visible() {
		return Minecraft.getInstance().debugEntries.isOverlayVisible();
	}
}
