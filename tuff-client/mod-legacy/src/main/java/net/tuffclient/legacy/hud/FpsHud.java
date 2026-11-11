package net.tuffclient.legacy.hud;

public final class FpsHud extends TextHud {
	public FpsHud() {
		super("fps", "FPS", "Frames per second.", "FPS", 0.005, 0.01, true);
	}

	@Override
	protected String value(boolean preview) {
		return String.valueOf(net.minecraft.client.MinecraftClient.getCurrentFps());
	}
}
