package net.tuffclient.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

/**
 * Base for Glade's screens, drawn the way Minecraft's own options screens are:
 * the game's button, slider and text field textures, its font, the list
 * background between header and footer separators, and its click sound.
 * Screens lay themselves out every frame in {@link #layout}; controls added
 * there are clickable until the next frame.
 */
public abstract class VanillaScreen extends GladeScreen {
	protected static final int HEADER = 33;
	protected static final int FOOTER = 33;
	protected static final int ROW = 24;

	private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
	private static final Identifier BUTTON_HOVER = Identifier.withDefaultNamespace("widget/button_highlighted");
	private static final Identifier BUTTON_OFF = Identifier.withDefaultNamespace("widget/button_disabled");
	private static final Identifier SLIDER = Identifier.withDefaultNamespace("widget/slider");
	private static final Identifier SLIDER_HOVER = Identifier.withDefaultNamespace("widget/slider_highlighted");
	private static final Identifier HANDLE = Identifier.withDefaultNamespace("widget/slider_handle");
	private static final Identifier HANDLE_HOVER = Identifier.withDefaultNamespace("widget/slider_handle_highlighted");
	private static final Identifier FIELD = Identifier.withDefaultNamespace("widget/text_field");
	private static final Identifier FIELD_FOCUS = Identifier.withDefaultNamespace("widget/text_field_highlighted");
	private static final Identifier SCROLLER = Identifier.withDefaultNamespace("widget/scroller");
	private static final Identifier SCROLLER_BG = Identifier.withDefaultNamespace("widget/scroller_background");
	private static final Identifier LIST_BG = Identifier.withDefaultNamespace("textures/gui/menu_list_background.png");
	private static final Identifier LIST_BG_INWORLD = Identifier.withDefaultNamespace("textures/gui/inworld_menu_list_background.png");

	protected static final int WHITE = 0xFFFFFFFF;
	protected static final int GREY = 0xFFA0A0A0;

	/** Something clickable this frame. */
	protected record Hit(int x0, int y0, int x1, int y1, Click action) {
	}

	@FunctionalInterface
	protected interface Click {
		void run(Input.Click click);
	}

	@FunctionalInterface
	protected interface Drag {
		void to(double x, double y);
	}

	protected final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private Drag drag;
	private int mouseX;
	private int mouseY;
	/** How far the list is scrolled, and how tall its content was last frame. */
	protected double scroll;
	private int contentHeight;
	private int listTop;
	private int listBottom;
	private boolean draggingScrollbar;

	protected VanillaScreen(Component title, Screen parent) {
		super(title);
		this.parent = parent;
	}

	/** Draws the screen's controls and registers what they do; called every frame. */
	protected abstract void layout(Gfx g);

