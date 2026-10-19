package net.tuffclient.module.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.tuffclient.setting.BoolSetting;

/** The in-game day, the time of day and how long until night (or day). */
public final class DayTimeModule extends HudModule {
	private final BoolSetting day = add(new BoolSetting("day", "Day number", true));
	private final BoolSetting time = add(new BoolSetting("time", "Time of day", true));
	private final BoolSetting twelve = add(new BoolSetting("12h", "12-hour clock", false));
	private final BoolSetting countdown = add(new BoolSetting("countdown", "Time until night or day", true));

	public DayTimeModule() {
		super("day_time", "Day & Time", "The in-game day, time, and when night starts.", 0.885, 0.1, false);
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font font, boolean preview) {
		Minecraft mc = Minecraft.getInstance();
		long t = mc.level == null ? 30000 : mc.level.getOverworldClockTime();
		long tod = Math.floorMod(t, 24000L);
		List<String[]> lines = new ArrayList<>();
		if (day.get()) lines.add(new String[] {"Day ", String.valueOf(t / 24000 + 1)});
		if (time.get()) {
			// tick 0 is 6:00 in the morning
			int hours = (int) ((tod / 1000 + 6) % 24), minutes = (int) (tod % 1000 * 60 / 1000);
			String clock = twelve.get()
					? (hours % 12 == 0 ? 12 : hours % 12) + String.format(":%02d ", minutes) + (hours < 12 ? "AM" : "PM")
					: String.format("%02d:%02d", hours, minutes);
			lines.add(new String[] {"Time ", clock});
		}
		if (countdown.get()) {
			boolean night = tod >= 13000 && tod < 23000;
			long left = night ? 23000 - tod : Math.floorMod(13000 - tod, 24000L);
			long secs = left / 20;
			lines.add(new String[] {night ? "Day in " : "Night in ", secs / 60 + "m " + secs % 60 + "s"});
		}
		if (lines.isEmpty()) lines.add(new String[] {"", "-"});
		int p = pad(), w = 0;
		for (String[] l : lines) w = Math.max(w, font.width(l[0]) + font.width(l[1]));
		box(g, w + p * 2, linesHeight(lines.size()));
		int y = p;
		for (String[] l : lines) {
			text(g, font, l[0], p, y, labelColor());
			text(g, font, l[1], p + font.width(l[0]), y, textColor());
			y += 10;
		}
	}
}
