package net.tuffclient.legacy;

import net.minecraft.client.option.KeyBinding;
import org.lwjgl.input.Keyboard;

/** Glade's key bindings; added to the Controls screen by GameOptionsMixin. */
public final class Keys {
	public static final KeyBinding MENU = new KeyBinding("Glade menu", Keyboard.KEY_RSHIFT, "Glade");
	public static final KeyBinding ZOOM = new KeyBinding("Zoom", Keyboard.KEY_NONE, "Glade");
	public static final KeyBinding FREELOOK = new KeyBinding("Freelook", Keyboard.KEY_F4, "Glade");
	public static final KeyBinding[] ALL = {MENU, ZOOM, FREELOOK};

	private Keys() {
	}
}
