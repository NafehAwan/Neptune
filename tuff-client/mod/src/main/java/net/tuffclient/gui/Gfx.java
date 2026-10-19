package net.tuffclient.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;

/**
 * Everything Glade draws goes through here, so only this class changes between
 * Minecraft versions. Coordinates are GUI pixels; the transform stack moves,
 * scales and turns what comes after it.
 */
public final class Gfx {
	private static final Gfx SHARED = new Gfx();

	private GuiGraphicsExtractor g;

	private Gfx() {
	}

	/** Wraps the game's graphics object for this frame (drawing is single-threaded). */
	public static Gfx of(GuiGraphicsExtractor graphics) {
		SHARED.g = graphics;
		return SHARED;
	}

	/** The game's own graphics object, for version-specific code. */
	public GuiGraphicsExtractor raw() {
		return g;
	}

	// ------------------------------------------------------------- transform

	public void push() {
		g.pose().pushMatrix();
	}

	public void pop() {
		g.pose().popMatrix();
	}

	public void translate(float x, float y) {
		g.pose().translate(x, y);
	}

	public void scale(float x, float y) {
		g.pose().scale(x, y);
	}

	public void scale(float s) {
		scale(s, s);
	}

	/** Turns by an angle in radians. */
	public void rotate(float radians) {
		g.pose().rotate(radians);
	}

	/** Applies an affine 2D transform (used for skewed skin faces). */
	public void mul(Matrix3x2f m) {
		g.pose().mul(m);
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
		g.outline(x, y, w, h, argb);
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
		g.nextStratum();
	}

	// ------------------------------------------------------------------- text

	public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
		g.text(font, text, x, y, color, shadow);
	}

	public void text(Font font, String text, int x, int y, int color, boolean shadow) {
		g.text(font, text, x, y, color, shadow);
	}

	public void centeredText(Font font, Component text, int x, int y, int color) {
		g.centeredText(font, text, x, y, color);
	}

	public void centeredText(Font font, String text, int x, int y, int color) {
		g.centeredText(font, text, x, y, color);
	}

	// --------------------------------------------------------------- textures

	/** A GUI sprite (from textures/gui/sprites), stretched or nine-sliced as its .mcmeta says, tinted. */
	public void sprite(Identifier sprite, int x, int y, int w, int h, int argb) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h, argb);
	}

	public void sprite(Identifier sprite, int x, int y, int w, int h) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
	}

	/** Part of a texture: (u, v, uw, vh) of a texW x texH image, drawn at x, y, w x h, tinted. */
	public void texture(Identifier texture, int x, int y, float u, float v, int w, int h, int uw, int vh, int texW, int texH, int argb) {
		g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, uw, vh, texW, texH, argb);
	}

	public void item(ItemStack stack, int x, int y) {
		g.item(stack, x, y);
	}
}
