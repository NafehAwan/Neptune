package net.tuffclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.tuffclient.setting.ColorSetting;

/** Picks a colour with red, green, blue (and opacity) sliders or a hex code. */
public final class ColorScreen extends VanillaScreen {
	private final ColorSetting color;
	private String hex;
	private boolean typing;

	public ColorScreen(Screen parent, ColorSetting color) {
		super(Component.literal(color.name), parent);
		this.color = color;
		this.hex = hexOf(color.get());
	}

	private static String hexOf(int argb) {
		return String.format("%08X", argb);
	}

	@Override
	protected void layout(Gfx g) {
		title(g);
		int w = 310, x = centerX() - w / 2;
		int y = beginList(g);
		int argb = color.get();
		// a preview on dark and light, so see-through colours show
		g.fill(x, y, x + w / 2, y + 30, 0xFF3A3A3A);
		g.fill(x + w / 2, y, x + w, y + 30, 0xFFC6C6C6);
		g.fill(x + 10, y + 5, x + w - 10, y + 25, color.argb());
		g.outline(x, y, w, 30, 0xFF000000);
		y += 36;
		String[] names = {"Red", "Green", "Blue", "Opacity"};
		int[] shifts = {16, 8, 0, 24};
		for (int i = 0; i < (color.alpha ? 4 : 3); i++) {
			int shift = shifts[i];
			int v = (argb >>> shift) & 0xFF;
			slider(g, x, y, w, Component.literal(names[i] + ": " + v), v / 255.0, true, f -> {
				int nv = (int) Math.round(f * 255);
				int cur = color.get();
				color.set((cur & ~(0xFF << shift)) | (nv << shift));
				hex = hexOf(color.get());
			});
			y += ROW;
		}
		g.text(font, "Hex (AARRGGBB)", x, y + 1, GREY, true);
		y += 11;
		textField(g, x, y, w, hex, typing, true, () -> typing = true);
		y += ROW;
		button(g, x, y, w, onOff("Rainbow", color.chroma), true, () -> color.chroma = !color.chroma);
		y += ROW;
		button(g, x, y, w, Component.literal("Reset"), true, () -> {
			color.reset();
			hex = hexOf(color.get());
		});
		y += ROW;
		endList(g, y);
		button(g, centerX() - 100, height - FOOTER + 7, 200, Component.translatable("gui.done"), false, this::onClose);
	}

	private void commit() {
		Integer parsed = ColorSetting.parse(hex);
		if (parsed != null) color.set(hex.length() <= 6 ? (color.get() & 0xFF000000) | (parsed & 0xFFFFFF) : parsed);
		hex = hexOf(color.get());
		typing = false;
	}

	@Override
	protected void clickedNothing() {
		if (typing) commit();
	}

	@Override
	protected boolean onKey(Input.Key key) {
		if (typing) {
			if (key.isEscape() || key.key() == InputConstants.KEY_RETURN) commit();
			else if (key.key() == InputConstants.KEY_BACKSPACE && !hex.isEmpty()) hex = hex.substring(0, hex.length() - 1);
			return true;
		}
		return super.onKey(key);
	}

	@Override
	protected boolean onType(Input.Typed typed) {
		if (typing) {
			String ch = typed.codepointAsString();
			if (ch.matches("[0-9a-fA-F]") && hex.length() < 8) hex += ch.toUpperCase();
			return true;
		}
		return super.onType(typed);
	}

	@Override
	public void onClose() {
		if (typing) commit();
		super.onClose();
	}
}
