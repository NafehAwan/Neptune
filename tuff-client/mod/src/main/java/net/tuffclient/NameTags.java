package net.tuffclient;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Everything Glade adds to a player's nametag (tiers), in one place for every version's hook. */
public final class NameTags {
	private NameTags() {
	}

	public static Component decorate(Entity entity, Component name) {
		var modules = TuffClient.modules();
		if (modules == null || name == null) return name;
		if (!(entity instanceof Player p)) return name;
		return modules.tiers.decorate(p, name);
	}
}
