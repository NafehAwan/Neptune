package net.tuffclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
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

		/** One copy per oversample level, so glyphs are rasterised near the size they are shown at. */
		private final FontDescription[] variants = new FontDescription[MAX_OVERSAMPLE];

		Font(String id) {
			for (int i = 0; i < MAX_OVERSAMPLE; i++) {
				variants[i] = new FontDescription.Resource(Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, id + "_" + (i + 1)));
			}
		}

		FontDescription description() {
			int oversample = (int) Math.ceil(scale() - 0.2f);
			return variants[Math.max(1, Math.min(MAX_OVERSAMPLE, oversample)) - 1];
		}
	}

	private static final int MAX_OVERSAMPLE = 6;

	private static final int[] RADII = {4, 8, 16, 32};
	private static final Identifier[] ROUND = new Identifier[RADII.length];
	private static final Identifier[] RING = new Identifier[RADII.length];
	private static final Identifier SHADOW = id("shadow");
	private static final Identifier SHADOW_RING = id("shadow_ring");

	static {
		for (int i = 0; i < RADII.length; i++) {
			ROUND[i] = id("round_" + RADII[i]);
			RING[i] = id("ring_" + RADII[i]);
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

	public static Component text(String s, Font font) {
		return Component.literal(s).withStyle(style -> style.withFont(font.description()));
	}

	public static int width(String s, Font font) {
		return mc().font.width(text(s, font));
	}

	public static void text(GuiGraphicsExtractor g, String s, float x, float y, int color, Font font) {
		if ((color >>> 24) < 4 || s.isEmpty()) return;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.text(mc().font, text(s, font), 0, 0, color, false);
		g.pose().popMatrix();
	}

	/**
	 * Text drawn at the game's fixed baseline, 7 units below the anchor, whatever
	 * the font size: these place text by its baseline instead, like CSS does.
	 */
	public static void textAt(GuiGraphicsExtractor g, String s, float x, float baseline, int color, Font font) {
		text(g, s, x, baseline - 7, color, font);
	}

	public static void centeredAt(GuiGraphicsExtractor g, String s, float cx, float baseline, int color, Font font) {
		text(g, s, cx - width(s, font) / 2f, baseline - 7, color, font);
	}

	public static void rightAt(GuiGraphicsExtractor g, String s, float rx, float baseline, int color, Font font) {
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
		return guiHeight / 720f;
	}

	/** Frosted glass, as in the mockups: soft green shadow, translucent white, bright rim and top highlight. */
	public static void frost(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int fill, float shadow, float alpha) {
		if (shadow > 0) shadowOutside(g, x, y, w, h, 26, shadow * alpha);
		round(g, x, y, w, h, r, Theme.alpha(fill, alpha), Theme.alpha(0xB3FFFFFF, alpha), 1);
		round(g, x + r * 0.7f, y + 1, w - r * 1.4f, 1, 0.5f, Theme.alpha(0xCCFFFFFF, alpha));
	}

	public static void centered(GuiGraphicsExtractor g, String s, float cx, float y, int color, Font font) {
		text(g, s, cx - width(s, font) / 2f, y, color, font);
	}

	public static void right(GuiGraphicsExtractor g, String s, float rx, float y, int color, Font font) {
		text(g, s, rx - width(s, font), y, color, font);
	}

	/** Draws text with extra space between letters (for wordmarks). */
	public static float spaced(GuiGraphicsExtractor g, String s, float x, float y, int color, Font font, float spacing) {
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
	public static void round(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int argb) {
		roundSprite(g, ROUND, x, y, w, h, r, argb);
	}

	/** Rounded outline only, about one screen pixel wide, like a CSS border. */
	public static void outline(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int argb) {
		roundSprite(g, RING, x, y, w, h, r, argb);
	}

	private static void roundSprite(GuiGraphicsExtractor g, Identifier[] set, float x, float y, float w, float h, float r, int argb) {
		if ((argb >>> 24) == 0 || w <= 0 || h <= 0) return;
		r = Math.max(0.5f, Math.min(r, Math.min(w, h) / 2));
		float physical = r * scale();
		int pick = RADII.length - 1;
		for (int i = 0; i < RADII.length; i++) {
			if (RADII[i] >= physical) {
				pick = i;
				break;
			}
		}
		float s = r / RADII[pick];
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(s, s);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, set[pick], 0, 0, Math.max(2 * RADII[pick], Math.round(w / s)), Math.max(2 * RADII[pick], Math.round(h / s)), argb);
		g.pose().popMatrix();
	}

	/** Rounded rectangle with a border of the given width. */
	public static void round(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int fill, int border, float bw) {
		// the fill shows through see-through borders, so the border is only an outline on top
		round(g, x, y, w, h, r, fill);
		outline(g, x, y, w, h, r, border);
	}

	public static void circle(GuiGraphicsExtractor g, float cx, float cy, float radius, int argb) {
		round(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, argb);
	}

	/** Soft drop shadow around a rectangle. */
	public static void shadow(GuiGraphicsExtractor g, float x, float y, float w, float h, float spread, float alpha) {
		float s = spread / 16f;
		g.pose().pushMatrix();
		g.pose().translate(x - spread, y - spread + spread * 0.35f);
		g.pose().scale(s, s);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, SHADOW, 0, 0, Math.round((w + 2 * spread) / s), Math.round((h + 2 * spread) / s), Theme.alpha(0xFF1E3C28, alpha));
		g.pose().popMatrix();
	}

	/** Soft shadow drawn only outside the rectangle, for see-through panels (like a CSS box-shadow). */
	public static void shadowOutside(GuiGraphicsExtractor g, float x, float y, float w, float h, float spread, float alpha) {
		float s = spread / 16f;
		g.pose().pushMatrix();
		g.pose().translate(x - spread, y - spread + spread * 0.35f);
		g.pose().scale(s, s);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, SHADOW_RING, 0, 0, Math.round((w + 2 * spread) / s), Math.round((h + 2 * spread) / s), Theme.alpha(0xFF1E3C28, alpha));
		g.pose().popMatrix();
	}

	// ------------------------------------------------------------------ icons & images

	/** 12x12 pixel icon, drawn at a whole multiple so it stays crisp. */
	public static void icon(GuiGraphicsExtractor g, String name, float x, float y, int size, int argb) {
		if ((argb >>> 24) == 0) return;
		// Pixel icons stay crisp only at whole multiples of their 12px grid, on whole physical pixels.
		float p = scale();
		float drawn = Math.max(1, Math.round(size * p / 12f)) * 12 / p;
		float off = (size - drawn) / 2;
		g.pose().pushMatrix();
		g.pose().translate(Math.round((x + off) * p) / p, Math.round((y + off) * p) / p);
		g.pose().scale(drawn / size, drawn / size);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, id("icon/" + name), 0, 0, size, size, argb);
		g.pose().popMatrix();
	}

	/** Draws a whole texture into a float rectangle. */
	public static void image(GuiGraphicsExtractor g, Identifier texture, float x, float y, float w, float h, int texW, int texH, int argb) {
		if ((argb >>> 24) == 0) return;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(w / texW, h / texH);
		g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0f, 0f, texW, texH, texW, texH, texW, texH, argb);
		g.pose().popMatrix();
	}

	// ------------------------------------------------------------------ widgets

	/** Animated switch, 22x12. */
	public static void toggle(GuiGraphicsExtractor g, float x, float y, float anim, float alpha) {
		int track = Theme.mix(Theme.OFF, Theme.COPPER, anim);
		round(g, x, y, 22, 12, 6, Theme.alpha(track, alpha));
		float knob = x + 2 + 10 * anim;
		circle(g, knob + 4, y + 6.4f, 4, Theme.alpha(0x221E3C28, alpha));
		circle(g, knob + 4, y + 6, 4, Theme.alpha(0xFFFFFFFF, alpha));
	}

	/** Accent pill button with a soft highlight. */
	public static void accentButton(GuiGraphicsExtractor g, float x, float y, float w, float h, float hover, float alpha) {
		round(g, x, y, w, h, Math.min(7, h / 2), Theme.alpha(Theme.mix(Theme.COPPER, Theme.COPPER_HI, hover * 0.6f), alpha));
		round(g, x + 1, y + 1, w - 2, h / 2f - 1, Math.min(6, h / 2), Theme.alpha(0x22FFFFFF, alpha));
	}

	/** Frosted glass button. */
	public static void glassButton(GuiGraphicsExtractor g, float x, float y, float w, float h, float hover, float alpha) {
		int fill = Theme.mix(0x9CFFFFFF, 0xEBFFFFFF, hover);
		int border = Theme.mix(Theme.GLASS_RIM, Theme.alpha(Theme.COPPER, 0.45f), hover);
		round(g, x, y, w, h, Math.min(7, h / 2), Theme.alpha(fill, alpha), Theme.alpha(border, alpha), 1);
	}

	/**
	 * A frosted glass panel: soft green-tinted shadow, translucent white body
	 * and a bright rim with a highlight along the top edge.
	 */
	public static void glass(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int fill, float alpha) {
		shadow(g, x, y, w, h, 16, 0.16f * alpha);
		round(g, x, y, w, h, r, Theme.alpha(fill, alpha), Theme.alpha(Theme.GLASS_RIM, alpha), 1);
		round(g, x + r * 0.6f, y + 1, w - r * 1.2f, 1, 0.5f, Theme.alpha(0x99FFFFFF, alpha));
	}

	private static final int[] LUCIDE_SIZES = {32, 64, 128};

	/** Draws a smooth Lucide line icon, picking the sprite size closest to its on-screen size. */
	public static void lucide(GuiGraphicsExtractor g, String name, float x, float y, float size, int argb) {
		if ((argb >>> 24) == 0) return;
		float physical = size * scale();
		int pick = LUCIDE_SIZES[LUCIDE_SIZES.length - 1];
		for (int s : LUCIDE_SIZES) {
			if (s >= physical * 0.85f) {
				pick = s;
				break;
			}
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(size / pick, size / pick);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, id("lucide/" + pick + "/" + name), 0, 0, pick, pick, argb);
		g.pose().popMatrix();
	}

	/** A small pill switch like the one on Lunar-style mod cards. */
	public static void pillSwitch(GuiGraphicsExtractor g, float x, float y, float anim, float alpha) {
		int track = Theme.mix(Theme.OFF, Theme.COPPER, anim);
		round(g, x, y, 20, 10, 5, Theme.alpha(track, alpha));
		circle(g, x + 5 + 10 * anim, y + 5, 3.5f, Theme.alpha(0xFFFFFFFF, alpha));
	}

	/** Extra scale a screen applies on top of the GUI scale (see {@link TuffMenuScreen}). */
	public static float zoom = 1f;

	public static int guiScale() {
		return mc().getWindow().getGuiScale();
	}

	/** Physical pixels per GUI unit, including {@link #zoom}. */
	public static float scale() {
		return guiScale() * zoom;
	}
}
