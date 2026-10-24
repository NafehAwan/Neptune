package net.tuffclient.module.visual;

import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;

/** 1.8-style first-person animations: the held item never dips. */
public final class OldAnimationsModule extends Module {
	/** Your item stays up: no dip when switching items or after each attack (the 1.8 feel). */
	public final BoolSetting noDips = add(new BoolSetting("no_dips", "No item dip when switching or attacking", true));

	public OldAnimationsModule() {
		super("old_animations", "Old Animations", "1.8-style first person: your item stays up, no dip when switching or attacking.", Category.VISUAL, false);
		requires("FirstPersonHandsMixin");
	}
}
