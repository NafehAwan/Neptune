// glade: minecraft >=1.21.9
package net.tuffclient.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.resources.Identifier;

/** The game's hitbox view (F3+B). Since 1.21.9 it's an entry in the debug options. */
public final class Hitboxes {
	private static final Identifier ENTRY = Identifier.withDefaultNamespace("entity_hitboxes");
	private static DebugScreenEntryStatus before;

	private Hitboxes() {
	}

	public static void show(boolean on) {
		var list = Minecraft.getInstance().debugEntries;
		if (on) {
			if (before == null) before = list.getStatus(ENTRY);
			if (list.getStatus(ENTRY) != DebugScreenEntryStatus.ALWAYS_ON) list.setStatus(ENTRY, DebugScreenEntryStatus.ALWAYS_ON);
		} else if (before != null) {
			list.setStatus(ENTRY, before);
			before = null;
		}
	}
}
