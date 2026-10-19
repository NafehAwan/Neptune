package net.tuffclient.module.visual;

import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ChoiceSetting;
import net.tuffclient.setting.NumberSetting;

/** Your own time of day and weather. Only on your screen; the server keeps its own. */
public final class TimeWeatherModule extends Module {
	public final BoolSetting changeTime = add(new BoolSetting("change_time", "Change the time", true));
	public final NumberSetting hour = add(new NumberSetting("hour", "Time of day (hour)", 12, 0, 23.5, 0.5)).showIf(() -> changeTime.get());
	public final ChoiceSetting weather = add(new ChoiceSetting("weather", "Weather", 0, "Clear", "Rain", "Thunder", "Server's"));

	public TimeWeatherModule() {
		super("time_weather", "Time & Weather", "Set your own time of day and weather. Only you see it.", Category.VISUAL, false);
	}

	@Override
	public void tick(net.minecraft.client.Minecraft client) {
		if (client.level != null && changeTime.get()) net.tuffclient.compat.WorldTime.force(client.level, ticks());
	}

	/** The chosen time in ticks (0 is 6:00 in the morning). */
	public long ticks() {
		return Math.floorMod(Math.round((hour.get() - 6) * 1000), 24000L);
	}
}
