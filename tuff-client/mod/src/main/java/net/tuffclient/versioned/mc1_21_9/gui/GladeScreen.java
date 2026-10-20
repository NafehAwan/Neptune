// glade: minecraft >=1.21.9 <26.1
package net.tuffclient.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Base for Glade's screens: turns the game's render and input calls (which
 * differ between Minecraft versions) into Glade's own. Screens override
 * {@link #draw}, {@link #drawBackground} and the {@code on...} methods; the
 * default {@code on...} methods do what the game would.
 */
public abstract class GladeScreen extends Screen {
	private GuiGraphics graphics;
	private MouseButtonEvent mouseEvent;
	private KeyEvent keyEvent;
	private CharacterEvent charEvent;

	protected GladeScreen(Component title) {
		super(title);
	}

	protected void drawBackground(Gfx g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(graphics, mouseX, mouseY, partialTick);
	}

	protected abstract void draw(Gfx g, int mouseX, int mouseY, float partialTick);

	/** Blurs what has been drawn so far (the game's menu blur). */
	protected void blurBehind(Gfx g) {
		renderBlurredBackground(graphics);
	}

	protected boolean onClick(Input.Click click, boolean doubleClick) {
		return super.mouseClicked(mouseEvent, doubleClick);
	}

	protected boolean onDrag(Input.Click click, double dx, double dy) {
		return super.mouseDragged(mouseEvent, dx, dy);
	}

	protected boolean onRelease(Input.Click click) {
		return super.mouseReleased(mouseEvent);
	}

	protected boolean onScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	protected boolean onKey(Input.Key key) {
		return super.keyPressed(keyEvent);
	}

	/** Whether the key just pressed is bound to this key mapping. */
	protected boolean keyMatches(net.minecraft.client.KeyMapping mapping) {
		return keyEvent != null && mapping.matches(keyEvent);
	}

	protected boolean onType(Input.Typed typed) {
		return super.charTyped(charEvent);
	}

	// ---------------------------------------------------- the game's methods

	@Override
	public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.graphics = graphics;
		drawBackground(Gfx.of(graphics), mouseX, mouseY, partialTick);
	}

	@Override
	public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.graphics = graphics;
		draw(Gfx.of(graphics), mouseX, mouseY, partialTick);
	}

	private static Input.Click click(MouseButtonEvent e) {
		return new Input.Click(e.x(), e.y(), e.button());
	}

	@Override
	public final boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		mouseEvent = event;
		return onClick(click(event), doubleClick);
	}

	@Override
	public final boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		mouseEvent = event;
		return onDrag(click(event), dx, dy);
	}

	@Override
	public final boolean mouseReleased(MouseButtonEvent event) {
		mouseEvent = event;
		return onRelease(click(event));
	}

	@Override
	public final boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return onScroll(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public final boolean keyPressed(KeyEvent event) {
		keyEvent = event;
		return onKey(new Input.Key(event.key(), event.modifiers(), event.isEscape(), com.mojang.blaze3d.platform.InputConstants.getKey(event)));
	}

	@Override
	public final boolean charTyped(CharacterEvent event) {
		charEvent = event;
		return onType(new Input.Typed(event.codepoint(), event.isAllowedChatCharacter()));
	}
}