	@Override
	protected void draw(Gfx g, int mouseX, int mouseY, float partialTick) {
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		hits.clear();
		layout(g);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	// ------------------------------------------------------------- layout

	protected int centerX() {
		return width / 2;
	}

	protected boolean inWorld() {
		return minecraft.level != null;
	}

	/** The title, centred in the header, like the game's options screens. */
	protected void title(Gfx g) {
		g.centeredText(font, title, width / 2, 15 - font.lineHeight / 2 + 1, WHITE);
	}

	/**
	 * Starts the scrolling list between header and footer: its background and
	 * separators. Returns the y of the first row (scrolled); draw rows from there
	 * and pass the y after the last one to {@link #endList}.
	 */
	protected int beginList(Gfx g) {
		listTop = HEADER;
		listBottom = height - FOOTER;
		var raw = g.raw();
		Screen.renderMenuBackgroundTexture(raw, inWorld() ? LIST_BG_INWORLD : LIST_BG, 0, listTop, 0, 0, width, listBottom - listTop);
		g.texture(inWorld() ? Screen.INWORLD_HEADER_SEPARATOR : Screen.HEADER_SEPARATOR, 0, listTop - 2, 0, 0, width, 2, width, 2, 32, 2, WHITE);
		g.texture(inWorld() ? Screen.INWORLD_FOOTER_SEPARATOR : Screen.FOOTER_SEPARATOR, 0, listBottom, 0, 0, width, 2, width, 2, 32, 2, WHITE);
		clampScroll();
		g.enableScissor(0, listTop, width, listBottom);
		return listTop + 4 - (int) scroll;
	}

	protected void endList(Gfx g, int endY) {
		g.disableScissor();
		contentHeight = endY + (int) scroll - listTop + 4;
		int max = maxScroll();
		if (max > 0) {
			int x = width / 2 + 158;
			int track = listBottom - listTop;
			int knob = Math.max(32, track * track / contentHeight);
			int ky = listTop + (int) ((track - knob) * scroll / max);
			g.sprite(SCROLLER_BG, x, listTop, 6, track);
			g.sprite(SCROLLER, x, ky, 6, knob);
			hits.add(new Hit(x, listTop, x + 6, listBottom, c -> {
				draggingScrollbar = true;
				scrollTo(c.y());
			}));
		}
	}

	private int maxScroll() {
		return Math.max(0, contentHeight - (listBottom - listTop));
	}

	private void clampScroll() {
		scroll = Math.max(0, Math.min(maxScroll(), scroll));
	}

	private void scrollTo(double y) {
		int track = listBottom - listTop;
		int knob = Math.max(32, track * track / Math.max(1, contentHeight));
		scroll = (y - listTop - knob / 2.0) / Math.max(1, track - knob) * maxScroll();
		clampScroll();
	}

	/** Whether a row at y is (partly) inside the visible list, so it can be clicked. */
	private boolean listed(int y, int h) {
		return listBottom == 0 || y + h > listTop && y < listBottom;
	}

	/** Controls inside the list only react inside it; outside (header, footer) everything does. */
	private boolean clickable(int y, int h, boolean inList) {
		return !inList || listed(y, h);
	}

	protected boolean hovered(int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private boolean hovered(int x, int y, int w, int h, boolean inList) {
		return hovered(x, y, w, h) && (!inList || mouseY >= listTop && mouseY < listBottom);
	}

	protected void onClickAt(int x, int y, int w, int h, boolean inList, Click action) {
		if (clickable(y, h, inList)) hits.add(new Hit(x, Math.max(y, inList ? listTop : y), x + w, Math.min(y + h, inList ? listBottom : y + h), action));
	}

	protected void playClick() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	// ------------------------------------------------------------ controls

	/** A game button. */
	protected void button(Gfx g, int x, int y, int w, int h, Component label, boolean active, boolean inList, Click onClick) {
		boolean over = active && hovered(x, y, w, h, inList);
		g.sprite(!active ? BUTTON_OFF : over ? BUTTON_HOVER : BUTTON, x, y, w, h);
		label(g, label, x, y, w, h, active ? WHITE : GREY);
		if (active) onClickAt(x, y, w, h, inList, c -> {
			playClick();
			onClick.run(c);
		});
	}

	protected void button(Gfx g, int x, int y, int w, Component label, boolean inList, Runnable onClick) {
		button(g, x, y, w, 20, label, true, inList, c -> onClick.run());
	}

	/** A game slider showing {@code label}; dragging gives a fraction from 0 to 1. */
	protected void slider(Gfx g, int x, int y, int w, Component label, double fraction, boolean inList, DoubleConsumer onChange) {
		boolean over = hovered(x, y, w, 20, inList);
		g.sprite(over ? SLIDER_HOVER : SLIDER, x, y, w, 20);
		int hx = x + (int) Math.round(Math.max(0, Math.min(1, fraction)) * (w - 8));
		g.sprite(over ? HANDLE_HOVER : HANDLE, hx, y, 8, 20);
		label(g, label, x, y, w, 20, WHITE);
		onClickAt(x, y, w, 20, inList, c -> {
			Drag set = (mx, my) -> onChange.accept(Math.max(0, Math.min(1, (mx - (x + 4)) / (double) (w - 8))));
			set.to(c.x(), c.y());
			drag = set;
		});
	}

	/** A game text field showing {@code text}; a blinking cursor when focused. */
	protected void textField(Gfx g, int x, int y, int w, String text, boolean focused, boolean inList, Runnable onFocus) {
		g.sprite(focused ? FIELD_FOCUS : FIELD, x, y, w, 20);
		String shown = font.plainSubstrByWidth(text, w - 8, true);
		boolean blink = focused && (System.currentTimeMillis() / 500) % 2 == 0;
		g.text(font, shown + (blink ? "_" : ""), x + 4, y + 6, 0xFFE0E0E0, true);
		onClickAt(x, y, w, 20, inList, c -> onFocus.run());
	}

	/** Centred text that fits the width (cut with "..." when too long). */
	protected void label(Gfx g, Component text, int x, int y, int w, int h, int color) {
		Component shown = text;
		int max = w - 8;
		if (font.width(text) > max) {
			String s = font.plainSubstrByWidth(text.getString(), max - font.width("...")) + "...";
			shown = Component.literal(s).withStyle(text.getStyle());
		}
		g.centeredText(font, shown, x + w / 2, y + (h - 8) / 2, color);
	}

	/** A section heading inside the list, like the game's key binding categories. */
	protected int heading(Gfx g, String text, int y) {
		g.centeredText(font, Component.literal(text).withStyle(net.minecraft.ChatFormatting.BOLD), width / 2, y + 6, WHITE);
		return y + 20;
	}

	/** A wrapped grey note inside the list. */
	protected int note(Gfx g, String text, int x, int y, int w) {
		for (FormattedCharSequence line : font.split(Component.literal(text), w)) {
			g.raw().drawString(font, line, x, y, GREY, true);
			y += 10;
		}
		return y + 2;
	}

	/** A tooltip (wrapped) at the mouse, drawn after the screen. */
	protected void tooltip(Gfx g, String text) {
		g.raw().setTooltipForNextFrame(font, font.split(Component.literal(text), 200), mouseX, mouseY);
	}

	protected static Component onOff(String name, boolean on) {
		return Component.literal(name + ": ").append(Component.translatable(on ? "options.on" : "options.off"));
	}

	// --------------------------------------------------------------- input

	@Override
	protected boolean onClick(Input.Click click, boolean doubleClick) {
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (click.x() >= h.x0() && click.x() < h.x1() && click.y() >= h.y0() && click.y() < h.y1()) {
				h.action().run(click);
				return true;
			}
		}
		clickedNothing();
		return super.onClick(click, doubleClick);
	}

	/** A click that hit no control (to drop text focus, for example). */
	protected void clickedNothing() {
	}

	@Override
	protected boolean onDrag(Input.Click click, double dx, double dy) {
		if (draggingScrollbar) {
			scrollTo(click.y());
			return true;
		}
		if (drag != null) {
			drag.to(click.x(), click.y());
			return true;
		}
		return super.onDrag(click, dx, dy);
	}

	@Override
	protected boolean onRelease(Input.Click click) {
		boolean was = drag != null || draggingScrollbar;
		drag = null;
		draggingScrollbar = false;
		return was || super.onRelease(click);
	}

	/** Lets a screen drag something of its own (crosshair drawing, HUD elements). */
	protected void startDrag(Drag onMove) {
		drag = onMove;
	}

	@Override
	protected boolean onScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= scrollY * 20;
		clampScroll();
		return true;
	}
}
