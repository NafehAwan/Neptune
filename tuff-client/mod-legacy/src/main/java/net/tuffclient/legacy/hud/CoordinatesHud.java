package net.tuffclient.legacy.hud;

import net.minecraft.client.MinecraftClient;
import net.tuffclient.legacy.setting.BoolSetting;

/** Your position and the way you face. */
public final class CoordinatesHud extends HudModule {
	public final BoolSetting facing = add(new BoolSetting("facing", "Facing direction", true));
	private static final String[] DIRS = {"South +Z", "South West", "West -X", "North West", "North -Z", "North East", "East +X", "South East"};

	public CoordinatesHud() {
		super("coordinates", "Coordinates", "Your X, Y and Z, and which way you face.", 0.005, 0.30, true);
	}

	private String[] lines(boolean preview) {
		MinecraftClient mc = mc();
		if (mc.player == null) return new String[] {"0 64 0", "North -Z"};
		String xyz = (int) Math.floor(mc.player.x) + " " + (int) Math.floor(mc.player.y) + " " + (int) Math.floor(mc.player.z);
		int dir = Math.floorMod(Math.round(mc.player.yaw / 45f), 8);
		return new String[] {xyz, DIRS[dir]};
	}

	@Override
	public int width() {
		String[] l = lines(true);
		return Math.max(pairWidth("XYZ", l[0]), facing.get() ? pairWidth("", l[1]) : 0) + 8;
	}

	@Override
	public int height() {
		return facing.get() ? 26 : 16;
	}

	@Override
	protected void draw(boolean preview) {
		String[] l = lines(preview);
		pair("XYZ", l[0], 4, 4);
		if (facing.get()) pair("", l[1], 4, 14);
	}
}
