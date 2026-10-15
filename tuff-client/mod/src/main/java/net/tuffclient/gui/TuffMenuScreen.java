package net.tuffclient.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.tuffclient.TuffClient;
import net.tuffclient.TuffConfig;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ChoiceSetting;
import net.tuffclient.setting.NumberSetting;
import net.tuffclient.setting.Setting;

/** The Tuff mod menu: searchable module grid with per-module settings. */
public final class TuffMenuScreen extends Screen {
	private record Hit(int x1, int y1, int x2, int y2, Consumer<MouseButtonEvent> action) {
	}

	private static final int CARD_H = 46;
	private static final int GAP = 6;

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private final java.util.Map<Module, Float> toggleAnim = new java.util.HashMap<>();
	private Category tab = null;
	private String query = "";
	private boolean searchFocused;
	private Module open;
	private double scroll;
	private double maxScroll;
	private NumberSetting dragging;
	private int dragX1;
	private int dragX2;
	private float appear;
	private long last = System.nanoTime();

	public TuffMenuScreen(Screen parent) {
		super(Component.literal("Tuff Mods"));
		this.parent = parent;
	}

	public TuffMenuScreen(Screen parent, Category tab) {
		this(parent);
		this.tab = tab;
	}

	private int panelW() {
		return Math.min(width - 24, 440);
	}

	private int panelH() {
		return Math.min(height - 24, 300);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, minecraft.player == null ? Theme.BG : Theme.SCRIM);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		appear = Theme.approach(appear, 1, dt, 12);
		hits.clear();

		int pw = panelW(), ph = panelH();
		int px = (width - pw) / 2;
		int py = (height - ph) / 2 + Math.round((1 - appear) * 8);
		Theme.panel(g, px, py, px + pw, py + ph, Theme.BG, Theme.BORDER_STRONG);

		// header
		g.pose().pushMatrix();
		g.pose().translate(px + 12, py + 10);
		g.pose().scale(2);
		g.text(font, "TUFF", 1, 1, Theme.COPPER_LO, false);
		g.text(font, "TUFF", 0, 0, Theme.TEXT, false);
		g.pose().popMatrix();

		int editW = 64;
		int ex = px + pw - editW - 10, ey = py + 9;
		boolean editHover = Theme.inside(mouseX, mouseY, ex, ey, ex + editW, ey + 16);
		Theme.pixelButton(g, ex, ey, ex + editW, ey + 16, editHover ? 1 : 0);
		Theme.centered(g, font, "EDIT HUD", ex + editW / 2, ey + 4, Theme.COPPER_INK);
		hits.add(new Hit(ex, ey, ex + editW, ey + 16, e -> {
			click();
			minecraft.gui.setScreen(new HudEditorScreen(this));
		}));

		int contentTop = py + 34;
		if (open != null) {
			settings(g, mouseX, mouseY, px, contentTop, pw, py + ph);
			return;
		}

		// tabs
		int tx = px + 10;
		tx = tab(g, mouseX, mouseY, tx, contentTop, "All", null);
		for (Category c : Category.values()) tx = tab(g, mouseX, mouseY, tx, contentTop, c.label, c);

		// search box
		int sw = Math.min(140, pw - (tx - px) - 20);
		int sx = px + pw - sw - 10;
		Theme.panel(g, sx, contentTop, sx + sw, contentTop + 16, Theme.SURFACE, searchFocused ? Theme.COPPER : Theme.BORDER_STRONG);
		boolean blink = searchFocused && (now / 500_000_000L) % 2 == 0;
		String shown = query.isEmpty() && !searchFocused ? "Search modules" : query + (blink ? "_" : "");
		g.text(font, font.plainSubstrByWidth(shown, sw - 8, true), sx + 5, contentTop + 4, query.isEmpty() && !searchFocused ? Theme.FAINT : Theme.TEXT);
		hits.add(new Hit(sx, contentTop, sx + sw, contentTop + 16, e -> searchFocused = true));

