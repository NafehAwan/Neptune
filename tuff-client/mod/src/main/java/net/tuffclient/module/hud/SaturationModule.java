package net.tuffclient.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.tuffclient.gui.Gfx;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ColorSetting;

/** Shows your hidden saturation on the food bar, so you know when you'll start getting hungry. */
public final class SaturationModule extends Module {
	private final BoolSetting overlay = add(new BoolSetting("overlay", "Show on the food bar", true));
	private final ColorSetting color = add(new ColorSetting("color", "Overlay color", 0x99FFD24A, true)).showIf(() -> overlay.get());
	private final BoolSetting number = add(new BoolSetting("number", "Show the number", false));

	public SaturationModule() {
		super("saturation", "Saturation", "Shows hidden hunger saturation on the food bar.", Category.HUD, false);
	}

	/** Drawn over the vanilla food bar, which sits right of the hotbar. */
	public void render(Gfx g) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer() || p.getVehicle() instanceof LivingEntity) return;
		float sat = p.getFoodData().getSaturationLevel();
		int right = g.guiWidth() / 2 + 91, y = g.guiHeight() - 39;
		if (overlay.get()) {
			int c = color.argb();
			for (int i = 0; i < 10; i++) {
				float part = Math.max(0, Math.min(2, sat - i * 2)) / 2f;
				if (part <= 0) break;
				int x = right - i * 8 - 9;
				// fills each shank from the right, like the food bar empties
				int w = Math.max(1, Math.round(7 * part));
				g.fill(x + 8 - w, y + 1, x + 8, y + 8, c);
			}
		}
		if (number.get()) {
			String s = String.format("%.1f", sat);
			g.text(mc.font, s, right - mc.font.width(s), y - 10, 0xFFFFD24A, true);
		}
	}
}
