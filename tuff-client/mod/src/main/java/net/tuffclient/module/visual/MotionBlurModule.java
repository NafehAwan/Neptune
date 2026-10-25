package net.tuffclient.module.visual;

import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.ChoiceSetting;

/**
 * Motion blur: each frame is blended with the frames before it (kept in a
 * persistent target of a small post effect shipped with Glade). Runs after
 * the world and before the HUD, so the HUD stays sharp. Minecraft 1.21.6+.
 */
public final class MotionBlurModule extends Module {
	private final ChoiceSetting strength = add(new ChoiceSetting("strength", "Strength", 1, "Low", "Medium", "High"));

	public MotionBlurModule() {
		super("motion_blur", "Motion Blur", "Smooth blur when you turn, like Lunar's motion blur.", Category.VISUAL, false);
		requires("MotionBlurMixin");
	}

	/** 0 low, 1 medium, 2 high. */
	public int level() {
		return strength.get();
	}

	/** Whether the blur effect loads on this version (shader and settings file). */
	public boolean loaded() {
		return net.tuffclient.compat.MotionBlurFormat.loaded(level());
	}
}
