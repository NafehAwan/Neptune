package net.tuffclient.module.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.tuffclient.gui.Gfx;
import net.tuffclient.gui.Projector;
import net.tuffclient.gui.Ui;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.NumberSetting;

/** How long until lit TNT explodes, shown above it. */
public final class TntTimerModule extends Module {
	private final BoolSetting decimals = add(new BoolSetting("decimals", "Show tenths of a second", true));
	private final BoolSetting colors = add(new BoolSetting("colors", "Green to red as it runs out", true));
	private final BoolSetting background = add(new BoolSetting("background", "Background", true));
	private final NumberSetting range = add(new NumberSetting("range", "Range (blocks)", 32, 8, 64, 4));

	public TntTimerModule() {
		super("tnt_timer", "TNT Timer", "Shows how long until lit TNT explodes.", Category.VISUAL, false);
	}

	// line of sight is a raycast, so it's checked once a tick per TNT, not every frame
	private final java.util.Map<Integer, Boolean> sight = new java.util.HashMap<>();
	private long sightTick = -1;

	private boolean seen(Minecraft mc, PrimedTnt tnt) {
		long t = mc.level.getGameTime();
		if (t != sightTick) {
			sightTick = t;
			sight.clear();
		}
		return sight.computeIfAbsent(tnt.getId(), id -> mc.player.hasLineOfSight(tnt));
	}

	public void render(Gfx g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		Projector proj = null;
		Font font = mc.font;
		double max = range.get();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof PrimedTnt tnt) || tnt.distanceTo(mc.player) > max) continue;
			// only TNT you can see, so it doesn't show through walls
			if (!seen(mc, tnt)) continue;
			if (proj == null) proj = new Projector(g);
			float[] at = proj.project(tnt.getX(), tnt.getY() + 1.4, tnt.getZ());
			if (at == null) continue;
			float secs = Math.max(0, tnt.getFuse()) / 20f;
			String s = decimals.get() ? String.format("%.1f", secs) : String.valueOf((int) Math.ceil(secs));
			int color = 0xFFFFFFFF;
			if (colors.get()) {
				float t = Math.min(1, secs / 4f);
				int r = Math.round(255 * Math.min(1, 2 * (1 - t))), gr = Math.round(255 * Math.min(1, 2 * t));
				color = 0xFF000000 | r << 16 | gr << 8 | 0x40;
			}
			int w = font.width(s);
			int x = Math.round(at[0] - w / 2f), y = Math.round(at[1] - 4);
			if (background.get()) Ui.round(g, x - 3, y - 2, w + 6, 12, 3, 0x88101210);
			g.text(font, s, x, y, color, true);
		}
	}
}
