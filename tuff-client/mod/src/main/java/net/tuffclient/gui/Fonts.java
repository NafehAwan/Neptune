// glade: minecraft >=1.21.9
package net.tuffclient.gui;

import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.FontDescription;

/** Sets a text style's font (the font reference type changed in 1.21.9). */
public final class Fonts {
	private Fonts() {
	}

	public static Style withFont(Style style, Identifier font) {
		return style.withFont(new FontDescription.Resource(font));
	}
}
