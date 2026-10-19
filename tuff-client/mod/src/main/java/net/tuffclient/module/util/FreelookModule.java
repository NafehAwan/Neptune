package net.tuffclient.module.util;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;

/**
 * Hold Left Alt (by default) to look around your character without changing
 * where you walk. The camera moves to third person while held.
 */
public final class FreelookModule extends Module {
	private final BoolSetting toggle = add(new BoolSetting("toggle", "Press to toggle instead of hold", false));
	private final BoolSetting invert = add(new BoolSetting("invert", "Invert up and down", false));
	private final BoolSetting thirdPerson = add(new BoolSetting("third_person", "Switch to third person", true));
	private boolean active;
	private boolean wasDown;
	private CameraType previous;
	private float yaw;
	private float pitch;

	public FreelookModule() {
		super("freelook", "Freelook", "Hold Left Alt to look around without turning your character.", Category.UTILITY, true);
	}

	public boolean active() {
		return isEnabled() && active;
	}

	public float yaw() {
		return yaw;
	}

	public float pitch() {
		return pitch;
	}

	/** Mouse movement while freelooking turns the camera only (same maths as Entity.turn). */
	public void turn(double dx, double dy) {
		yaw += (float) dx * 0.15f;
		pitch = Math.max(-90, Math.min(90, pitch + (float) (invert.get() ? -dy : dy) * 0.15f));
	}

	/** Called every tick with whether the key is down. */
	public void update(Minecraft mc, boolean down) {
		boolean want = toggle.get() ? (down && !wasDown ? !active : active) : down;
		wasDown = down;
		if (mc.player == null || mc.gui.screen() != null && !active) want = false;
		if (want == active) return;
		active = want;
		if (active) {
			yaw = mc.player.getYRot();
			pitch = mc.player.getXRot();
			previous = mc.options.getCameraType();
			if (thirdPerson.get() && previous.isFirstPerson()) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		} else if (previous != null) {
			mc.options.setCameraType(previous);
			previous = null;
		}
	}

	@Override
	protected void onDisable() {
		if (active) update(Minecraft.getInstance(), false);
	}
}
