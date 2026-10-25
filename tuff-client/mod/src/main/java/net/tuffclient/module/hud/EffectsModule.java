package net.tuffclient.module.hud;

import net.tuffclient.gui.Gfx;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.tuffclient.gui.Theme;

public final class EffectsModule extends HudModule {
	private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

	private final net.tuffclient.setting.BoolSetting icons = add(new net.tuffclient.setting.BoolSetting("icons", "Show icons", true));
	private final net.tuffclient.setting.BoolSetting names = add(new net.tuffclient.setting.BoolSetting("names", "Show names", true));
	private final net.tuffclient.setting.BoolSetting times = add(new net.tuffclient.setting.BoolSetting("times", "Show time left", true));
	private final net.tuffclient.setting.BoolSetting roman = add(new net.tuffclient.setting.BoolSetting("roman", "Levels as Roman numerals", true));
	private final net.tuffclient.setting.BoolSetting blink = add(new net.tuffclient.setting.BoolSetting("blink", "Blink in the last 10 seconds", true));
	private final net.tuffclient.setting.BoolSetting hidePermanent = add(new net.tuffclient.setting.BoolSetting("hide_permanent", "Hide effects that never run out", false));

	public EffectsModule() {
		super("effects", "Potion Effects", "Active effects with time remaining.", 0.006, 0.48, false);
	}

	@Override
	public void render(Gfx g, Font font, boolean preview) {
		LocalPlayer player = Minecraft.getInstance().player;
		List<MobEffectInstance> effects = new ArrayList<>();
		if (player != null) {
			for (MobEffectInstance e : player.getActiveEffects()) {
				if (!e.showIcon() || hidePermanent.get() && e.isInfiniteDuration()) continue;
				effects.add(e);
			}
		}
		boolean showIcon = icons.get(), showName = names.get(), showTime = times.get() || !showName;
		int lines = (showName ? 1 : 0) + (showTime ? 1 : 0);
		int rowH = Math.max(showIcon ? 20 : 0, lines * 9 + 2) + 2;
		int textX = showIcon ? 20 : 0;
		int p = pad();
		if (effects.isEmpty()) {
			if (preview) {
				int w = textX + Math.max(showName ? width(font, "Speed II") : 0, showTime ? width(font, "1:30") : 0);
				box(g, w + p * 2, rowH + p * 2 - 4);
				int ty = p - 2 + (rowH - lines * 9) / 2;
				if (showName) { value(g, font, "Speed II", p + textX, ty); ty += 9; }
				if (showTime) label(g, font, "1:30", p + textX, ty);
			} else {
				width = 0;
				height = 0;
			}
			return;
		}
		int w = showIcon ? 18 : 10;
		for (MobEffectInstance e : effects) {
			int tw = Math.max(showName ? width(font, name(e)) : 0, showTime ? width(font, time(e)) : 0);
			w = Math.max(w, textX + tw);
		}
		box(g, w + p * 2, effects.size() * rowH + p * 2 - 4);
		int y = p - 2;
		boolean blinkOff = blink.get() && System.currentTimeMillis() / 250 % 2 == 0;
		for (MobEffectInstance e : effects) {
			if (showIcon) {
				var icon = net.tuffclient.compat.EffectIcons.sprite(e.getEffect());
				if (icon != null) g.sprite(icon, p - 1, y + (rowH - 18) / 2, 18, 18);
			}
			int ty = y + (rowH - lines * 9) / 2;
			if (showName) {
				value(g, font, name(e), p + textX, ty);
				ty += 9;
			}
			if (showTime) {
				boolean ending = !e.isInfiniteDuration() && e.endsWithin(200);
				if (ending && blinkOff) {
					// hidden on every other beat while it blinks
				} else if (ending) text(g, font, time(e), p + textX, ty, Theme.DANGER);
				else label(g, font, time(e), p + textX, ty);
			}
			y += rowH;
		}
	}

	private static String time(MobEffectInstance e) {
		return e.isInfiniteDuration() ? "**:**" : duration(e.getDuration());
	}

	private String name(MobEffectInstance e) {
		int amp = e.getAmplifier();
		String level = amp == 0 ? "" : roman.get() && amp < ROMAN.length ? ROMAN[amp] : " " + (amp + 1);
		return Component.translatable(e.getDescriptionId()).getString() + level;
	}

	private static String duration(int ticks) {
		int secs = ticks / 20;
		return secs / 60 + ":" + String.format("%02d", secs % 60);
	}
}
