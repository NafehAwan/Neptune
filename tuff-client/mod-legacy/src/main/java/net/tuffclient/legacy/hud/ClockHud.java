package net.tuffclient.legacy.hud;

public final class ClockHud extends TextHud {
	public ClockHud() {
		super("clock", "Clock", "The time on your computer.", "", 0.005, 0.1, false);
	}

	@Override
	protected String value(boolean preview) {
		return new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());
	}
}
