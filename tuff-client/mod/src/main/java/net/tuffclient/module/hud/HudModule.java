package net.tuffclient.module.hud;

import net.tuffclient.gui.Gfx;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.tuffclient.gui.Theme;
import net.tuffclient.gui.Ui;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ChoiceSetting;
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
	public final ColorSetting textColor = styled(new ColorSetting("text_color", "Text color", 0xFFFFFFFF, true)).showIf(() -> usesTextColors());
	public final ColorSetting labelColor = styled(new ColorSetting("label_color", "Label color", 0xFFFFFFFF, true)).showIf(() -> usesTextColors());
	public final BoolSetting shadow = styled(new BoolSetting("shadow", "Text shadow", true));
	public final BoolSetting background = styled(new BoolSetting("background", "Background", boxedByDefault()));
	public final ColorSetting backgroundColor = styled(new ColorSetting("background_color", "Background color", 0x9A101210, true)).showIf(() -> background.get() && usesTextColors());
	public final BoolSetting border = styled(new BoolSetting("border", "Border", false));
	public final ColorSetting borderColor = styled(new ColorSetting("border_color", "Border color", 0x80FFFFFF, true)).showIf(() -> border.get());
	public final ChoiceSetting shape = styled(new ChoiceSetting("shape", "Background shape", 1, "Sharp", "Rounded", "Smooth")).showIf(() -> background.get() || border.get());
	public final NumberSetting padding = styled(new NumberSetting("padding", "Padding", 4, 0, 10, 1));
	/** Minecraft's own font, or its small unicode one (minecraft:uniform). */
	public final ChoiceSetting textFont = styled(new ChoiceSetting("font", "Font", 0, "Default", "Small"));

	private static final net.minecraft.resources.Identifier SMALL = net.minecraft.resources.Identifier.withDefaultNamespace("uniform");

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

	/** Plain text by default; Keystrokes keeps its key boxes. */
	protected boolean boxedByDefault() {
		return false;
	}

	/** Corner radius for the background shape: sharp, rounded or smooth (fully round ends, capped at half the height by the caller). */
	public float cornerRadius() {
		return switch (shape.get()) {
			case 0 -> 0f;
			case 2 -> 6f;
			default -> 2.5f;
		};
	}

	/** Keystrokes has its own key colours, so it hides the shared text and background colours. */
	protected boolean usesTextColors() {
		return true;
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
	public abstract void render(Gfx g, Font font, boolean preview);

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

	/** A value (the number, the name): drawn in Text color, with chroma if it's on. */
	protected void value(Gfx g, Font font, String s, int x, int y) {
		colored(g, font, s, x, y, textColor);
	}

	/** A label (FPS, XYZ, Time): drawn in Label color, with chroma if it's on. */
	protected void label(Gfx g, Font font, String s, int x, int y) {
		colored(g, font, s, x, y, labelColor);
	}

	/** Text in a colour setting; with chroma wave on, each letter is a step along the rainbow. */
	protected void colored(Gfx g, Font font, String s, int x, int y, ColorSetting c) {
		if (s.isEmpty()) return;
		if (!c.chroma || !c.wave) {
			text(g, font, s, x, y, c.argb());
			return;
		}
		int cx = x;
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			String ch = new String(Character.toChars(cp));
			text(g, font, ch, cx, y, c.argb((float) (this.x * 2 + cx * 0.006)));
			cx += width(font, ch);
			i += Character.charCount(cp);
		}
	}

	/** Quick looks for the Style section: Default (the original look), Clean (just text), Boxed and Outline. */
	public void applyLook(String look) {
		switch (look) {
			case "Default" -> {
				// Glade's default look: white text with a shadow, no box, no rainbow
				for (Setting<?> s : settings()) {
					if (isStyle(s) && s instanceof ColorSetting c) c.reset();
				}
				shadow.set(true);
				background.set(boxedByDefault());
				border.set(false);
				shape.set(1);
				padding.set(4.0);
				textFont.set(0);
			}
			case "Clean" -> {
				background.set(false);
				border.set(false);
				shadow.set(true);
			}
			case "Boxed" -> {
				background.set(true);
				backgroundColor.set(0x9A101210);
				border.set(false);
				shape.set(1);
			}
			case "Outline" -> {
				background.set(false);
				border.set(true);
				borderColor.set(0xB3FFFFFF);
				shape.set(1);
				shadow.set(true);
			}
			default -> {
			}
		}
	}

	/** Text in this element's style (with or without a shadow). */
	protected void text(Gfx g, Font font, String s, int x, int y, int color) {
		if (textFont.get() == 1) g.text(font, small(s), x, y, color, shadow.get());
		else g.text(font, s, x, y, color, shadow.get());
	}

	private static net.minecraft.network.chat.Component small(String s) {
		return net.minecraft.network.chat.Component.literal(s).withStyle(st -> net.tuffclient.gui.Fonts.withFont(st, SMALL));
	}

	/** Width of text in this element's font. */
	protected int width(Font font, String s) {
		return textFont.get() == 1 ? font.width(small(s)) : font.width(s);
	}

	/** Sets the element's size and draws its background and border. */
	protected void box(Gfx g, int w, int h) {
		width = w;
		height = h;
		float r = Math.min(cornerRadius(), h / 2f);
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
