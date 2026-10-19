package net.tuffclient.module.hud;

import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.tuffclient.gui.Theme;
import net.tuffclient.gui.Ui;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ColorSetting;
import net.tuffclient.setting.NumberSetting;
import net.tuffclient.setting.Setting;

/**
 * A draggable HUD element. Position is stored as a fraction of the screen so
 * layouts survive window and GUI-scale changes. Every element shares the same
 * style settings (colours, shadow, background, border, corners, padding),
 * listed under Style in its settings.
 */
public abstract class HudModule extends Module {
	private final Set<Setting<?>> style = new HashSet<>();

	public double x;
	public double y;
	public final double defaultX;
	public final double defaultY;
	public final NumberSetting scale = styled(new NumberSetting("scale", "Scale", 1.0, 0.5, 2.0, 0.1));
	public final ColorSetting textColor = styled(new ColorSetting("text_color", "Text color", Theme.HUD_TEXT, true));
	public final ColorSetting labelColor = styled(new ColorSetting("label_color", "Label color", Theme.HUD_MUTED, true));
	public final BoolSetting shadow = styled(new BoolSetting("shadow", "Text shadow", true));
	public final BoolSetting background = styled(new BoolSetting("background", "Background", true));
	public final ColorSetting backgroundColor = styled(new ColorSetting("background_color", "Background color", 0x9A101210, true)).showIf(() -> background.get());
	public final BoolSetting border = styled(new BoolSetting("border", "Border", false));
	public final ColorSetting borderColor = styled(new ColorSetting("border_color", "Border color", 0x80FFFFFF, true)).showIf(() -> border.get());
	public final NumberSetting radius = styled(new NumberSetting("radius", "Corner radius", 3, 0, 8, 1)).showIf(() -> background.get() || border.get());
	public final NumberSetting padding = styled(new NumberSetting("padding", "Padding", 4, 0, 10, 1));

	/** Size of the last render, in unscaled GUI pixels. */
	protected int width = 40;
	protected int height = 14;

	protected HudModule(String id, String name, String description, double x, double y, boolean enabled) {
		super(id, name, description, Category.HUD, enabled);
		this.x = x;
		this.y = y;
		this.defaultX = x;
		this.defaultY = y;
	}

	private <S extends Setting<?>> S styled(S setting) {
		style.add(setting);
		return add(setting);
	}

	/** Shared look settings, shown in their own section. */
	public boolean isStyle(Setting<?> setting) {
		return style.contains(setting);
	}

	public void resetPosition() {
		x = defaultX;
		y = defaultY;
		scale.set(1.0);
	}

	/** Draws at the origin; the manager has already translated and scaled. */
	public abstract void render(GuiGraphicsExtractor g, Font font, boolean preview);

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	protected int pad() {
		return padding.get().intValue();
	}

	/** Height of a box holding {@code lines} lines of text. */
	protected int linesHeight(int lines) {
		return pad() * 2 + lines * 10 - 3;
	}

	protected int textColor() {
		return textColor.argb();
	}

	protected int labelColor() {
		return labelColor.argb();
	}

	/** Text in this element's style (with or without a shadow). */
	protected void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
		g.text(font, s, x, y, color, shadow.get());
	}

	/** Sets the element's size and draws its background and border. */
	protected void box(GuiGraphicsExtractor g, int w, int h) {
		width = w;
		height = h;
		float r = radius.get().floatValue();
		if (background.get()) {
			if (r > 0) Ui.round(g, 0, 0, w, h, r, backgroundColor.argb());
			else g.fill(0, 0, w, h, backgroundColor.argb());
		}
		if (border.get()) {
			int c = borderColor.argb();
			if (r > 0) Ui.outline(g, 0, 0, w, h, r, c);
			else {
				g.fill(0, 0, w, 1, c);
				g.fill(0, h - 1, w, h, c);
				g.fill(0, 1, 1, h - 1, c);
				g.fill(w - 1, 1, w, h - 1, c);
			}
		}
	}

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		json.addProperty("x", x);
		json.addProperty("y", y);
		return json;
	}

	@Override
	public void load(JsonObject json) {
		super.load(json);
		if (json.has("x")) x = json.get("x").getAsDouble();
		if (json.has("y")) y = json.get("y").getAsDouble();
		// older versions had five named text colours
		if (json.has("settings") && json.getAsJsonObject("settings").has("color")) {
			String old = json.getAsJsonObject("settings").get("color").getAsString();
			Integer c = switch (old) {
				case "Copper" -> Theme.COPPER_HI;
				case "Mint" -> 0xFF8FE3B0;
				case "Sky" -> 0xFF8EC9FF;
				case "Gold" -> 0xFFF2D06B;
				default -> null;
			};
			if (c != null && !json.getAsJsonObject("settings").has("text_color")) textColor.set(c);
		}
	}
}
