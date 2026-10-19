package net.tuffclient.module.visual;

import net.minecraft.client.Minecraft;
import net.tuffclient.hook.GladeOverlayTexture;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.ColorSetting;

/** The colour mobs and players flash when they're hit (vanilla is red). */
public final class HitColorModule extends Module {
	public static final int VANILLA = 0xB2FF0000;
	private final ColorSetting color = add(new ColorSetting("color", "Hit color", 0xB24FC79A, true));
	private int applied = VANILLA;

	public HitColorModule() {
		super("hit_color", "Hit Color", "Change the red flash when something takes damage.", Category.VISUAL, false);
	}

	@Override
	public void tick(Minecraft client) {
		apply(client, color.argb());
	}

	@Override
	protected void onDisable() {
		apply(Minecraft.getInstance(), VANILLA);
	}

	private void apply(Minecraft client, int argb) {
		if (argb == applied || client.gameRenderer == null) return;
		applied = argb;
		((GladeOverlayTexture) client.gameRenderer.overlayTexture()).glade$recolor(argb);
	}
}
