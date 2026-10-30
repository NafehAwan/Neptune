package net.tuffclient.module.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.tuffclient.compat.Hitboxes;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ColorSetting;
import net.tuffclient.setting.NumberSetting;

/** Always shows hitboxes (like F3+B), so you can see exactly where your hits land. */
public final class HitboxesModule extends Module {
	private static boolean styled() {
		return Module.hasMixin("HitboxStyleMixin");
	}

	public final BoolSetting lookLine = add(new BoolSetting("look_line", "Look direction line", true)).showIf(HitboxesModule::styled);
	public final BoolSetting eyeLine = add(new BoolSetting("eye_line", "Eye height line (red)", true)).showIf(HitboxesModule::styled);
	public final NumberSetting width = add(new NumberSetting("width", "Line width", 2.5, 0.5, 8, 0.5)).showIf(HitboxesModule::styled);
	public final ColorSetting color = add(new ColorSetting("color", "Box color", 0xFFFFFFFF, true)).showIf(HitboxesModule::styled);
	public final BoolSetting aimColor = add(new BoolSetting("aim_color", "Change color when aiming at it", true)).showIf(HitboxesModule::styled);
	public final ColorSetting aimed = add(new ColorSetting("aimed_color", "Aiming color", 0xFFFF3B3B, true)).showIf(() -> styled() && aimColor.get());

	public HitboxesModule() {
		super("hitboxes", "Hitboxes", "Shows player and mob hitboxes, like F3+B, without the key.", Category.VISUAL, false);
	}

	/** The box colour for an entity: the aiming colour when you're looking at it, else the box colour (vanilla's own when it isn't white). */
	public int boxColor(Entity entity, int vanilla) {
		if (aimColor.get() && entity != null && entity == Minecraft.getInstance().crosshairPickEntity) return aimed.argb();
		return vanilla == 0xFFFFFFFF ? color.argb() : vanilla;
	}

	@Override
	public void tick(Minecraft client) {
		if (client.level != null) Hitboxes.show(true);
	}

	@Override
	protected void onDisable() {
		Hitboxes.show(false);
	}
}
