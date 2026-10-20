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

	public CoordinatesModule() {
		super("coordinates", "Coordinates", "Your position and the direction you're facing.", 0.006, 0.15, true);
	}

	@Override
	public void render(Gfx g, Font font, boolean preview) {
		LocalPlayer p = Minecraft.getInstance().player;
		String xyz = p == null ? "0 64 0" : String.format("%.0f %.0f %.0f", Math.floor(p.getX()), Math.floor(p.getY()), Math.floor(p.getZ()));
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
}
