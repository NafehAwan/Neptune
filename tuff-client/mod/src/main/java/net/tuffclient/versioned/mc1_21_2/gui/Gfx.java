// glade: minecraft >=1.21.2 <1.21.6
package net.tuffclient.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

/**
 * Everything Glade draws goes through here, so only this class changes between
 * Minecraft versions. Coordinates are GUI pixels; the transform stack moves,
 * scales and turns what comes after it.
 */
public final class Gfx {
	private static final Gfx SHARED = new Gfx();
	private static final Identifier WHITE = Identifier.fromNamespaceAndPath("tuff", "textures/gui/white.png");

	private GuiGraphics g;

	private Gfx() {
	}

	/** Wraps the game's graphics object for this frame (drawing is single-threaded). */
	public static Gfx of(GuiGraphics graphics) {
		SHARED.g = graphics;
		return SHARED;
	}

	/** The game's own graphics object, for version-specific code. */
	public GuiGraphics raw() {
		return g;
	}

	// ------------------------------------------------------------- transform

	public void push() {
		g.pose().pushPose();
	}

	public void pop() {
		g.pose().popPose();
	}

	public void translate(float x, float y) {
		g.pose().translate(x, y, 0);
	}

	public void scale(float x, float y) {
		g.pose().scale(x, y, 1);
	}

	public void scale(float s) {
		scale(s, s);
	}

	/** Turns by an angle in radians. */
	public void rotate(float radians) {
		g.pose().mulPose(Axis.ZP.rotation(radians));
	}

	/** Applies an affine 2D transform (used for skewed skin faces). */
	public void mul(Matrix3x2f m) {
		g.pose().mulPose(new Matrix4f(m.m00(), m.m01(), 0, 0, m.m10(), m.m11(), 0, 0, 0, 0, 1, 0, m.m20(), m.m21(), 0, 1));
	}

	// ----------------------------------------------------------------- shapes

	public int guiWidth() {
		return g.guiWidth();
	}

	public int guiHeight() {
		return g.guiHeight();
	}

	public void fill(int x0, int y0, int x1, int y1, int argb) {
		g.fill(x0, y0, x1, y1, argb);
	}

	public void fillGradient(int x0, int y0, int x1, int y1, int top, int bottom) {
		g.fillGradient(x0, y0, x1, y1, top, bottom);
	}

	public void outline(int x, int y, int w, int h, int argb) {
		fill(x, y, x + w, y + 1, argb);
		fill(x, y + h - 1, x + w, y + h, argb);
		fill(x, y + 1, x + 1, y + h - 1, argb);
		fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
	}

	/** Only draw inside this rectangle until {@link #disableScissor}. */
	public void enableScissor(int x0, int y0, int x1, int y1) {
		g.enableScissor(x0, y0, x1, y1);
	}

	public void disableScissor() {
		g.disableScissor();
	}

	/** Starts a new layer: later shapes draw over earlier text and textures. */
	public void nextStratum() {
		g.flush();
	}

	// ------------------------------------------------------------------- text

	public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
		g.drawString(font, text, x, y, color, shadow);
	}

	public void text(Font font, String text, int x, int y, int color, boolean shadow) {
		g.drawString(font, text, x, y, color, shadow);
	}

	public void centeredText(Font font, Component text, int x, int y, int color) {
		g.drawCenteredString(font, text, x, y, color);
	}

	public void centeredText(Font font, String text, int x, int y, int color) {
		g.drawCenteredString(font, text, x, y, color);
	}

	// --------------------------------------------------------------- textures

	/** A GUI sprite (from textures/gui/sprites), stretched or nine-sliced as its .mcmeta says, tinted. */
	public void sprite(Identifier sprite, int x, int y, int w, int h, int argb) {
		g.blitSprite(RenderType::guiTextured, sprite, x, y, w, h, argb);
	}

	public void sprite(Identifier sprite, int x, int y, int w, int h) {
		g.blitSprite(RenderType::guiTextured, sprite, x, y, w, h);
	}

	/** Part of a texture: (u, v, uw, vh) of a texW x texH image, drawn at x, y, w x h, tinted. */
	public void texture(Identifier texture, int x, int y, float u, float v, int w, int h, int uw, int vh, int texW, int texH, int argb) {
		g.blit(RenderType::guiTextured, texture, x, y, u, v, w, h, uw, vh, texW, texH, argb);
	}

	public void item(ItemStack stack, int x, int y) {
		g.renderItem(stack, x, y);
	}

	// ------------------------------------------------------------- crosshair

	/**
	 * A rectangle drawn with the vanilla crosshair's inverting blend. Strength
	 * 1 flips the colour behind it fully; less mixes the flip with what's
	 * there (drawing grey instead of white does exactly that with this blend).
	 */
	public void invertRect(int x0, int y0, int x1, int y1, float strength) {
		if (x1 <= x0 || y1 <= y0) return;
		g.blit(net.minecraft.client.renderer.RenderType::crosshair, WHITE, x0, y0, 0, 0, x1 - x0, y1 - y0, x1 - x0, y1 - y0, grey(strength));
	}

	/** A GUI sprite drawn with the vanilla crosshair's inverting blend, at a strength. */
	public void invertSprite(Identifier sprite, int x, int y, int w, int h, float strength) {
		g.blitSprite(net.minecraft.client.renderer.RenderType::crosshair, sprite, x, y, w, h, grey(strength));
	}

	private static int grey(float strength) {
		int v = Math.round(Math.max(0, Math.min(1, strength)) * 255);
		return 0xFF000000 | v << 16 | v << 8 | v;
	}
}
