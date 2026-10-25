package net.tuffclient.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Horizontal speed in blocks per second, smoothed over a few ticks. */
public final class SpeedModule extends TextHudModule {
	private double lastX;
	private double lastZ;
	private double speed;
	private boolean primed;

	private final net.tuffclient.setting.ChoiceSetting unit = add(new net.tuffclient.setting.ChoiceSetting("unit", "Unit", 0, "m/s", "km/h", "blocks/s"));
	private final net.tuffclient.setting.BoolSetting vertical = add(new net.tuffclient.setting.BoolSetting("vertical", "Count up and down movement", false));
	private double lastY;

	public SpeedModule() {
		super("speed", "Speed", "How fast you're moving, in blocks per second.", "Speed", 0.006, 0.33, false);
	}

	@Override
	public void tick(Minecraft client) {
		LocalPlayer p = client.player;
		if (p == null) {
			primed = false;
			return;
		}
		if (primed) {
			double dx = p.getX() - lastX;
			double dz = p.getZ() - lastZ;
			double dy = vertical.get() ? p.getY() - lastY : 0;
			double now = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20;
			speed = speed * 0.6 + now * 0.4;
		}
		lastX = p.getX();
		lastY = p.getY();
		lastZ = p.getZ();
		primed = true;
	}

	@Override
	protected String value(Minecraft client, boolean preview) {
		return switch (unit.get()) {
			case 1 -> String.format("%.1f km/h", speed * 3.6);
			case 2 -> String.format("%.2f b/s", speed);
			default -> String.format("%.2f m/s", speed);
		};
	}
}
