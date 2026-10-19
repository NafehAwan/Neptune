package net.tuffclient.module.visual;

import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.NumberSetting;

/** A smaller, lower shield (and totem) in first person, so they block less of the view. */
public final class SmallShieldModule extends Module {
	public final NumberSetting scale = add(new NumberSetting("scale", "Size (%)", 70, 30, 100, 5));
	public final NumberSetting lower = add(new NumberSetting("lower", "Lower by", 15, 0, 50, 5));
	public final BoolSetting totems = add(new BoolSetting("totems", "Totems too", true));

	public SmallShieldModule() {
		super("small_shield", "Small Shield", "A smaller, lower shield and totem in first person.", Category.VISUAL, false);
	}
}
