package net.tuffclient.module.visual;

import net.minecraft.client.Minecraft;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.module.OptionOverride;
import net.tuffclient.setting.NumberSetting;

/** How much the screen shakes when you take damage (the game's damage tilt). */
public final class HurtCamModule extends Module {
	private final NumberSetting strength = add(new NumberSetting("strength", "Shake (%)", 0, 0, 100, 5));
	private final OptionOverride<Double> tilt = new OptionOverride<>("original_tilt", o -> o.damageTiltStrength(), j -> j.getAsDouble());

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

	@Override
	public com.google.gson.JsonObject save() {
		com.google.gson.JsonObject json = super.save();
		tilt.save(json);
		return json;
	}

	@Override
	public void load(com.google.gson.JsonObject json) {
		super.load(json);
		tilt.load(json);
	}
}
