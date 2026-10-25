package net.tuffclient.module.hud;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import net.minecraft.client.Minecraft;
import net.tuffclient.setting.BoolSetting;

public final class ClockModule extends TextHudModule {
	private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a");
	private static final DateTimeFormatter H24S = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final DateTimeFormatter H12S = DateTimeFormatter.ofPattern("h:mm:ss a");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM");
	private final BoolSetting twelveHour = add(new BoolSetting("12h", "12-hour clock", false));
	private final BoolSetting seconds = add(new BoolSetting("seconds", "Show seconds", false));
	private final BoolSetting date = add(new BoolSetting("date", "Show the date", false));

	public ClockModule() {
		super("clock", "Clock", "The real-world time.", "", 0.94, 0.01, false);
	}

	@Override
	protected String value(Minecraft client, boolean preview) {
		DateTimeFormatter f = twelveHour.get() ? (seconds.get() ? H12S : H12) : (seconds.get() ? H24S : H24);
		String time = LocalTime.now().format(f);
		return date.get() ? java.time.LocalDate.now().format(DATE) + "  " + time : time;
	}
}
