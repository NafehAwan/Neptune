package net.tuffclient.legacy.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;

/** Active potion effects with their level and time left. */
public final class EffectsHud extends HudModule {
	public EffectsHud() {
		super("effects", "Potion Effects", "Your active potion effects and how long they last.", 0.005, 0.45, false);
	}

	private List<String[]> rows(boolean preview) {
		List<String[]> rows = new ArrayList<String[]>();
		if (mc().player != null) {
			Collection<StatusEffectInstance> effects = mc().player.getStatusEffectInstances();
			for (StatusEffectInstance e : effects) {
				StatusEffect type = StatusEffect.STATUS_EFFECTS[e.getEffectId()];
				if (type == null) continue;
				String name = I18n.translate(type.getTranslationKey());
				if (e.getAmplifier() > 0) name += " " + (e.getAmplifier() + 1);
				int secs = e.getDuration() / 20;
				rows.add(new String[] {name, secs > 3600 ? "**:**" : String.format("%d:%02d", secs / 60, secs % 60)});
			}
		}
		if (rows.isEmpty() && preview) rows.add(new String[] {"Speed 2", "1:30"});
		return rows;
	}

	@Override
	public int width() {
		int w = 40;
		for (String[] r : rows(true)) w = Math.max(w, pairWidth(r[0], r[1]));
		return w + 8;
	}

	@Override
	public int height() {
		return Math.max(1, rows(true).size()) * 10 + 6;
	}

	@Override
	protected void draw(boolean preview) {
		int y = 4;
		for (String[] r : rows(preview)) {
			pair(r[0], r[1], 4, y);
			y += 10;
		}
	}
}
