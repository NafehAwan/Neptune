package net.tuffclient.legacy.hud;

public final class SpeedHud extends TextHud {
	public SpeedHud() {
		super("speed", "Speed", "How fast you move, in blocks per second.", "Speed", 0.005, 0.19, false);
	}

	@Override
	protected String value(boolean preview) {
		net.minecraft.client.MinecraftClient mc = mc();
		if (mc.player == null) return "0.00 m/s";
		double dx = mc.player.x - mc.player.prevX, dz = mc.player.z - mc.player.prevZ;
		return String.format("%.2f m/s", Math.sqrt(dx * dx + dz * dz) * 20);
	}
}
