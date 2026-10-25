package net.tuffclient.module.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.tuffclient.gui.Gfx;
import net.tuffclient.gui.Theme;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ChoiceSetting;
import net.tuffclient.setting.ColorSetting;
import net.tuffclient.setting.NumberSetting;

/** Replaces the vanilla crosshair with a configurable one. */
public final class CrosshairModule extends Module {
	private static final int[] OLD_COLORS = {0xFFFFFFFF, Theme.COPPER_HI, 0xFF8FE3B0, 0xFF8EC9FF, 0xFFFF6B6B, 0xFFF2D06B};
	public final ChoiceSetting style = add(new ChoiceSetting("style", "Style", 0, "Cross", "Dot", "Square", "T", "Circle"));
	public final ColorSetting color = add(new ColorSetting("crosshair_color", "Color", 0xFFFFFFFF, true));
	public final NumberSetting size = add(new NumberSetting("size", "Size", 5, 2, 12, 1));
	public final NumberSetting gap = add(new NumberSetting("gap", "Gap", 2, 0, 6, 1));
	public final NumberSetting thickness = add(new NumberSetting("thickness", "Thickness", 1, 1, 3, 1));
	public final BoolSetting dot = add(new BoolSetting("dot", "Center dot", false));
	public final BoolSetting outline = add(new BoolSetting("outline", "Outline", true));
	public final BoolSetting targetColor = add(new BoolSetting("target", "Change color when aiming at a player or mob", false));
	public final ColorSetting targetTint = add(new ColorSetting("target_color", "Aiming color", 0xFFFF5555, false)).showIf(() -> targetColor.get());
	public final BoolSetting thirdPerson = add(new BoolSetting("third_person", "Show in third person", false));
	/** The colour choice from before custom colours, read once from old settings. */
	private final ChoiceSetting oldColor = new ChoiceSetting("color", "Color", 0, "White", "Copper", "Mint", "Sky", "Red", "Gold");

	public CrosshairModule() {
		super("crosshair", "Crosshair", "Pick your own crosshair shape, size and color.", Category.VISUAL, false);
	}

	/** Whether it draws in the current camera view. */
	public boolean shows(Minecraft mc) {
		return mc.options.getCameraType().isFirstPerson() || thirdPerson.get();
	}

	@Override
	public void load(com.google.gson.JsonObject json) {
		super.load(json);
		var s = json.has("settings") && json.get("settings").isJsonObject() ? json.getAsJsonObject("settings") : null;
		if (s != null && s.has("color") && !s.has("crosshair_color")) {
			oldColor.fromJson(s.get("color"));
			color.set(OLD_COLORS[oldColor.get()]);
		}
	}

	public void render(Gfx g) {
		Minecraft mc = Minecraft.getInstance();
		int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
		int s = size.get().intValue(), gp = gap.get().intValue(), t = thickness.get().intValue();
		boolean aiming = targetColor.get() && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY;
		int c = aiming ? targetTint.argb() : color.argb();
		int lo = t / 2, hi = t - lo; // thickness spread around the centre pixel
		switch (style.choice()) {
			case "Dot" -> rect(g, cx - lo - 1, cy - lo - 1, cx + hi + 1, cy + hi + 1, c);
			case "Square" -> {
				rect(g, cx - s, cy - s, cx + s, cy - s + t, c);
				rect(g, cx - s, cy + s - t, cx + s, cy + s, c);
				rect(g, cx - s, cy - s, cx - s + t, cy + s, c);
				rect(g, cx + s - t, cy - s, cx + s, cy + s, c);
			}
			case "Circle" -> {
				// a ring of small squares
				int n = Math.max(12, s * 4);
				for (int i = 0; i < n; i++) {
					double a = Math.PI * 2 * i / n;
					int px = cx + (int) Math.round(Math.cos(a) * s), py = cy + (int) Math.round(Math.sin(a) * s);
					rect(g, px - lo, py - lo, px + hi, py + hi, c);
				}
			}
			case "T" -> {
				rect(g, cx - s - gp, cy - lo, cx - gp, cy + hi, c);
				rect(g, cx + gp + 1, cy - lo, cx + gp + 1 + s, cy + hi, c);
				rect(g, cx - lo, cy + gp + 1, cx + hi, cy + gp + 1 + s, c);
			}
			default -> {
				rect(g, cx - s - gp, cy - lo, cx - gp, cy + hi, c);
				rect(g, cx + gp + 1, cy - lo, cx + gp + 1 + s, cy + hi, c);
				rect(g, cx - lo, cy - s - gp, cx + hi, cy - gp, c);
				rect(g, cx - lo, cy + gp + 1, cx + hi, cy + gp + 1 + s, c);
			}
		}
		if (dot.get() && !style.choice().equals("Dot")) rect(g, cx - lo, cy - lo, cx + hi, cy + hi, c);
	}

	private void rect(Gfx g, int x1, int y1, int x2, int y2, int color) {
		if (outline.get()) g.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xC0000000);
		g.fill(x1, y1, x2, y2, color);
	}
}
