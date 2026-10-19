package net.tuffclient.module.visual;

import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.NumberSetting;

/** Move and scale your hand and held items in first person, each hand on its own. */
public final class HandViewModule extends Module {
	public final NumberSetting mainX = add(new NumberSetting("main_x", "Main hand left / right", 0, -1, 1, 0.05));
	public final NumberSetting mainY = add(new NumberSetting("main_y", "Main hand down / up", 0, -1, 1, 0.05));
	public final NumberSetting mainZ = add(new NumberSetting("main_z", "Main hand near / far", 0, -1, 1, 0.05));
	public final NumberSetting mainScale = add(new NumberSetting("main_scale", "Main hand size (%)", 100, 30, 150, 5));
	public final NumberSetting offX = add(new NumberSetting("off_x", "Offhand left / right", 0, -1, 1, 0.05));
	public final NumberSetting offY = add(new NumberSetting("off_y", "Offhand down / up", 0, -1, 1, 0.05));
	public final NumberSetting offZ = add(new NumberSetting("off_z", "Offhand near / far", 0, -1, 1, 0.05));
	public final NumberSetting offScale = add(new NumberSetting("off_scale", "Offhand size (%)", 100, 30, 150, 5));

	public HandViewModule() {
		super("hand_view", "Hand & Item View", "Move and resize your hand and held items in first person.", Category.VISUAL, false);
	}
}
