package net.tuffclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.tuffclient.TuffClient;

/**
 * Drawing toolkit for the Glade screens: Plus Jakarta Sans and Fraunces text, anti-aliased rounded
 * shapes, tinted pixel icons and smoothly scaled textures. Positions are
 * floats and go through the pose matrix, so animations never snap to whole
 * GUI pixels.
 */
public final class Ui {
	public enum Font {
		BODY("body"), MEDIUM("medium"), SEMIBOLD("semibold"), BOLD("bold"), TITLE("title"), HERO("hero"),
		/** Small print: counts, hints, footers. */
		SMALL("small"),
		/** Bold small capitals for status bars. */
		CAPS("caps"),
		/** Tiny labels under sidebar icons and on badges. */
		TINY("tiny"),
		/** Mod names on cards. */
		NAME("name"),

		// Mockup fonts, sized in mockup pixels (see {@link #mockup}): the name says size and weight.
		UI_11_SB("ui11sb"), UI_11_5("ui11_5"), UI_12("ui12"), UI_12_5_M("ui12_5m"), UI_12_5("ui12_5"),
		UI_13_SB("ui13sb"), UI_13_5_SB("ui13_5sb"), UI_14("ui14"), UI_14_SB("ui14sb"), UI_15_SB("ui15sb"), UI_16_B("ui16b"),
		/** Fraunces italic: the wordmark, page titles and account names. */
		HEAD_96("head96"), HEAD_30("head30"), HEAD_22("head22"),
		/** Geist Mono for small labels and numbers. */
		MONO_10_5("mono10_5"), MONO_10_5_M("mono10_5m"), MONO_11_M("mono11m"), MONO_11_5("mono11_5");

		/** One copy per oversample level (1, 1.25 ... 3), so glyphs land 1:1 on screen pixels. */
		private final Identifier[] variants = new Identifier[MAX_OVERSAMPLE];

		Font(String id) {
			for (int i = 0; i < MAX_OVERSAMPLE; i++) {
				variants[i] = Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, id + "_" + (i + 1));
			}
		}

