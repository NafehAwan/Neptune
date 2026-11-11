package net.tuffclient.legacy.hud;

public final class CpsHud extends TextHud {
	public CpsHud() {
		super("cps", "CPS", "Clicks per second, left and right.", "CPS", 0.005, 0.055, true);
	}

	@Override
	protected String value(boolean preview) {
		return Cps.left() + " | " + Cps.right();
	}
}
