// glade: minecraft <1.21.11
package net.tuffclient.compat;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** A registry key's id (ResourceKey.location() became identifier() in 1.21.11). */
public final class ResourceKeys {
	private ResourceKeys() {
	}

	public static Identifier id(ResourceKey<?> key) {
		return key.location();
	}
}
