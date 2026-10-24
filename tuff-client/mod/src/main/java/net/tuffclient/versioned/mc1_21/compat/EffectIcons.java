// glade: minecraft <1.21.6
package net.tuffclient.compat;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

/** The GUI sprite for a status effect's icon, or null when this version has none. */
public final class EffectIcons {
	private EffectIcons() {
	}

	public static Identifier sprite(Holder<MobEffect> effect) {
		return null;
	}
}
