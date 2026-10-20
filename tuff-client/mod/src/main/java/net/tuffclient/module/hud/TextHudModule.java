package net.tuffclient.module.hud;

import net.tuffclient.gui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ChoiceSetting;

/** A single-line "label value" HUD element. */
public abstract class TextHudModule extends HudModule {
	private final String label;
	protected final BoolSetting showLabel;
	/** How label and value are put together: "FPS 60", "FPS: 60", "60 FPS", "[FPS 60]" or "[60 FPS]". */
	protected final ChoiceSetting format;

	protected TextHudModule(String id, String name, String description, String label, double x, double y, boolean enabled) {
		super(id, name, description, x, y, enabled);
		this.label = label;
		showLabel = label.isEmpty() ? null : add(new BoolSetting("show_label", "Show label", true));
		format = label.isEmpty() ? null : add(new ChoiceSetting("format", "Format", 0,
				label + " 12", label + ": 12", "12 " + label, "[" + label + " 12]", "[12 " + label + "]")).showIf(() -> showLabel.get());
	}

	/** The value to show, or null to hide (ignored in the HUD editor preview). */
	protected abstract String value(Minecraft client, boolean preview);

	@Override
	public void render(Gfx g, Font font, boolean preview) {
		String value = value(Minecraft.getInstance(), preview);
		if (value == null) {
			if (!preview) {
				width = 0;
				height = 0;
				return;
			}
			value = "-";
		}
		boolean labelled = showLabel != null && showLabel.get();
		int p = pad();
		// pieces in order; true marks the ones drawn in Label color
		java.util.List<String> parts = new java.util.ArrayList<>();
		java.util.List<Boolean> isLabel = new java.util.ArrayList<>();
		java.util.function.BiConsumer<String, Boolean> part = (str, lab) -> { parts.add(str); isLabel.add(lab); };
		int f = labelled ? format.get() : -1;
		switch (f) {
			case 1 -> { part.accept(label + ": ", true); part.accept(value, false); }
			case 2 -> { part.accept(value, false); part.accept(" " + label, true); }
			case 3 -> { part.accept("[" + label + " ", true); part.accept(value, false); part.accept("]", true); }
			case 4 -> { part.accept("[", true); part.accept(value, false); part.accept(" " + label + "]", true); }
			case 0 -> { part.accept(label + " ", true); part.accept(value, false); }
			default -> part.accept(value, false);
		}
		int total = 0;
		for (String str : parts) total += width(font, str);
		box(g, total + p * 2, linesHeight(1));
		int cx = p;
		for (int i = 0; i < parts.size(); i++) {
			if (isLabel.get(i)) label(g, font, parts.get(i), cx, p);
			else value(g, font, parts.get(i), cx, p);
			cx += width(font, parts.get(i));
		}
	}
}
