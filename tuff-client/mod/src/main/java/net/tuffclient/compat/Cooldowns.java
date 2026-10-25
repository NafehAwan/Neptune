// glade: minecraft >=1.21.2
package net.tuffclient.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Item cooldowns (keyed by item before 1.21.2, by stack since). */
public final class Cooldowns {
	private Cooldowns() {
	}

	/** Share of the cooldown still to go, 0 when the item is ready. */
	public static float left(Player player, ItemStack stack) {
		return player.getCooldowns().getCooldownPercent(stack, 0);
	}
}
