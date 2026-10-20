package net.tuffclient;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Everything Glade adds to a player's nametag (the cosmetics badge, tiers), in one place for every version's hook. */
public final class NameTags {
	private NameTags() {
	}

	public static Component decorate(Entity entity, Component name) {
		var modules = TuffClient.modules();
		if (modules == null || name == null || !(entity instanceof Player p)) return name;
		Component out = modules.tiers.decorate(p, name);
		var cosmetics = modules.cosmetics;
		if (cosmetics.showBadge(p)) out = Component.empty().append(cosmetics.badge()).append(out);
		return out;
	}
}
