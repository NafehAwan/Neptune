package net.tuffclient.module.hud;

import net.tuffclient.gui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.tuffclient.TuffClient;
import net.tuffclient.module.util.WaypointsModule;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ColorSetting;
import net.tuffclient.setting.NumberSetting;

/** A strip across the top of the screen showing N, E, S, W and your heading. */
public final class CompassModule extends HudModule {
	private static final String[] POINTS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
	private final NumberSetting barWidth = add(new NumberSetting("width", "Width", 180, 100, 320, 10));
	private final NumberSetting span = add(new NumberSetting("span", "Degrees shown", 150, 60, 240, 10));
	private final BoolSetting degrees = add(new BoolSetting("degrees", "Show heading in degrees", true));
	private final BoolSetting ticks = add(new BoolSetting("ticks", "Tick marks", true));
	private final BoolSetting waypoints = add(new BoolSetting("waypoints", "Show waypoints", true));
	private final ColorSetting marker = add(new ColorSetting("marker", "Center marker", 0xFFE3B23C, false));

	public CompassModule() {
		super("compass", "Compass", "A strip across the top showing N, E, S, W and which way you face.", 0.41, 0.01, false);
	}

	@Override
	public void render(Gfx g, Font font, boolean preview) {
		LocalPlayer p = Minecraft.getInstance().player;
		float yaw = p == null ? 180 : ((p.getYRot() % 360) + 360) % 360;
		int w = barWidth.get().intValue(), pad = pad();
		int h = 9 + pad * 2 + (degrees.get() ? 10 : 0);
		box(g, w, h);
		float range = span.get().floatValue(), half = range / 2, perDeg = w / range;
		int mid = w / 2;
		g.enableScissor(0, 0, w, h);
		// every 15 degrees: a tick, every 45 a letter
		for (int d = 0; d < 360; d += 15) {
			float diff = (((d - yaw) % 360) + 540) % 360 - 180;
			if (Math.abs(diff) > half + 10) continue;
			int x = Math.round(mid + diff * perDeg);
			float fade = 1 - Math.max(0, Math.abs(diff) - half * 0.6f) / (half * 0.4f + 10);
			if (d % 45 == 0) {
				String label = POINTS[d / 45];
				var source = label.length() == 1 ? textColor : labelColor;
				text(g, font, label, x - width(font, label) / 2, pad, fadeColor(source.argb(x * 0.004f), fade));
			} else if (ticks.get()) {
				g.fill(x, pad + 2, x + 1, pad + 6, fadeColor(labelColor(), fade));
			}
		}
		if (waypoints.get() && p != null) {
			WaypointsModule wp = TuffClient.modules().waypoints;
			if (wp.isEnabled()) {
				for (WaypointsModule.Point pt : wp.here()) {
					if (!pt.visible) continue;
					double angle = Math.toDegrees(Math.atan2(-(pt.x + 0.5 - p.getX()), pt.z + 0.5 - p.getZ()));
					float diff = (float) ((((angle - yaw) % 360) + 540) % 360 - 180);
					if (Math.abs(diff) > half) continue;
					int x = Math.round(mid + diff * perDeg);
					g.fill(x - 2, pad + 9, x + 2, pad + 11, pt.color | 0xFF000000);
				}
			}
		}
		g.disableScissor();
		g.fill(mid, 0, mid + 1, pad + 8, marker.argb());
		if (degrees.get()) {
			String deg = Math.round(yaw) % 360 + "°";
			value(g, font, deg, mid - width(font, deg) / 2, pad + 11);
		}
	}

	private static int fadeColor(int argb, float fade) {
		fade = Math.max(0, Math.min(1, fade));
		return Math.round((argb >>> 24) * fade) << 24 | (argb & 0xFFFFFF);
	}
}
