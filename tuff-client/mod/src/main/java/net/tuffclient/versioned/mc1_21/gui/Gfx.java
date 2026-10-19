// glade: minecraft <1.21.2
package net.tuffclient.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.math.Axis;
import com.mojang.blaze3d.systems.RenderSystem;
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
		tint(argb);
		g.blitSprite(sprite, x, y, w, h);
		g.setColor(1, 1, 1, 1);
	}

	public void sprite(Identifier sprite, int x, int y, int w, int h) {
		RenderSystem.enableBlend();
		g.blitSprite(sprite, x, y, w, h);
	}

	/** Part of a texture: (u, v, uw, vh) of a texW x texH image, drawn at x, y, w x h, tinted. */
	public void texture(Identifier texture, int x, int y, float u, float v, int w, int h, int uw, int vh, int texW, int texH, int argb) {
		tint(argb);
		g.blit(texture, x, y, w, h, u, v, uw, vh, texW, texH);
		g.setColor(1, 1, 1, 1);
	}

	private void tint(int argb) {
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		g.setColor((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
	}

	public void item(ItemStack stack, int x, int y) {
		g.renderItem(stack, x, y);
	}
}
