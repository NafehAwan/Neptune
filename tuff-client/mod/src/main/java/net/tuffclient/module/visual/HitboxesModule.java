package net.tuffclient.module.visual;

import net.minecraft.client.Minecraft;
import net.tuffclient.compat.Hitboxes;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;

/** Always shows hitboxes (like F3+B), so you can see exactly where your hits land. */
public final class HitboxesModule extends Module {
	public HitboxesModule() {
		super("hitboxes", "Hitboxes", "Shows player and mob hitboxes, like F3+B, without the key.", Category.VISUAL, false);
	}

	@Override
	public void tick(Minecraft client) {
		if (client.level != null) Hitboxes.show(true);
	}

	@Override
	protected void onDisable() {
		Hitboxes.show(false);
	}
}
