package net.tuffclient.module.visual;

import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;

/** In game: no dark tint behind inventories and chests, and optionally no blur behind menus. */
public final class MenuBackgroundModule extends Module {
	public final BoolSetting tint = add(new BoolSetting("tint", "Remove the dark tint behind inventories", true));
	public final BoolSetting blur = add(new BoolSetting("blur", "Remove the blur behind menus", false));

	public MenuBackgroundModule() {
		super("menu_background", "Menu Background", "See the game behind your inventory: no dark tint, and no blur if you like.", Category.VISUAL, false);
	}
}
