package net.tuffclient.module.hud;

import net.minecraft.client.Minecraft;

public final class MemoryModule extends TextHudModule {
	private final net.tuffclient.setting.ChoiceSetting show = add(new net.tuffclient.setting.ChoiceSetting("show", "Show", 0, "Used / max MB", "Percent", "Used MB"));

	public MemoryModule() {
		super("memory", "Memory", "Java heap in use, to spot memory pressure.", "RAM", 0.006, 0.4, false);
	}

	@Override
	protected String value(Minecraft client, boolean preview) {
		Runtime rt = Runtime.getRuntime();
		long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
		long max = rt.maxMemory() / (1024 * 1024);
		return switch (show.get()) {
			case 1 -> Math.round(used * 100.0 / Math.max(1, max)) + "%";
			case 2 -> used + " MB";
			default -> used + " / " + max + " MB";
		};
	}
}
