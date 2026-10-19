package net.tuffclient.module.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.tuffclient.gui.Theme;

public final class EffectsModule extends HudModule {
	private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

	public EffectsModule() {
		super("effects", "Potion Effects", "Active effects with time remaining.", 0.006, 0.48, false);
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font font, boolean preview) {
		LocalPlayer player = Minecraft.getInstance().player;
		List<MobEffectInstance> effects = new ArrayList<>();
		if (player != null) {
			for (MobEffectInstance e : player.getActiveEffects()) {
				if (e.showIcon()) effects.add(e);
			}
		}
		if (effects.isEmpty()) {
			if (preview) {
				int p = pad();
				box(g, 22 + font.width("Speed II") + p * 2, 22 + p * 2 - 4);
				text(g, font, "Speed II", p + 20, p + 1, textColor());
				text(g, font, "1:30", p + 20, p + 10, labelColor());
			} else {
				width = 0;
				height = 0;
			}
			return;
		}
		int p = pad();
		int w = 40;
		for (MobEffectInstance e : effects) w = Math.max(w, 20 + font.width(name(e)));
		box(g, w + p * 2, effects.size() * 22 + p * 2 - 4);
		int y = p - 2;
		for (MobEffectInstance e : effects) {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(e.getEffect()), p - 1, y + 2, 18, 18);
			text(g, font, name(e), p + 20, y + 3, textColor());
			String time = e.isInfiniteDuration() ? "**:**" : duration(e.getDuration());
			text(g, font, time, p + 20, y + 12, e.endsWithin(200) ? Theme.DANGER : labelColor());
			y += 22;
		}
	}

	private static String name(MobEffectInstance e) {
		int amp = e.getAmplifier();
		return Component.translatable(e.getDescriptionId()).getString() + (amp < ROMAN.length ? ROMAN[amp] : " " + (amp + 1));
	}

	private static String duration(int ticks) {
		int secs = ticks / 20;
		return secs / 60 + ":" + String.format("%02d", secs % 60);
	}
}
