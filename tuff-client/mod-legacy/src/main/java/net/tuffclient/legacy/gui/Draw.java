package net.tuffclient.legacy.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.opengl.GL11;

/** Glade's drawing toolkit for 1.8.9: rounded panels, circles and scaled text. */
public final class Draw {
	/** Grove Glass colors. */
	public static final int INK = 0xFF1F2A22, MUTED = 0xFF5E6B61, FAINT = 0xFF9AA59C;
	public static final int ACCENT = 0xFF2F6B4F, ACCENT_HI = 0xFF3D8A66, PAPER = 0xFFF6F4EE;
	public static final int GLASS = 0xE6F6F4EE, CARD = 0xCCFFFFFF, LINE = 0x221F2A22;

	private Draw() {
	}

	public static TextRenderer font() {
		return MinecraftClient.getInstance().textRenderer;
	}

	private static void color(int argb) {
		GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
	}

	private static void begin(int argb) {
		GlStateManager.enableBlend();
		GlStateManager.disableTexture();
		GlStateManager.blendFuncSeparate(770, 771, 1, 0);
		color(argb);
	}

	private static void end() {
		GlStateManager.color(1, 1, 1, 1);
		GlStateManager.enableTexture();
		GlStateManager.disableBlend();
	}

	public static void rect(float x, float y, float w, float h, int argb) {
		if ((argb >>> 24) == 0) return;
		begin(argb);
		Tessellator t = Tessellator.getInstance();
		BufferBuilder b = t.getBuffer();
		b.begin(GL11.GL_QUADS, VertexFormats.POSITION);
		b.vertex(x, y + h, 0).next();
		b.vertex(x + w, y + h, 0).next();
		b.vertex(x + w, y, 0).next();
		b.vertex(x, y, 0).next();
		t.draw();
		end();
	}

	/** A rectangle with rounded corners of radius r. */
	public static void round(float x, float y, float w, float h, float r, int argb) {
		if ((argb >>> 24) == 0 || w <= 0 || h <= 0) return;
		r = Math.min(r, Math.min(w, h) / 2);
		if (r < 0.5f) {
			rect(x, y, w, h, argb);
			return;
		}
		begin(argb);
		GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
		Tessellator t = Tessellator.getInstance();
		BufferBuilder b = t.getBuffer();
		b.begin(GL11.GL_POLYGON, VertexFormats.POSITION);
		corner(b, x + w - r, y + r, r, 0);
		corner(b, x + r, y + r, r, 90);
		corner(b, x + r, y + h - r, r, 180);
		corner(b, x + w - r, y + h - r, r, 270);
		t.draw();
		GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
		end();
	}

	private static void corner(BufferBuilder b, float cx, float cy, float r, int start) {
		for (int a = start; a <= start + 90; a += 10) {
			double rad = Math.toRadians(a);
			b.vertex(cx + Math.cos(rad) * r, cy - Math.sin(rad) * r, 0).next();
		}
	}

	public static void circle(float cx, float cy, float r, int argb) {
		round(cx - r, cy - r, r * 2, r * 2, r, argb);
	}

	/** A 1px outline around a rounded rectangle. */
	public static void outline(float x, float y, float w, float h, float r, int argb) {
		begin(argb);
		GL11.glLineWidth(1.2f);
		GL11.glEnable(GL11.GL_LINE_SMOOTH);
		Tessellator t = Tessellator.getInstance();
		BufferBuilder b = t.getBuffer();
		b.begin(GL11.GL_LINE_LOOP, VertexFormats.POSITION);
		r = Math.min(r, Math.min(w, h) / 2);
		corner(b, x + w - r, y + r, r, 0);
		corner(b, x + r, y + r, r, 90);
		corner(b, x + r, y + h - r, r, 180);
		corner(b, x + w - r, y + h - r, r, 270);
		t.draw();
		GL11.glDisable(GL11.GL_LINE_SMOOTH);
		end();
	}

	/** Glass panel: soft shadow, frosted fill and a white rim. */
	public static void glass(float x, float y, float w, float h, float r, float alpha) {
		for (int i = 1; i <= 4; i++) round(x - i, y - i + 2, w + 2 * i, h + 2 * i, r + i, alphaOf(0x101E3C28, alpha));
		round(x, y, w, h, r, alphaOf(GLASS, alpha));
		outline(x, y, w, h, r, alphaOf(0xB3FFFFFF, alpha));
	}

	public static int alphaOf(int argb, float alpha) {
		return ((int) ((argb >>> 24) * Math.max(0, Math.min(1, alpha))) << 24) | (argb & 0xFFFFFF);
	}

	public static int mix(int a, int b, float t) {
		int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255, aa = a >>> 24;
		int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255, ba = b >>> 24;
		return (int) (aa + (ba - aa) * t) << 24 | (int) (ar + (br - ar) * t) << 16 | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
	}

	/** Text at a float position and scale. */
	public static void text(String s, float x, float y, int color, float scale, boolean shadow) {
		if ((color >>> 24) < 4 || s.isEmpty()) return;
		GlStateManager.pushMatrix();
		GlStateManager.translate(x, y, 0);
		GlStateManager.scale(scale, scale, 1);
		GlStateManager.enableBlend();
		font().draw(s, 0, 0, color, shadow);
		GlStateManager.popMatrix();
	}

	public static void text(String s, float x, float y, int color, float scale) {
		text(s, x, y, color, scale, false);
	}

	public static void centered(String s, float cx, float y, int color, float scale) {
		text(s, cx - width(s) * scale / 2, y, color, scale, false);
	}

	public static int width(String s) {
		return font().getStringWidth(s);
	}

	public static boolean inside(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	/** Moves toward a target at a speed independent of frame rate. */
	public static float approach(float value, float target, float dt, float speed) {
		return value + (target - value) * (1 - (float) Math.exp(-speed * dt));
	}

	public static void fill(int x1, int y1, int x2, int y2, int argb) {
		DrawableHelper.fill(x1, y1, x2, y2, argb);
	}
}
