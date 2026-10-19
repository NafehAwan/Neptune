package net.tuffclient.legacy.util;

import net.minecraft.client.MinecraftClient;
import net.tuffclient.legacy.module.Category;
import net.tuffclient.legacy.module.Module;
import net.tuffclient.legacy.setting.NumberSetting;

/** Small modules that need no mixin of their own. */
public final class SimpleModules {
	private SimpleModules() {
	}

	/** Keeps you sprinting without holding the sprint key. */
	public static final class ToggleSprint extends Module {
		public ToggleSprint() {
			super("toggle_sprint", "Toggle Sprint", "Sprint without holding the sprint key.", Category.UTILITY, false);
		}

		@Override
		public void tick(MinecraftClient mc) {
			if (mc.player != null && mc.player.forwardSpeed > 0 && !mc.player.isSneaking() && mc.player.getHungerManager().getFoodLevel() > 6) {
				mc.player.setSprinting(true);
			}
		}
	}

	/** Full brightness everywhere. */
	public static final class Fullbright extends Module {
		private float before = -1;

		public Fullbright() {
			super("fullbright", "Fullbright", "See everything clearly, even in caves and at night.", Category.VISUAL, false);
		}

		@Override
		public void tick(MinecraftClient mc) {
			if (before < 0) before = mc.options.gamma;
			mc.options.gamma = 100;
		}

		@Override
		protected void onDisable() {
			MinecraftClient mc = MinecraftClient.getInstance();
			mc.options.gamma = before < 0 ? 1 : before;
			before = -1;
		}
	}

	/** Your own time of day; only you see it. */
	public static final class TimeChanger extends Module {
		public final NumberSetting hour = add(new NumberSetting("hour", "Time of day (hour)", 12, 0, 23.5, 0.5));

		public TimeChanger() {
			super("time", "Time Changer", "Set your own time of day. Only you see it.", Category.VISUAL, false);
		}

		@Override
		public void tick(MinecraftClient mc) {
			if (mc.world == null) return;
			long t = mc.world.getTimeOfDay();
			long ticks = Math.floorMod(Math.round((hour.get() - 6) * 1000), 24000L);
			mc.world.setTimeOfDay(t - Math.floorMod(t, 24000L) + ticks);
		}
	}

	/** Hides the scoreboard on the right of the screen (see InGameHudMixin). */
	public static final class HideScoreboard extends Module {
		public HideScoreboard() {
			super("hide_scoreboard", "Hide Scoreboard", "Hides the scoreboard on the side of the screen.", Category.VISUAL, false);
		}
	}
}
