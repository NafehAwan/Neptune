package net.tuffclient.module.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.tuffclient.gui.Ui;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ColorSetting;

/**
 * A clean F3 screen: only the lines you pick, in a Glade panel. The F3 charts
 * (F3+1, F3+2, F3+3) still show the vanilla screen.
 */
public final class CleanF3Module extends Module {
	private static final String[] FACING = {"South (+Z)", "West (-X)", "North (-Z)", "East (+X)"};
	private final BoolSetting fps = add(new BoolSetting("fps", "FPS", true));
	private final BoolSetting position = add(new BoolSetting("position", "Position", true));
	private final BoolSetting block = add(new BoolSetting("block", "Block and chunk", true));
	private final BoolSetting facing = add(new BoolSetting("facing", "Facing", true));
	private final BoolSetting biome = add(new BoolSetting("biome", "Biome", true));
	private final BoolSetting light = add(new BoolSetting("light", "Light", true));
	private final BoolSetting target = add(new BoolSetting("target", "Looking at", true));
	private final BoolSetting memory = add(new BoolSetting("memory", "Memory", false));
	private final BoolSetting server = add(new BoolSetting("server", "Server", false));
	private final ColorSetting panel = add(new ColorSetting("panel", "Panel color", 0xB0101210, true));
	private final ColorSetting labels = add(new ColorSetting("labels", "Label color", 0xFF9AA59C, true));
	private final ColorSetting values = add(new ColorSetting("values", "Value color", 0xFFFFFFFF, true));

	public CleanF3Module() {
		super("clean_f3", "Clean F3", "A tidy F3 screen with only the lines you want.", Category.UTILITY, false);
	}

	/** Whether an F3 chart is open (then the vanilla screen shows); set by DebugScreenOverlayMixin. */
	public static boolean charts;

	public void render(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		Entity cam = mc.getCameraEntity();
		if (cam == null || mc.level == null || !mc.debugEntries.isOverlayVisible() || charts) return;
		List<String[]> lines = new ArrayList<>();
		BlockPos pos = cam.blockPosition();
		if (fps.get()) lines.add(new String[] {"FPS", String.valueOf(mc.getFps())});
		if (position.get()) lines.add(new String[] {"XYZ", String.format("%.1f / %.1f / %.1f", cam.getX(), cam.getY(), cam.getZ())});
		if (block.get()) lines.add(new String[] {"Block", pos.getX() + " " + pos.getY() + " " + pos.getZ() + "  chunk " + (pos.getX() >> 4) + " " + (pos.getZ() >> 4)});
		if (facing.get()) lines.add(new String[] {"Facing", FACING[Math.floorMod(Math.round(cam.getYRot() / 90f), 4)] + String.format("  %.1f° / %.1f°", ((cam.getYRot() % 360) + 360) % 360, cam.getXRot())});
		if (biome.get()) lines.add(new String[] {"Biome", mc.level.getBiome(pos).unwrapKey().map(k -> k.identifier().toString()).orElse("?")});
		if (light.get()) lines.add(new String[] {"Light", mc.level.getBrightness(LightLayer.BLOCK, pos) + " block, " + mc.level.getBrightness(LightLayer.SKY, pos) + " sky"});
		if (target.get()) {
			HitResult hit = mc.hitResult;
			String what = "-";
			if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) {
				BlockPos bp = b.getBlockPos();
				what = mc.level.getBlockState(bp).getBlock().getName().getString() + " (" + bp.getX() + " " + bp.getY() + " " + bp.getZ() + ")";
			} else if (mc.crosshairPickEntity != null) {
				what = mc.crosshairPickEntity.getName().getString();
			}
			lines.add(new String[] {"Target", what});
		}
		if (memory.get()) {
			Runtime r = Runtime.getRuntime();
			long used = (r.totalMemory() - r.freeMemory()) >> 20, max = r.maxMemory() >> 20;
			lines.add(new String[] {"Memory", used + " / " + max + " MB"});
		}
		if (server.get()) lines.add(new String[] {"Server", mc.getCurrentServer() == null ? "Singleplayer" : mc.getCurrentServer().ip});
		Font font = mc.font;
		int lw = 0, vw = 0;
		for (String[] l : lines) {
			lw = Math.max(lw, font.width(l[0]));
			vw = Math.max(vw, font.width(l[1]));
		}
		int x = 6, y = 6, w = lw + vw + 22, h = lines.size() * 11 + 9;
		Ui.round(g, x, y, w, h, 5, panel.argb());
		int ty = y + 6;
		for (String[] l : lines) {
			g.text(font, l[0], x + 7, ty, labels.argb(), false);
			g.text(font, l[1], x + 15 + lw, ty, values.argb(), false);
			ty += 11;
		}
	}
}
