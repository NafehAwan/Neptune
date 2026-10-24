// glade: minecraft >=26.1
package net.tuffclient.compat;

import net.minecraft.client.multiplayer.ClientLevel;

/** The overworld's time of day in ticks (worlds got clocks in 26.1). */
public final class WorldTime {
	private WorldTime() {
	}

	public static long dayTime(ClientLevel level) {
		return level.getOverworldClockTime();
	}

	/** Shows this time of day on the client (26.1+ does it through the world clock mixin). */
	public static void force(ClientLevel level, long dayTicks) {
	}
}
