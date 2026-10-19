package net.tuffclient.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ColorSetting;

/** The biome you're in and the light level at your feet (mobs spawn at block light 0). */
public final class BiomeLightModule extends HudModule {
	private final BoolSetting biome = add(new BoolSetting("biome", "Biome", true));
	private final BoolSetting light = add(new BoolSetting("light", "Block light", true));
	private final BoolSetting sky = add(new BoolSetting("sky", "Sky light", false));
	private final BoolSetting warn = add(new BoolSetting("warn", "Warn where mobs can spawn", true));
	private final ColorSetting warnColor = add(new ColorSetting("warn_color", "Warning color", 0xFFFF6B6B, false)).showIf(() -> warn.get());

	public BiomeLightModule() {
		super("biome_light", "Biome & Light", "The biome you're in and the light level, so you know where mobs can spawn.", 0.006, 0.22, false);
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font font, boolean preview) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		Entity camera = mc.getCameraEntity();
		String biomeName = "Plains";
		int block = 12, skyLight = 15;
		if (level != null && camera != null) {
			BlockPos pos = camera.blockPosition();
			biomeName = level.getBiome(pos).unwrapKey().map(k -> pretty(k.identifier().getPath())).orElse("Unknown");
			block = level.getBrightness(LightLayer.BLOCK, pos);
			skyLight = level.getBrightness(LightLayer.SKY, pos);
		}
		java.util.List<String[]> lines = new java.util.ArrayList<>();
		if (biome.get()) lines.add(new String[] {"Biome ", biomeName});
		if (light.get()) lines.add(new String[] {"Light ", String.valueOf(block)});
		if (sky.get()) lines.add(new String[] {"Sky ", String.valueOf(skyLight)});
		if (lines.isEmpty()) lines.add(new String[] {"", "-"});
		int p = pad(), w = 0;
		for (String[] l : lines) w = Math.max(w, font.width(l[0]) + font.width(l[1]));
		box(g, w + p * 2, linesHeight(lines.size()));
		int y = p;
		for (String[] l : lines) {
			text(g, font, l[0], p, y, labelColor());
			boolean danger = warn.get() && l[0].equals("Light ") && block == 0;
			text(g, font, l[1], p + font.width(l[0]), y, danger ? warnColor.argb() : textColor());
			y += 10;
		}
	}

	/** "dark_forest" -> "Dark Forest". */
	static String pretty(String path) {
		StringBuilder out = new StringBuilder();
		for (String part : path.split("_")) {
			if (part.isEmpty()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return out.toString();
	}
}
