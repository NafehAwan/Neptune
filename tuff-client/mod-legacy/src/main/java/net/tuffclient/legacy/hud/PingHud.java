package net.tuffclient.legacy.hud;

public final class PingHud extends TextHud {
	public PingHud() {
		super("ping", "Ping", "Your connection delay to the server.", "Ping", 0.005, 0.145, false);
	}

	@Override
	protected String value(boolean preview) {
		net.minecraft.client.MinecraftClient mc = mc();
		if (mc.player == null || mc.getNetworkHandler() == null) return preview ? "42 ms" : "-";
		net.minecraft.client.network.PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
		return e == null ? "0 ms" : e.getLatency() + " ms";
	}
}
