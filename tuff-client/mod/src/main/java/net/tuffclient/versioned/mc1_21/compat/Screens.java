// glade: minecraft <26.1
package net.tuffclient.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;

/** The game's Options screen. */
public final class Screens {
	private Screens() {
	}

	public static Screen options(Screen parent) {
		return new OptionsScreen(parent, Minecraft.getInstance().options);
	}
}
