package net.tuffclient.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.resources.Identifier;

/** The game's hitbox view (F3+B). Since 1.21.9 it's an entry in the debug options. */
public final class Hitboxes {
	private static final Identifier ENTRY = net.minecraft.client.gui.components.debug.DebugScreenEntries.ENTITY_HITBOXES;
	private static DebugScreenEntryStatus before;

	private Hitboxes() {
	}

	public static void show(boolean on) {
		var list = Minecraft.getInstance().debugEntries;
		if (on) {
			if (before == null) before = list.getStatus(ENTRY);
			if (list.getStatus(ENTRY) != DebugScreenEntryStatus.ALWAYS_ON) {
				list.setStatus(ENTRY, DebugScreenEntryStatus.ALWAYS_ON);
				// what's drawn follows the list of active entries, built from these statuses
				list.rebuildCurrentList();
			}
		} else if (list.getStatus(ENTRY) == DebugScreenEntryStatus.ALWAYS_ON) {
			// back to how it was before the module turned it on (off if we never saw it otherwise)
			list.setStatus(ENTRY, before == null || before == DebugScreenEntryStatus.ALWAYS_ON ? DebugScreenEntryStatus.NEVER : before);
			list.rebuildCurrentList();
			before = null;
		}
	}
}
