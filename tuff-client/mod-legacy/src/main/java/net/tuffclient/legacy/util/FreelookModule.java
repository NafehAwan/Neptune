package net.tuffclient.legacy.util;

import net.minecraft.client.MinecraftClient;
import net.tuffclient.legacy.module.Category;
import net.tuffclient.legacy.module.Module;
import net.tuffclient.legacy.setting.BoolSetting;

/** Hold the freelook key to look around in third person while you keep walking the same way. */
public final class FreelookModule extends Module {
	public final BoolSetting invert = add(new BoolSetting("invert", "Invert up and down", false));
	private boolean active;
	private int previousPerspective;
	public float yaw, pitch;

	public FreelookModule() {
		super("freelook", "Freelook", "Hold F4 (change it in Controls) to look around while moving straight.", Category.UTILITY, true);
	}

	public boolean active() {
		return active;
	}

	public void update(MinecraftClient mc, boolean want) {
		if (mc.player == null || mc.currentScreen != null && !active) want = false;
		if (want && !active) {
			active = true;
			yaw = mc.player.yaw;
			pitch = mc.player.pitch;
			previousPerspective = mc.options.perspective;
			mc.options.perspective = 1;
		} else if (!want && active) {
			active = false;
			mc.options.perspective = previousPerspective;
		}
	}

	/** Mouse movement turns the camera instead of the player while active. */
	public void turn(float dx, float dy) {
		yaw += dx * 0.15f;
		pitch = Math.max(-90, Math.min(90, pitch + (invert.get() ? dy : -dy) * 0.15f));
	}
}