		// grid
		int gridTop = contentTop + 24, gridBottom = py + ph - 8;
		int cols = Math.max(1, (pw - 20 + GAP) / (130 + GAP));
		int cardW = (pw - 20 - GAP * (cols - 1)) / cols;
		List<Module> shownModules = filtered();
		int rows = (shownModules.size() + cols - 1) / cols;
		maxScroll = Math.max(0, rows * (CARD_H + GAP) - GAP - (gridBottom - gridTop));
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		g.enableScissor(px + 1, gridTop, px + pw - 1, gridBottom);
		for (int i = 0; i < shownModules.size(); i++) {
			Module m = shownModules.get(i);
			int cx = px + 10 + (i % cols) * (cardW + GAP);
			int cy = gridTop + (i / cols) * (CARD_H + GAP) - (int) scroll;
			if (cy + CARD_H < gridTop || cy > gridBottom) continue;
			card(g, mouseX, mouseY, m, cx, cy, cardW, dt, gridTop, gridBottom);
		}
		g.disableScissor();
		if (shownModules.isEmpty()) Theme.centered(g, font, "No modules match \"" + query + "\"", px + pw / 2, gridTop + 30, Theme.MUTED);
		if (maxScroll > 0) {
			int trackH = gridBottom - gridTop;
			int thumbH = Math.max(16, (int) (trackH * trackH / (trackH + maxScroll)));
			int thumbY = gridTop + (int) ((trackH - thumbH) * (scroll / maxScroll));
			g.fill(px + pw - 5, thumbY, px + pw - 3, thumbY + thumbH, Theme.SURFACE_3);
		}
	}

	private int tab(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, String label, Category c) {
		int w = font.width(label) + 14;
		boolean on = tab == c;
		boolean over = Theme.inside(mouseX, mouseY, x, y, x + w, y + 16);
		if (on) g.fill(x, y, x + w, y + 16, Theme.SURFACE_3);
		else if (over) g.fill(x, y, x + w, y + 16, Theme.SURFACE);
		g.text(font, label, x + 7, y + 4, on ? Theme.TEXT : over ? Theme.TEXT : Theme.MUTED);
		if (on) g.fill(x + 4, y + 15, x + w - 4, y + 16, Theme.COPPER);
		hits.add(new Hit(x, y, x + w, y + 16, e -> {
			tab = c;
			scroll = 0;
		}));
		return x + w + 2;
	}

	private void card(GuiGraphicsExtractor g, int mouseX, int mouseY, Module m, int x, int y, int w, float dt, int clipTop, int clipBottom) {
		boolean over = Theme.inside(mouseX, mouseY, x, y, x + w, y + CARD_H) && mouseY >= clipTop && mouseY < clipBottom;
		int border = m.isEnabled() ? Theme.mix(Theme.BORDER_STRONG, Theme.COPPER, 0.55f) : over ? 0xFF4A504B : Theme.BORDER;
		Theme.panel(g, x, y, x + w, y + CARD_H, over ? Theme.SURFACE_2 : Theme.SURFACE, border);
		g.text(font, font.plainSubstrByWidth(m.name, w - 40), x + 7, y + 7, Theme.TEXT);
		String chip = m.category.label.toUpperCase();
		g.text(font, chip, x + 7, y + CARD_H - 13, Theme.FAINT);
		String desc = m.description;
		if (font.width(desc) > w - 14) desc = font.plainSubstrByWidth(desc, w - 14 - font.width("...")).stripTrailing() + "...";
		g.text(font, desc, x + 7, y + 19, Theme.MUTED);
		float anim = Theme.approach(toggleAnim.getOrDefault(m, m.isEnabled() ? 1f : 0f), m.isEnabled() ? 1 : 0, dt, 16);
		toggleAnim.put(m, anim);
		int tx = x + w - 25, ty = y + 7;
		Theme.toggle(g, tx, ty, m.isEnabled(), anim);
		if (!m.settings().isEmpty()) {
			int gx = x + w - 20, gy = y + CARD_H - 15;
			boolean gearOver = Theme.inside(mouseX, mouseY, gx - 2, gy - 2, gx + 14, gy + 12);
			g.text(font, "···", gx, gy, gearOver ? Theme.COPPER_HI : Theme.MUTED);
			if (mouseY >= clipTop && mouseY < clipBottom) {
				hits.add(new Hit(gx - 4, gy - 4, gx + 16, gy + 12, e -> {
					click();
					open = m;
				}));
			}
		}
		if (mouseY >= clipTop && mouseY < clipBottom) {
			hits.add(new Hit(x, y, x + w, y + CARD_H, e -> {
				if (e.button() == 1 && !m.settings().isEmpty()) {
					open = m;
				} else {
					m.toggle();
				}
				click();
			}));
		}
	}

	private void settings(GuiGraphicsExtractor g, int mouseX, int mouseY, int px, int top, int pw, int bottom) {
		Module m = open;
		boolean backOver = Theme.inside(mouseX, mouseY, px + 10, top, px + 60, top + 16);
		g.text(font, "< Back", px + 10, top + 4, backOver ? Theme.COPPER_HI : Theme.MUTED);
		hits.add(new Hit(px + 8, top, px + 60, top + 16, e -> {
			click();
			open = null;
		}));
		g.text(font, m.name, px + 70, top + 4, Theme.TEXT);
		int tx = px + pw - 30;
		Theme.toggle(g, tx, top + 3, m.isEnabled(), m.isEnabled() ? 1 : 0);
		hits.add(new Hit(tx, top, tx + 18, top + 16, e -> {
			click();
			m.toggle();
		}));
		int y = top + 26;
		for (Setting<?> s : m.settings()) {
			if (y + 18 > bottom) break;
			g.fill(px + 10, y + 19, px + pw - 10, y + 20, Theme.BORDER);
			g.text(font, s.name, px + 12, y + 5, Theme.TEXT);
			int cx2 = px + pw - 12;
			switch (s) {
				case BoolSetting b -> {
					Theme.toggle(g, cx2 - 18, y + 4, b.get(), b.get() ? 1 : 0);
					hits.add(new Hit(cx2 - 18, y, cx2, y + 18, e -> {
						click();
						b.toggle();
					}));
				}
				case ChoiceSetting c -> {
					String label = "< " + c.choice() + " >";
					int lw = font.width(label);
					boolean over = Theme.inside(mouseX, mouseY, cx2 - lw - 4, y, cx2, y + 18);
					g.text(font, label, cx2 - lw, y + 5, over ? Theme.COPPER_HI : Theme.TEXT);
					hits.add(new Hit(cx2 - lw - 4, y, cx2, y + 18, e -> {
						click();
						c.cycle(e.button() == 1 ? -1 : 1);
					}));
				}
				case NumberSetting n -> {
					int sw = 110, sx = cx2 - sw - 34;
					g.fill(sx, y + 8, sx + sw, y + 10, Theme.SURFACE_3);
					int knob = sx + (int) Math.round(n.fraction() * sw);
					g.fill(sx, y + 8, knob, y + 10, Theme.COPPER_LO);
					g.fill(knob - 2, y + 4, knob + 2, y + 14, dragging == n ? Theme.COPPER_HI : Theme.COPPER);
					String v = n.display();
					g.text(font, v, cx2 - font.width(v), y + 5, Theme.TEXT);
					hits.add(new Hit(sx - 3, y, sx + sw + 3, y + 18, e -> {
						dragging = n;
						dragX1 = sx;
						dragX2 = sx + sw;
						updateSlider(e.x());
					}));
				}
			}
			y += 22;
		}
	}

	private void updateSlider(double mouseX) {
		if (dragging == null) return;
		double t = Math.max(0, Math.min(1, (mouseX - dragX1) / (double) (dragX2 - dragX1)));
		dragging.set(dragging.min + t * (dragging.max - dragging.min));
	}

	private List<Module> filtered() {
		String q = query.toLowerCase();
		List<Module> out = new ArrayList<>();
		for (Module m : TuffClient.modules().all()) {
			if (tab != null && m.category != tab) continue;
			if (!q.isEmpty() && !m.name.toLowerCase().contains(q) && !m.description.toLowerCase().contains(q)) continue;
			out.add(m);
		}
		return out;
	}

	private void click() {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		searchFocused = false;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (Theme.inside(event.x(), event.y(), h.x1(), h.y1(), h.x2(), h.y2())) {
				h.action().accept(event);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging != null) {
			updateSlider(event.x());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = Math.max(0, Math.min(maxScroll, scroll - scrollY * 20));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (searchFocused) {
			if (event.key() == InputConstants.KEY_BACKSPACE && !query.isEmpty()) {
				query = query.substring(0, query.length() - 1);
				return true;
			}
			if (event.isEscape()) {
				searchFocused = false;
				return true;
			}
		} else if (event.isEscape() && open != null) {
			open = null;
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (searchFocused && event.isAllowedChatCharacter() && query.length() < 32) {
			query += event.codepointAsString();
			scroll = 0;
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public void onClose() {
		TuffConfig.save(TuffClient.modules());
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