		Identifier description() {
			int level = Math.round(scale() * 4) - 4;
			return variants[Math.max(0, Math.min(MAX_OVERSAMPLE - 1, level))];
		}
	}

	/** Font copies with oversample 1, 1.25 ... 3 (see art/fonts.py). */
	private static final int MAX_OVERSAMPLE = 9;

	/** Rounded shapes for every on-screen corner radius from 1 to 40 pixels (see art/sprites.py). */
	private static final int MAX_RADIUS = 40;
	private static final Identifier[] ROUND = new Identifier[MAX_RADIUS + 1];
	private static final Identifier[] RING = new Identifier[MAX_RADIUS + 1];
	private static final Identifier SHADOW = id("shadow");
	private static final Identifier SHADOW_RING = id("shadow_ring");

	static {
		for (int i = 1; i <= MAX_RADIUS; i++) {
			ROUND[i] = id("round_" + i);
			RING[i] = id("ring_" + i);
		}
	}

	private Ui() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, path);
	}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	// ------------------------------------------------------------------ text

	/** Built text and measured widths, per font size variant: labels are drawn every frame. */
	private static final java.util.Map<String, Component> TEXTS = new java.util.HashMap<>();
	private static final java.util.Map<String, Integer> WIDTHS = new java.util.HashMap<>();

	private static String key(String s, Font font) {
		Identifier d = font.description();
		return System.identityHashCode(d) + "|" + s;
	}

	public static Component text(String s, Font font) {
		if (TEXTS.size() > 4096) TEXTS.clear();
		return TEXTS.computeIfAbsent(key(s, font), k -> Component.literal(s).withStyle(style -> Fonts.withFont(style, font.description())));
	}

	public static int width(String s, Font font) {
		if (WIDTHS.size() > 4096) WIDTHS.clear();
		String k = key(s, font);
		Integer w = WIDTHS.get(k);
		if (w == null) {
			w = mc().font.width(text(s, font));
			WIDTHS.put(k, w);
		}
		return w;
	}

	/** Fonts can change on a resource reload; measured widths are thrown away then. */
	public static void clearTextCache() {
		TEXTS.clear();
		WIDTHS.clear();
	}

	public static void text(Gfx g, String s, float x, float y, int color, Font font) {
		if ((color >>> 24) < 4 || s.isEmpty()) return;
		g.push();
		g.translate(x, y);
		g.text(mc().font, text(s, font), 0, 0, color, false);
		g.pop();
	}

	/**
	 * Text drawn at the game's fixed baseline, 7 units below the anchor, whatever
	 * the font size: these place text by its baseline instead, like CSS does.
	 */
	public static void textAt(Gfx g, String s, float x, float baseline, int color, Font font) {
		text(g, s, x, baseline - 7, color, font);
	}

	public static void centeredAt(Gfx g, String s, float cx, float baseline, int color, Font font) {
		text(g, s, cx - width(s, font) / 2f, baseline - 7, color, font);
	}

	public static void rightAt(Gfx g, String s, float rx, float baseline, int color, Font font) {
		text(g, s, rx - width(s, font), baseline - 7, color, font);
	}

	/** Baseline that centres a line of a {@code px}-sized font (cap height) on {@code cy}. */
	public static float mid(float cy, float px) {
		return cy + px * 0.36f;
	}

	// ------------------------------------------------------------------ mockup space

	/**
	 * GUI units per mockup pixel. The Grove Glass mockups are laid out at
	 * 1280x720; screens drawn in that space look the same at any window size.
	 */
	public static float mockup(int guiHeight) {
		return zoomFor(guiHeight, 720f, 640f);
	}

	/**
	 * GUI units per layout pixel for a screen laid out designHeight tall. The
	 * screen pixels per layout pixel are rounded down to a quarter step (1,
	 * 1.25, 1.5 ...), the steps Glade's fonts are made for, so text, corners
	 * and icons land exactly on screen pixels. The layout is then a little
	 * taller than designHeight; screens centre themselves in it.
	 */
	public static float zoomFor(int guiHeight, float designHeight, float minHeight) {
		int gs = Math.max(1, guiScale());
		float pixels = guiHeight * gs, p = pixels / designHeight;
		if (p >= 1) p = (float) Math.floor(p * 4 + 1e-3) / 4f;
		// a bit short (768 px laptops): still 1:1 if the content fits
		else if (pixels >= minHeight) p = 1;
		return p / gs;
	}

	/** Frosted glass, as in the mockups: soft green shadow, translucent white, bright rim and top highlight. */
	public static void frost(Gfx g, float x, float y, float w, float h, float r, int fill, float shadow, float alpha) {
		if (shadow > 0) shadowOutside(g, x, y, w, h, 26, shadow * alpha);
		round(g, x, y, w, h, r, Theme.alpha(fill, alpha), Theme.alpha(0xB3FFFFFF, alpha), 1);
		round(g, x + r * 0.7f, y + 1, w - r * 1.4f, 1, 0.5f, Theme.alpha(0xCCFFFFFF, alpha));
	}

	public static void centered(Gfx g, String s, float cx, float y, int color, Font font) {
		text(g, s, cx - width(s, font) / 2f, y, color, font);
	}

	public static void right(Gfx g, String s, float rx, float y, int color, Font font) {
		text(g, s, rx - width(s, font), y, color, font);
	}

	/** Draws text with extra space between letters (for wordmarks). */
	public static float spaced(Gfx g, String s, float x, float y, int color, Font font, float spacing) {
		for (int i = 0; i < s.length(); i++) {
			String ch = String.valueOf(s.charAt(i));
			text(g, ch, x, y, color, font);
			x += width(ch, font) + spacing;
		}
		return x;
	}

	public static float spacedWidth(String s, Font font, float spacing) {
		float w = 0;
		for (int i = 0; i < s.length(); i++) w += width(String.valueOf(s.charAt(i)), font) + spacing;
		return s.isEmpty() ? 0 : w - spacing;
	}

	public static String ellipsize(String s, Font font, int maxWidth) {
		if (width(s, font) <= maxWidth) return s;
		String dots = "…";
		int end = s.length();
		while (end > 0 && width(s.substring(0, end).stripTrailing() + dots, font) > maxWidth) end--;
		return s.substring(0, end).stripTrailing() + dots;
	}

	// ------------------------------------------------------------------ shapes

	/** Filled rounded rectangle. Picks the sprite whose corner is closest to 1:1 on screen. */
	public static void round(Gfx g, float x, float y, float w, float h, float r, int argb) {
		roundSprite(g, ROUND, x, y, w, h, r, argb);
	}

	/** Rounded outline only, about one screen pixel wide, like a CSS border. */
	public static void outline(Gfx g, float x, float y, float w, float h, float r, int argb) {
		roundSprite(g, RING, x, y, w, h, r, argb);
	}

	private static void roundSprite(Gfx g, Identifier[] set, float x, float y, float w, float h, float r, int argb) {
		if ((argb >>> 24) == 0 || w <= 0 || h <= 0) return;
		r = Math.max(0.5f, Math.min(r, Math.min(w, h) / 2));
		// draw in screen pixels with the shape made for this radius, so every
		// texel is exactly one pixel (scaled sprites look broken up)
		float scale = scale();
		int radius = Math.max(1, Math.min(MAX_RADIUS, Math.round(r * scale)));
		// past the largest shape (4K screens) the largest one is scaled up instead
		float s = Math.max(1f / scale, r / MAX_RADIUS);
		g.push();
		g.translate(x, y);
		g.scale(s, s);
		g.sprite(set[radius], 0, 0, Math.max(2 * radius, Math.round(w / s)), Math.max(2 * radius, Math.round(h / s)), argb);
		g.pop();
	}

	/** Rounded rectangle with a border of the given width. */
	public static void round(Gfx g, float x, float y, float w, float h, float r, int fill, int border, float bw) {
		// the fill shows through see-through borders, so the border is only an outline on top
		round(g, x, y, w, h, r, fill);
		outline(g, x, y, w, h, r, border);
	}

	public static void circle(Gfx g, float cx, float cy, float radius, int argb) {
		round(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, argb);
	}

	/** Soft drop shadow around a rectangle. */
	public static void shadow(Gfx g, float x, float y, float w, float h, float spread, float alpha) {
		float s = spread / 16f;
		g.push();
		g.translate(x - spread, y - spread + spread * 0.35f);
		g.scale(s, s);
		g.sprite(SHADOW, 0, 0, Math.round((w + 2 * spread) / s), Math.round((h + 2 * spread) / s), Theme.alpha(0xFF1E3C28, alpha));
		g.pop();
	}

	/** Soft shadow drawn only outside the rectangle, for see-through panels (like a CSS box-shadow). */
	public static void shadowOutside(Gfx g, float x, float y, float w, float h, float spread, float alpha) {
		float s = spread / 16f;
		g.push();
		g.translate(x - spread, y - spread + spread * 0.35f);
		g.scale(s, s);
		g.sprite(SHADOW_RING, 0, 0, Math.round((w + 2 * spread) / s), Math.round((h + 2 * spread) / s), Theme.alpha(0xFF1E3C28, alpha));
		g.pop();
	}

	// ------------------------------------------------------------------ icons & images

	/** 12x12 pixel icon, drawn at a whole multiple so it stays crisp. */
	public static void icon(Gfx g, String name, float x, float y, int size, int argb) {
		if ((argb >>> 24) == 0) return;
		// Pixel icons stay crisp only at whole multiples of their 12px grid, on whole physical pixels.
		float p = scale();
		float drawn = Math.max(1, Math.round(size * p / 12f)) * 12 / p;
		float off = (size - drawn) / 2;
		g.push();
		g.translate(Math.round((x + off) * p) / p, Math.round((y + off) * p) / p);
		g.scale(drawn / size, drawn / size);
		g.sprite(id("icon/" + name), 0, 0, size, size, argb);
		g.pop();
	}

	/** Draws a whole texture into a float rectangle. */
	public static void image(Gfx g, Identifier texture, float x, float y, float w, float h, int texW, int texH, int argb) {
		if ((argb >>> 24) == 0) return;
		g.push();
		g.translate(x, y);
		g.scale(w / texW, h / texH);
		g.texture(texture, 0, 0, 0f, 0f, texW, texH, texW, texH, texW, texH, argb);
		g.pop();
	}

	// ------------------------------------------------------------------ widgets

	/** Animated switch, 22x12. */
	public static void toggle(Gfx g, float x, float y, float anim, float alpha) {
		int track = Theme.mix(Theme.OFF, Theme.COPPER, anim);
		round(g, x, y, 22, 12, 6, Theme.alpha(track, alpha));
		float knob = x + 2 + 10 * anim;
		circle(g, knob + 4, y + 6.4f, 4, Theme.alpha(0x221E3C28, alpha));
		circle(g, knob + 4, y + 6, 4, Theme.alpha(0xFFFFFFFF, alpha));
	}

	/** Accent pill button with a soft highlight. */
	public static void accentButton(Gfx g, float x, float y, float w, float h, float hover, float alpha) {
		round(g, x, y, w, h, Math.min(7, h / 2), Theme.alpha(Theme.mix(Theme.COPPER, Theme.COPPER_HI, hover * 0.6f), alpha));
		round(g, x + 1, y + 1, w - 2, h / 2f - 1, Math.min(6, h / 2), Theme.alpha(0x22FFFFFF, alpha));
	}

	/** Frosted glass button. */
	public static void glassButton(Gfx g, float x, float y, float w, float h, float hover, float alpha) {
		int fill = Theme.mix(0x9CFFFFFF, 0xEBFFFFFF, hover);
		int border = Theme.mix(Theme.GLASS_RIM, Theme.alpha(Theme.COPPER, 0.45f), hover);
		round(g, x, y, w, h, Math.min(7, h / 2), Theme.alpha(fill, alpha), Theme.alpha(border, alpha), 1);
	}

	/**
	 * A frosted glass panel: soft green-tinted shadow, translucent white body
	 * and a bright rim with a highlight along the top edge.
	 */
	public static void glass(Gfx g, float x, float y, float w, float h, float r, int fill, float alpha) {
		shadow(g, x, y, w, h, 16, 0.16f * alpha);
		round(g, x, y, w, h, r, Theme.alpha(fill, alpha), Theme.alpha(Theme.GLASS_RIM, alpha), 1);
		round(g, x + r * 0.6f, y + 1, w - r * 1.2f, 1, 0.5f, Theme.alpha(0x99FFFFFF, alpha));
	}

	private static final int[] LUCIDE_SIZES = {12, 14, 16, 18, 20, 22, 24, 28, 32, 36, 40, 48, 56, 64, 80, 96, 128};

	/**
	 * Draws a Lucide line icon with the sprite closest to its on-screen size,
	 * one texel per screen pixel (centred in the box), so it stays crisp.
	 */
	public static void lucide(Gfx g, String name, float x, float y, float size, int argb) {
		if ((argb >>> 24) == 0) return;
		float scale = scale(), physical = size * scale;
		int pick = LUCIDE_SIZES[0];
		for (int s : LUCIDE_SIZES) {
			if (Math.abs(s - physical) < Math.abs(pick - physical)) pick = s;
		}
		// past the largest sprite, scale it up instead
		float s = Math.max(1f / scale, size / LUCIDE_SIZES[LUCIDE_SIZES.length - 1]), drawn = pick * s;
		g.push();
		g.translate(x + (size - drawn) / 2, y + (size - drawn) / 2);
		g.scale(s, s);
		g.sprite(id("lucide/" + pick + "/" + name), 0, 0, pick, pick, argb);
		g.pop();
	}

	/** A small pill switch like the one on Lunar-style mod cards. */
	public static void pillSwitch(Gfx g, float x, float y, float anim, float alpha) {
		int track = Theme.mix(Theme.OFF, Theme.COPPER, anim);
		round(g, x, y, 20, 10, 5, Theme.alpha(track, alpha));
		circle(g, x + 5 + 10 * anim, y + 5, 3.5f, Theme.alpha(0xFFFFFFFF, alpha));
	}

	/** Extra scale a screen applies on top of the GUI scale (see {@link TuffMenuScreen}). */
	public static float zoom = 1f;

	public static int guiScale() {
		return (int) mc().getWindow().getGuiScale();
	}

	/** Physical pixels per GUI unit, including {@link #zoom}. */
	public static float scale() {
		return guiScale() * zoom;
	}
}
