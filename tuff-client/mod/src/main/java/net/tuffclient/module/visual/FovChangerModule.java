package net.tuffclient.module.visual;

import net.minecraft.client.Minecraft;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.module.OptionOverride;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.NumberSetting;

/** Keeps your field of view steady: sprinting, speed and bows change it less, or not at all. */
public final class FovChangerModule extends Module {
	private final NumberSetting effects = add(new NumberSetting("effects", "Sprint, speed and bow FOV (%)", 0, 0, 100, 5));
	private final BoolSetting custom = add(new BoolSetting("custom", "Set my own FOV", false));
	private final NumberSetting fov = add(new NumberSetting("fov", "FOV", 90, 30, 110, 1)).showIf(() -> custom.get());
	private final OptionOverride<Double> effectScale = new OptionOverride<>(o -> o.fovEffectScale());
	private final OptionOverride<Integer> baseFov = new OptionOverride<>(o -> o.fov());

	public FovChangerModule() {
		super("fov_changer", "FOV Changer", "Stops sprinting, speed and bows from changing your field of view.", Category.VISUAL, false);
	}

	@Override
	public void tick(Minecraft client) {
		effectScale.hold(effects.get() / 100.0);
		if (custom.get()) baseFov.hold(fov.get().intValue());
		else baseFov.release();
	}

	@Override
	protected void onDisable() {
		effectScale.release();
		baseFov.release();
	}
}
