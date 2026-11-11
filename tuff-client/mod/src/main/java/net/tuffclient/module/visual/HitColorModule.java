package net.tuffclient.module.visual;

import net.minecraft.client.Minecraft;
import net.tuffclient.hook.GladeOverlayTexture;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ColorSetting;

/** The colour mobs and players flash when they're hit (vanilla is red). */
public final class HitColorModule extends Module {
	public static final int VANILLA = 0xB2FF0000;
	private final ColorSetting color = add(new ColorSetting("color", "Hit color", VANILLA, true));
	/** Worn armor flashes too, outer pieces and the inner leggings layer (vanilla armor doesn't). */
	public final BoolSetting armor = add(new BoolSetting("armor", "Tint armor too", true)).showIf(() -> net.tuffclient.module.Module.hasMixin("ArmorTintMixin"));
	private int applied = VANILLA;

	public HitColorModule() {
		super("hit_color", "Damage Tint", "The flash when something takes damage: its colour, and on armor too.", Category.VISUAL, false);
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
