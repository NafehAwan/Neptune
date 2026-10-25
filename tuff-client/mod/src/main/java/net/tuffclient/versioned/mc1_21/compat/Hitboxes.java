// glade: minecraft <1.21.9
package net.tuffclient.compat;

import net.minecraft.client.Minecraft;

/** The game's hitbox view (F3+B). Since 1.21.9 it's an entry in the debug options. */
public final class Hitboxes {
	private static Boolean before;

	private Hitboxes() {
	}

	public static void show(boolean on) {
		var d = Minecraft.getInstance().getEntityRenderDispatcher();
		if (on) {
			if (before == null) before = d.shouldRenderHitBoxes();
			d.setRenderHitBoxes(true);
		} else if (before != null) {
			d.setRenderHitBoxes(before);
			before = null;
		}
	}
}
