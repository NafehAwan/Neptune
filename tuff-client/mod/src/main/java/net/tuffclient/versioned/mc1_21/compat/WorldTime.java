// glade: minecraft <26.1
package net.tuffclient.compat;

import net.minecraft.client.multiplayer.ClientLevel;

/** The overworld's time of day in ticks (worlds got clocks in 26.1). */
public final class WorldTime {
	private WorldTime() {
	}

	public static long dayTime(ClientLevel level) {
		return level.getDayTime();
	}

	/** Shows this time of day on the client, keeping the day count; the server corrects it again when Glade stops. */
	public static void force(ClientLevel level, long dayTicks) {
		var data = (ClientLevel.ClientLevelData) level.getLevelData();
		long t = data.getDayTime();
		data.setDayTime(t - Math.floorMod(t, 24000L) + dayTicks);
	}
}
