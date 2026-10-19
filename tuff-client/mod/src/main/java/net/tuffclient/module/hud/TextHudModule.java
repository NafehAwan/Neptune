package net.tuffclient.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.tuffclient.setting.BoolSetting;

/** A single-line "label value" HUD element. */
public abstract class TextHudModule extends HudModule {
	private final String label;
	protected final BoolSetting showLabel;

	protected TextHudModule(String id, String name, String description, String label, double x, double y, boolean enabled) {
		super(id, name, description, x, y, enabled);
		this.label = label;
		showLabel = label.isEmpty() ? null : add(new BoolSetting("show_label", "Show label", true));
	}

	/** The value to show, or null to hide (ignored in the HUD editor preview). */
	protected abstract String value(Minecraft client, boolean preview);

	@Override
	public void render(GuiGraphicsExtractor g, Font font, boolean preview) {
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
		int labelWidth = labelled ? font.width(label) + 4 : 0;
		box(g, labelWidth + font.width(value) + p * 2, linesHeight(1));
		if (labelled) label(g, font, label, p, p);
		value(g, font, value, p + labelWidth, p);
	}
}
