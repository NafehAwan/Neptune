package net.tuffclient.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Tuff + Copper palette (ARGB) and small drawing helpers shared by every screen. */
public final class Theme {
	public static final int BG = 0xFF141614;
	public static final int SURFACE = 0xFF1E211F;
	public static final int SURFACE_2 = 0xFF262A27;
	public static final int SURFACE_3 = 0xFF2F3430;
	public static final int BORDER = 0xFF2C302D;
	public static final int BORDER_STRONG = 0xFF3A3F3B;
	public static final int TEXT = 0xFFE8E6E1;
	public static final int MUTED = 0xFF8B8F88;
	public static final int FAINT = 0xFF5F645D;
	public static final int COPPER = 0xFFD9824B;
	public static final int COPPER_HI = 0xFFF0A877;
	public static final int COPPER_LO = 0xFF9C5530;
	public static final int COPPER_INK = 0xFF1A0F08;
	public static final int ONLINE = 0xFF6FBF73;
	public static final int DANGER = 0xFFD9584B;
	public static final int SCRIM = 0xB00A0B0A;

	private Theme() {
	}

	public static int alpha(int argb, float a) {
		int alpha = Math.round(((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, a)));
		return (alpha << 24) | (argb & 0xFFFFFF);
	}

	public static int mix(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		int al = (int) (((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t);
		return (al << 24) | (r << 16) | (g << 8) | bl;
	}

	/** Filled box with a one-pixel border. */
	public static void panel(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int fill, int border) {
		g.fill(x1, y1, x2, y2, border);
		g.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);
	}

	/** Copper "pixel" button: bevelled 1px highlight and shadow. */
	public static void pixelButton(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, float hover) {
		int face = mix(COPPER, COPPER_HI, hover);
		g.fill(x1 - 1, y1, x2 + 1, y2, 0xFF0D0E0D);
		g.fill(x1, y1 - 1, x2, y2 + 1, 0xFF0D0E0D);
		g.fill(x1, y1, x2, y2, face);
		g.fill(x1, y1, x2, y1 + 1, COPPER_HI);
		g.fill(x1, y1, x1 + 1, y2, COPPER_HI);
		g.fill(x1, y2 - 1, x2, y2, COPPER_LO);
		g.fill(x2 - 1, y1, x2, y2, COPPER_LO);
	}

	public static void centered(GuiGraphicsExtractor g, Font font, String text, int cx, int y, int color) {
		g.text(font, text, cx - font.width(text) / 2, y, color);
	}

	/** Toggle switch, 18x10. */
	public static void toggle(GuiGraphicsExtractor g, int x, int y, boolean on, float anim) {
		int track = mix(SURFACE_3, COPPER_LO, anim);
		g.fill(x, y, x + 18, y + 10, track);
		int knob = x + 1 + Math.round(8 * anim);
		g.fill(knob, y + 1, knob + 8, y + 9, on ? COPPER_HI : MUTED);
	}

	public static boolean inside(double mx, double my, int x1, int y1, int x2, int y2) {
		return mx >= x1 && mx < x2 && my >= y1 && my < y2;
	}

	/** Frame-rate independent easing towards a target. */
	public static float approach(float current, float target, float dt, float speed) {
		return current + (target - current) * (1 - (float) Math.exp(-speed * dt));
	}
}
