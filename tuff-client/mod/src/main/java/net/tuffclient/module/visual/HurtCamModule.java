package net.tuffclient.module.visual;

import net.minecraft.client.Minecraft;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.module.OptionOverride;
import net.tuffclient.setting.NumberSetting;

/** How much the screen shakes when you take damage (the game's damage tilt). */
public final class HurtCamModule extends Module {
	private final NumberSetting strength = add(new NumberSetting("strength", "Shake (%)", 0, 0, 100, 5));
	private final OptionOverride<Double> tilt = new OptionOverride<>(o -> o.damageTiltStrength());

	public HurtCamModule() {
		super("hurt_cam", "Hurt Cam", "Lowers or turns off the screen shake when you get hit.", Category.VISUAL, false);
	}

	@Override
	public void tick(Minecraft client) {
		tilt.hold(strength.get() / 100.0);
	}

	@Override
	protected void onDisable() {
		tilt.release();
	}
}
