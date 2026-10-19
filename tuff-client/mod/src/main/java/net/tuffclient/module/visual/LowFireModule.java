package net.tuffclient.module.visual;

import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.NumberSetting;

/** Lowers the fire overlay when you're burning so you can see. */
public final class LowFireModule extends Module {
	public final NumberSetting height = add(new NumberSetting("height", "Lower by (%)", 35, 0, 80, 5));

	public LowFireModule() {
		super("low_fire", "Low Fire", "Lowers the fire on your screen when you're burning.", Category.VISUAL, false);
	}
}
