package net.tuffclient.module.hud;

import net.tuffclient.gui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.tuffclient.setting.BoolSetting;

public final class CoordinatesModule extends HudModule {
	private static final String[] FACING = {"South +Z", "West -X", "North -Z", "East +X"};
	private final BoolSetting direction = add(new BoolSetting("direction", "Show direction", true));
	private final BoolSetting showLabel = add(new BoolSetting("show_label", "Show the XYZ label", true));
	private final net.tuffclient.setting.ColorSetting xyzColor = add(new net.tuffclient.setting.ColorSetting("xyz_color", "XYZ label color", net.tuffclient.gui.Theme.HUD_TEXT, true)).showIf(() -> showLabel.get());

	private final net.tuffclient.setting.NumberSetting decimals = add(new net.tuffclient.setting.NumberSetting("decimals", "Decimal places", 0, 0, 2, 1));
	private final BoolSetting perLine = add(new BoolSetting("per_line", "Each axis on its own line", false));

	public CoordinatesModule() {
		super("coordinates", "Coordinates", "Your position and the direction you're facing.", 0.006, 0.15, true);
	}

	@Override
	public void render(Gfx g, Font font, boolean preview) {
		LocalPlayer p = Minecraft.getInstance().player;
		int dp = decimals.get().intValue();
		double px = p == null ? 0 : p.getX(), py = p == null ? 64 : p.getY(), pz = p == null ? 0 : p.getZ();
		String[] axes = {num(px, dp), num(py, dp), num(pz, dp)};
		String facing0 = "";
		if (direction.get()) facing0 = FACING[Math.floorMod(Math.round((p == null ? 0 : p.getYRot()) / 90f), 4)];
		if (perLine.get()) {
			String[] tags = showLabel.get() ? new String[] {"X ", "Y ", "Z "} : new String[] {"", "", ""};
			int pd = pad(), w = width(font, facing0);
			for (int i = 0; i < 3; i++) w = Math.max(w, width(font, tags[i]) + width(font, axes[i]));
			int n = 3 + (facing0.isEmpty() ? 0 : 1);
			box(g, w + pd * 2, linesHeight(n));
			for (int i = 0; i < 3; i++) {
				colored(g, font, tags[i], pd, pd + i * 10, xyzColor);
				value(g, font, axes[i], pd + width(font, tags[i]), pd + i * 10);
			}
			if (!facing0.isEmpty()) label(g, font, facing0, pd, pd + 30);
			return;
		}
		String xyz = axes[0] + " " + axes[1] + " " + axes[2];
		String tag = showLabel.get() ? "XYZ " : "";
		String facing = "";
		if (direction.get()) {
			float yaw = p == null ? 0 : p.getYRot();
			facing = FACING[Math.floorMod(Math.round(yaw / 90f), 4)];
		}
		int pd = pad();
		box(g, Math.max(width(font, tag) + width(font, xyz), width(font, facing)) + pd * 2, linesHeight(facing.isEmpty() ? 1 : 2));
		colored(g, font, tag, pd, pd, xyzColor);
		value(g, font, xyz, pd + width(font, tag), pd);
		if (!facing.isEmpty()) label(g, font, facing, pd, pd + 10);
	}

	/** A coordinate rounded down to whole blocks, or with decimals. */
	private static String num(double v, int decimals) {
		return decimals == 0 ? String.valueOf((long) Math.floor(v)) : String.format("%." + decimals + "f", v);
	}
}
