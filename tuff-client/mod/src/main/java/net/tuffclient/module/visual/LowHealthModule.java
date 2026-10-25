package net.tuffclient.module.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.tuffclient.gui.Gfx;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ColorSetting;
import net.tuffclient.setting.NumberSetting;

/** Red screen edges when your health drops low. */
public final class LowHealthModule extends Module {
	private final NumberSetting hearts = add(new NumberSetting("hearts", "Below (hearts)", 4, 1, 10, 0.5));
	private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFFFF2020, false));
	private final NumberSetting strength = add(new NumberSetting("strength", "Strength (%)", 60, 10, 100, 5));
	private final BoolSetting pulse = add(new BoolSetting("pulse", "Pulse", true));

	public LowHealthModule() {
		super("low_health", "Low Health Warning", "Red screen edges when your health is low.", Category.VISUAL, false);
	}

	public void render(Gfx g) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || p.isDeadOrDying() || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return;
		float limit = hearts.get().floatValue() * 2;
		float hp = p.getHealth() + p.getAbsorptionAmount();
		if (hp >= limit) return;
		// stronger the lower you are
		float k = 0.35f + 0.65f * (1 - hp / limit);
		if (pulse.get()) k *= 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 160.0);
		int alpha = Math.round(255 * k * strength.get().floatValue() / 100f);
		int rgb = color.argb() & 0xFFFFFF;
		int w = g.guiWidth(), h = g.guiHeight();
		int band = Math.max(24, Math.min(w, h) / 4);
		int edge = alpha << 24 | rgb, clear = rgb;
		g.fillGradient(0, 0, w, band, edge, clear);
		g.fillGradient(0, h - band, w, h, clear, edge);
		// the sides fade in steps (gradients only run top to bottom)
		int steps = 12;
		for (int i = 0; i < steps; i++) {
			int a = Math.round(alpha * (1 - i / (float) steps) / 3f);
			int c = a << 24 | rgb;
			int x0 = band * i / steps, x1 = band * (i + 1) / steps;
			g.fill(x0, 0, x1, h, c);
			g.fill(w - x1, 0, w - x0, h, c);
		}
	}
}
