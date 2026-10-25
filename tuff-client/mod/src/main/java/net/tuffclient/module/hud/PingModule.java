package net.tuffclient.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

public final class PingModule extends TextHudModule {
	private final net.tuffclient.setting.BoolSetting colored = add(new net.tuffclient.setting.BoolSetting("colored", "Color by how good it is", false));

	public PingModule() {
		super("ping", "Ping", "Your latency to the server.", "Ping", 0.006, 0.26, false);
	}

	@Override
	protected String value(Minecraft client, boolean preview) {
		if (client.player == null || client.getConnection() == null) return preview ? "42 ms" : null;
		PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
		if (info == null) return preview ? "42 ms" : null;
		return info.getLatency() + " ms";
	}

	@Override
	protected void value(net.tuffclient.gui.Gfx g, net.minecraft.client.gui.Font font, String s, int x, int y) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		PlayerInfo info = !colored.get() || mc.player == null || mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
		if (info == null) super.value(g, font, s, x, y);
		else text(g, font, s, x, y, net.tuffclient.module.visual.TabPingModule.color(info.getLatency()));
	}
}
