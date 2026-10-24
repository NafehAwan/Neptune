// glade: minecraft <1.21.9
package net.tuffclient.account;

import net.minecraft.resources.Identifier;

/** Skin textures for account heads; before 1.21.9 every head shows Steve. */
public final class SkinTextures {
	public static final Identifier STEVE = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

	private SkinTextures() {
	}

	public static Identifier of(Accounts.Account account) {
		return STEVE;
	}
}
