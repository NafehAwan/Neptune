package net.tuffclient.module.visual;

import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;

/** 1.7-style first-person animations. */
public final class OldAnimationsModule extends Module {
	public final BoolSetting noEquipDip = add(new BoolSetting("no_equip_dip", "No dip when switching items", true));
	public final BoolSetting noCooldownDip = add(new BoolSetting("no_cooldown_dip", "No dip after attacking", true));
	public final BoolSetting swingWhileUsing = add(new BoolSetting("swing_while_using", "Swing while eating or blocking", true));

	public OldAnimationsModule() {
		super("old_animations", "Old Animations", "1.7-style animations: no item dips, swing while eating or blocking.", Category.VISUAL, false);
	}
}
