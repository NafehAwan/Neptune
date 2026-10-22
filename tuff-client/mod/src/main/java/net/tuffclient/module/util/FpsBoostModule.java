package net.tuffclient.module.util;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;

/**
 * More FPS without changing how the game plays: turns off the costly extras
 * (clouds, entity shadows, biome blending) and stops drawing far away mobs
 * and items. Turning it off puts the game's defaults back. Sodium, which
 * every Glade performance preset includes, gives the biggest boost on top.
 */
public final class FpsBoostModule extends Module {
	public final BoolSetting clouds = add(new BoolSetting("clouds", "Turn off clouds", true));
	public final BoolSetting shadows = add(new BoolSetting("shadows", "Turn off entity shadows", true));
	public final BoolSetting biomeBlend = add(new BoolSetting("biome_blend", "Turn off biome blending (chunks load faster)", true));
	public final BoolSetting farEntities = add(new BoolSetting("far_entities", "Hide mobs and items past 48 blocks", true));
	/** The settings last applied, so a change is applied once and the player's own video settings are left alone otherwise. */
	private String applied = "";

	public FpsBoostModule() {
		super("fps_boost", "FPS Boost", "Turns off costly extras for more FPS: clouds, shadows, biome blending, far mobs.", Category.UTILITY, true);
	}

	@Override
	public void tick(Minecraft client) {
		String wanted = clouds.get() + "," + shadows.get() + "," + biomeBlend.get();
		if (wanted.equals(applied) || client.options == null) return;
		applied = wanted;
		Options o = client.options;
		if (clouds.get()) set(o.cloudStatus(), CloudStatus.OFF);
		if (shadows.get()) set(o.entityShadows(), false);
		if (biomeBlend.get()) set(o.biomeBlendRadius(), 0);
		o.save();
	}

	@Override
	protected void onDisable() {
		applied = "";
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.options == null) return;
		Options o = mc.options;
		// back to the game's defaults, unless the player changed them since
		if (o.cloudStatus().get() == CloudStatus.OFF) o.cloudStatus().set(CloudStatus.FANCY);
		if (!o.entityShadows().get()) o.entityShadows().set(true);
		if (o.biomeBlendRadius().get() == 0) o.biomeBlendRadius().set(2);
		o.save();
	}

	private static <T> void set(net.minecraft.client.OptionInstance<T> option, T value) {
		if (!value.equals(option.get())) option.set(value);
	}

	/** Whether far mobs and items are hidden for FPS (see EntityDistanceModule). */
	public boolean hidesFarEntities() {
		return isEnabled() && farEntities.get();
	}
}
