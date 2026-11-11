package net.tuffclient.legacy.hud;

public final class MemoryHud extends TextHud {
	public MemoryHud() {
		super("memory", "Memory", "How much memory the game uses.", "Mem", 0.005, 0.235, false);
	}

	@Override
	protected String value(boolean preview) {
		Runtime r = Runtime.getRuntime();
		long used = (r.totalMemory() - r.freeMemory()) >> 20, max = r.maxMemory() >> 20;
		return used * 100 / Math.max(1, max) + "% (" + used + " MB)";
	}
}
