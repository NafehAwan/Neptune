// glade: minecraft <1.21.2
package net.tuffclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base for Glade's screens: turns the game's render and input calls (which
 * differ between Minecraft versions) into Glade's own. Screens override
 * {@link #draw}, {@link #drawBackground} and the {@code on...} methods; the
 * default {@code on...} methods do what the game would.
 */
public abstract class GladeScreen extends Screen {
	private GuiGraphics graphics;
	private float partialTick;
	private int lastKey;
	private int scancode;
	private int charModifiers;

	protected GladeScreen(Component title) {
		super(title);
	}

	protected void drawBackground(Gfx g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(graphics, mouseX, mouseY, partialTick);
	}

	protected abstract void draw(Gfx g, int mouseX, int mouseY, float partialTick);

	/** Blurs what has been drawn so far (the game's menu blur). */
	protected void blurBehind(Gfx g) {
		renderBlurredBackground(partialTick);
	}

	protected boolean onClick(Input.Click click, boolean doubleClick) {
		return super.mouseClicked(click.x(), click.y(), click.button());
	}

	protected boolean onDrag(Input.Click click, double dx, double dy) {
		return super.mouseDragged(click.x(), click.y(), click.button(), dx, dy);
	}

	protected boolean onRelease(Input.Click click) {
		return super.mouseReleased(click.x(), click.y(), click.button());
	}

	protected boolean onScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	protected boolean onKey(Input.Key key) {
		return super.keyPressed(key.key(), scancode, key.modifiers());
	}

	/** Whether the key just pressed is bound to this key mapping. */
	protected boolean keyMatches(net.minecraft.client.KeyMapping mapping) {
		return mapping.matches(lastKey, scancode);
	}

	protected boolean onType(Input.Typed typed) {
		return super.charTyped((char) typed.codepoint(), charModifiers);
	}

	// ---------------------------------------------------- the game's methods

	@Override
	public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.graphics = graphics;
		this.partialTick = partialTick;
		drawBackground(Gfx.of(graphics), mouseX, mouseY, partialTick);
	}

	@Override
	public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.graphics = graphics;
		this.partialTick = partialTick;
		draw(Gfx.of(graphics), mouseX, mouseY, partialTick);
	}

	@Override
	public final boolean mouseClicked(double x, double y, int button) {
		return onClick(new Input.Click(x, y, button), false);
	}

	@Override
	public final boolean mouseDragged(double x, double y, int button, double dx, double dy) {
		return onDrag(new Input.Click(x, y, button), dx, dy);
	}

	@Override
	public final boolean mouseReleased(double x, double y, int button) {
		return onRelease(new Input.Click(x, y, button));
	}

	@Override
	public final boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return onScroll(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public final boolean keyPressed(int key, int scancode, int modifiers) {
		this.lastKey = key;
		this.scancode = scancode;
		return onKey(new Input.Key(key, modifiers, key == InputConstants.KEY_ESCAPE, InputConstants.getKey(key, scancode)));
	}

	@Override
	public final boolean charTyped(char c, int modifiers) {
		charModifiers = modifiers;
		return onType(new Input.Typed(c, c >= ' ' && c != 127 && c != '\u00a7'));
	}
}
