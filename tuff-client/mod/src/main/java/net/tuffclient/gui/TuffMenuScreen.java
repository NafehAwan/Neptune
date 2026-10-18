package net.tuffclient.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.sounds.SoundEvents;
import net.tuffclient.ModuleManager;
import net.tuffclient.TuffClient;
import net.tuffclient.TuffConfig;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.module.hud.HudModule;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ChoiceSetting;
import net.tuffclient.setting.NumberSetting;
import net.tuffclient.setting.Setting;

/** The Glade mod menu: a top bar with Mods, Theme, Packs and Settings pages and a grid of mod cards. */
public final class TuffMenuScreen extends Screen {
	public enum Page {
		MODS("Mods", "grid"), THEME("Theme", "palette"), PACKS("Packs", "package"), SETTINGS("Settings", "settings");

		final String label;
		final String icon;

		Page(String label, String icon) {
			this.label = label;
			this.icon = icon;
		}
	}

	private enum Tab {
		ALL("All"), NEW("New"), FAVORITES("Favorites"), HUD("HUD"), UTILITY("Utility"), VISUAL("Visual");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private record Hit(float x1, float y1, float x2, float y2, Consumer<MouseButtonEvent> action) {
	}

	private static final Identifier EMBLEM = Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "textures/gui/title/emblem.png");
	/** Lucide sprite per module (see art/icons.mjs). */
	private static final Map<String, String> ICONS = Map.ofEntries(
			Map.entry("fps", "gauge"), Map.entry("cps", "mouse"), Map.entry("keystrokes", "keyboard"),
			Map.entry("armor", "shirt"), Map.entry("coordinates", "pin"), Map.entry("ping", "signal"),
			Map.entry("effects", "potion"), Map.entry("clock", "clock"), Map.entry("server", "globe"),
			Map.entry("speed", "bolt"), Map.entry("memory", "chip"), Map.entry("zoom", "zoom"),
			Map.entry("fullbright", "sun"), Map.entry("toggle_sprint", "sprint"), Map.entry("crosshair", "crosshair"),
			Map.entry("hide_scoreboard", "scoreboard"), Map.entry("title_screen", "image"));
	/** Modules that get a NEW badge in this version. */
	private static final Set<String> NEW_MODS = Set.of("title_screen", "crosshair", "zoom");
	/** Width of the left sidebar. */
	private static final int SIDE = 42;
	/** The menu is laid out in a space about this many units tall, whatever the GUI scale. */
	private static final float VIRTUAL_HEIGHT = 340;
	private static final int GAP = 6;

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private final Map<String, Float> anim = new HashMap<>();
	private final GroveBackground background = new GroveBackground();
	private Page page;
	private Tab tab = Tab.ALL;
	private String query = "";
	private float scroll;
	private float scrollTarget;
	private float maxScroll;
	private Module drawer;
	private float drawerAnim;
	private float drawerScroll;
	private NumberSetting dragging;
	private float dragX1;
	private float dragX2;
	private float open;
	private float pageFade = 1;
	private float dt;
	private long last = System.nanoTime();
	private List<String> enabledPacks;
	private List<String> availablePacks;
	private boolean packsDirty;
	private double clickX;
	private Module hovered;
	private final Map<String, float[]> switches = new HashMap<>();
	private final Map<String, float[]> hearts = new HashMap<>();
	private boolean listView;
	private String tooltip;
	private float hoverTime;
	private float drawerLeft;

	public TuffMenuScreen(Screen parent) {
		this(parent, Page.MODS);
	}

	public TuffMenuScreen(Screen parent, Page page) {
		super(Component.literal("Glade"));
		this.parent = parent;
		this.page = page;
	}

	public void setPage(Page page) {
		if (this.page == page) return;
		this.page = page;
		drawer = null;
		scroll = scrollTarget = 0;
		pageFade = 0;
		if (page == Page.PACKS) loadPacks();
	}

	/** Window-pixel centre of a module card's switch as last drawn, or null (used by tests). */
	public double[] switchPosition(String moduleId) {
		return toWindow(switches.get(moduleId));
	}

	/** Window-pixel centre of a module card's heart as last drawn, or null (used by tests). */
	public double[] heartPosition(String moduleId) {
		return toWindow(hearts.get(moduleId));
	}

	private double[] toWindow(float[] p) {
		if (p == null) return null;
		double k = zoom() * minecraft.getWindow().getGuiScale();
		return new double[] {p[0] * k, p[1] * k};
	}

	public void setListView(boolean list) {
		listView = list;
		scroll = scrollTarget = 0;
	}

	public void openSettings(Module module) {
		drawer = module;
		drawerScroll = 0;
	}

	private ModuleManager modules() {
		return TuffClient.modules();
	}

	private boolean animations() {
		return modules().ui.animations.get();
	}

	private float anim(String key, boolean target, float speed) {
		float v = anim.getOrDefault(key, target ? 1f : 0f);
		v = animations() ? Theme.approach(v, target ? 1 : 0, dt, speed) : (target ? 1 : 0);
		anim.put(key, v);
		return v;
	}

	private void click() {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	private void hit(float x1, float y1, float x2, float y2, Consumer<MouseButtonEvent> action) {
		hits.add(new Hit(x1, y1, x2, y2, action));
	}

	// ------------------------------------------------------------------ layout

	/**
	 * GUI units per menu unit. Large GUI scales (the default on 1080p and up) would make the
	 * menu huge and cramped, so it is drawn at a steady physical size instead, never larger
	 * than vanilla would draw it.
	 */
	private float zoom() {
		int gs = Ui.guiScale();
		// Small windows keep at least 1.5 physical pixels per unit so text stays readable.
		float unit = Math.min(gs, Math.max(1.5f, height * gs / VIRTUAL_HEIGHT));
		return unit / gs;
	}

	private float vw() {
		return width / zoom();
	}

	private float vh() {
		return height / zoom();
	}

	/** Sidebar plus panel width. */
	private float totalW() {
		return Math.max(440, Math.min(720, vw() * 0.8f));
	}

	private float sideX() {
		return (vw() - totalW()) / 2;
	}

	private float panelX() {
		return sideX() + SIDE + 6;
	}

	private float panelW() {
		return totalW() - SIDE - 6;
	}

	private float panelH() {
		return Math.max(210, Math.min(390, vh() * 0.7f));
	}

	private float panelY() {
		return (vh() - panelH()) / 2 + 12;
	}

	/** Bounds of everything the menu draws (sidebar, toolbar, panel); clicks outside close it. */
	private float wx() {
		return sideX();
	}

	private float wy() {
		return panelY() - 24;
	}

	private float ww() {
		return totalW();
	}

	private float wh() {
		return panelH() + 24;
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level == null) {
			background.render(g, width, height, TuffClient.modules().titleScreen.animate.get());
			g.fill(0, 0, width, height, 0x59F6F4EE);
		} else if (modules().ui.blur.get()) {
			extractBlurredBackground(g);
			g.fill(0, 0, width, height, 0x2EF6F4EE);
		} else {
			g.fill(0, 0, width, height, 0x73E9EEE7);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int guiMouseX, int guiMouseY, float partialTick) {
		float z = zoom();
		int mouseX = Math.round(guiMouseX / z), mouseY = Math.round(guiMouseY / z);
		long now = System.nanoTime();
		dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		open = animations() ? Theme.approach(open, 1, dt, 12) : 1;
		pageFade = animations() ? Theme.approach(pageFade, 1, dt, 14) : 1;
		scroll = animations() ? Theme.approach(scroll, scrollTarget, dt, 18) : scrollTarget;
		drawerAnim = Theme.approach(drawerAnim, drawer != null ? 1 : 0, dt, animations() ? 16 : 1000);
		hits.clear();
		Module wasHovered = hovered;
		hovered = null;
		tooltip = null;

		float s = 0.96f + 0.04f * open;
		Ui.zoom = z;
		g.pose().pushMatrix();
		g.pose().scale(z, z);
		g.pose().translate(vw() / 2f, vh() / 2f);
		g.pose().scale(s, s);
		g.pose().translate(-vw() / 2f, -vh() / 2f);
		float a = open;

		float px = panelX(), py = panelY(), pw = panelW(), ph = panelH();
		sidebar(g, mouseX, mouseY, sideX(), py, ph, a);
		toolbar(g, mouseX, mouseY, px + pw, py - 22, a);

		Ui.shadow(g, px, py, pw, ph, 18, 0.2f * a);
		Ui.round(g, px, py, pw, ph, 8, Theme.alpha(Theme.PANEL, a), Theme.alpha(Theme.GLASS_RIM, a), 1);

		float pa = a * pageFade;
		float slide = (1 - pageFade) * 6;
		switch (page) {
			case MODS -> mods(g, mouseX, mouseY, px, py + slide, pw, ph, pa);
			case THEME -> theme(g, mouseX, mouseY, px - 8, py - 22 + slide, pw + 16, ph + 22, pa);
			case PACKS -> packs(g, mouseX, mouseY, px - 8, py - 22 + slide, pw + 16, ph + 22, pa);
			case SETTINGS -> settings(g, mouseX, mouseY, px - 8, py - 22 + slide, pw + 16, ph + 22, pa);
		}
		header(g, mouseX, mouseY, px, py, pw, a);
		if (drawerAnim > 0.01f && drawer != null) drawer(g, mouseX, mouseY, px, py + 24, pw, ph - 24, a);
		else if (drawerAnim > 0.01f) drawerAnim = 0;

		// card description after a short hover
		if (hovered != null && hovered == wasHovered) hoverTime += dt;
		else if (hovered != wasHovered) hoverTime = 0;
		if (hovered != null && hoverTime > 0.45f && drawer == null && tooltip == null) {
			tooltip = hovered.description;
		}
		if (tooltip != null) drawTooltip(g, tooltip, mouseX, mouseY);
		g.pose().popMatrix();
		Ui.zoom = 1f;
	}

	private void drawTooltip(GuiGraphicsExtractor g, String text, int mouseX, int mouseY) {
		List<String> lines = wrap(text, Ui.Font.SMALL, 150);
		float tw = 0;
		for (String l : lines) tw = Math.max(tw, Ui.width(l, Ui.Font.SMALL));
		float th = lines.size() * 9 + 7;
		float tx = Math.min(mouseX + 10, vw() - tw - 14), ty = Math.min(mouseY + 12, vh() - th - 4);
		Ui.round(g, tx, ty, tw + 12, th, 5, 0xF7FFFFFF, Theme.LINE, 1);
		for (int i = 0; i < lines.size(); i++) Ui.text(g, lines.get(i), tx + 6, ty + 4 + i * 9, Theme.TEXT, Ui.Font.SMALL);
	}

	private void sidebar(GuiGraphicsExtractor g, int mouseX, int mouseY, float x, float y, float ph, float a) {
		int items = Page.values().length + 1;
		float h = Math.min(ph, 40 + items * 38 + 4);
		Ui.shadow(g, x, y, SIDE, h, 14, 0.18f * a);
		Ui.round(g, x, y, SIDE, h, 8, Theme.alpha(Theme.SIDEBAR, a), Theme.alpha(Theme.GLASS_RIM, a), 1);
		Ui.image(g, EMBLEM, x + SIDE / 2f - 12, y + 8, 24, 24, 512, 512, Theme.alpha(0xFFFFFFFF, a));
		float iy = y + 40;
		for (Page p : Page.values()) {
			sideItem(g, mouseX, mouseY, x + 4, iy, p.label.toUpperCase(), p.icon, page == p, a, () -> setPage(p));
			iy += 38;
		}
		sideItem(g, mouseX, mouseY, x + 4, iy, "HUD", "move", false, a, () -> minecraft.gui.setScreen(new HudEditorScreen(this)));
	}

	private void sideItem(GuiGraphicsExtractor g, int mouseX, int mouseY, float x, float y, String label, String icon, boolean active, float a, Runnable action) {
		float w = SIDE - 8, h = 34;
		boolean over = Theme.inside(mouseX, mouseY, x, y, x + w, y + h);
		float hv = anim("side:" + label, over, 16), act = anim("sideA:" + label, active, 14);
		if (act > 0.01f) Ui.round(g, x, y, w, h, 5, Theme.alpha(Theme.COPPER, act * a));
		if (hv > 0.01f && !active) Ui.round(g, x, y, w, h, 5, Theme.alpha(Theme.COPPER, 0.09f * hv * a));
		int color = Theme.mix(Theme.mix(Theme.MUTED, Theme.TEXT, hv), Theme.INK, act);
		Ui.lucide(g, icon, x + w / 2 - 6.5f, y + 6, 13, Theme.alpha(color, a));
		Ui.centered(g, label, x + w / 2, y + 23, Theme.alpha(color, a), Ui.Font.TINY);
		hit(x, y, x + w, y + h, e -> {
			click();
			action.run();
		});
	}

	private void toolbar(GuiGraphicsExtractor g, int mouseX, int mouseY, float right, float y, float a) {
		String[] icons = {"move", "heart", "grid", "list", "close"};
		String[] tips = {"Edit HUD", "Favorites", "Grid view", "List view", "Close"};
		float bw = 18, gap = 2, w = icons.length * bw + (icons.length - 1) * gap + 6;
		float x = right - w;
		Ui.round(g, x, y, w, 18, 6, Theme.alpha(Theme.GLASS_STRONG, a), Theme.alpha(Theme.GLASS_RIM, a), 1);
		for (int i = 0; i < icons.length; i++) {
			float bx = x + 3 + i * (bw + gap);
			boolean over = Theme.inside(mouseX, mouseY, bx, y, bx + bw, y + 18);
			boolean on = (i == 1 && tab == Tab.FAVORITES) || (i == 2 && !listView) || (i == 3 && listView);
			float hv = anim("tool:" + i, over, 16);
			if (hv > 0.01f) Ui.round(g, bx, y + 2, bw, 14, 4, Theme.alpha(Theme.COPPER, 0.1f * hv * a));
			int color = on ? Theme.COPPER : Theme.mix(Theme.MUTED, Theme.TEXT, hv);
			Ui.lucide(g, i == 1 && on ? "heart_fill" : icons[i], bx + bw / 2 - 5, y + 4, 10, Theme.alpha(color, a));
			if (over) tooltip = tips[i];
			final int index = i;
			hit(bx, y, bx + bw, y + 18, e -> {
				click();
				switch (index) {
					case 0 -> minecraft.gui.setScreen(new HudEditorScreen(this));
					case 1 -> {
						setPage(Page.MODS);
						tab = tab == Tab.FAVORITES ? Tab.ALL : Tab.FAVORITES;
						scrollTarget = 0;
					}
					case 2 -> listView = false;
					case 3 -> listView = true;
					default -> onClose();
				}
			});
		}
	}

	/** The page name in Fraunces, plus the filter chips and search on the Mods page. */
	private void header(GuiGraphicsExtractor g, int mouseX, int mouseY, float x, float y, float w, float a) {
		String title = page.label;
		float tw = Ui.width(title, Ui.Font.DISPLAY_SM) + 20;
		Ui.text(g, title, x + 10, y + 8, Theme.alpha(Theme.TEXT, a), Ui.Font.DISPLAY_SM);
		if (page != Page.MODS) {
			String subtitle = switch (page) {
				case THEME -> "Accent colour and menu style";
				case PACKS -> "Resource packs, top of the list wins";
				default -> "Title screen, keybinds and HUD";
			};
			Ui.text(g, subtitle, x + tw + 8, y + 7, Theme.alpha(Theme.MUTED, a), Ui.Font.SMALL);
			return;
		}

		// filter chips
		float cx = x + tw + 8, cy = y + 4;
		for (Tab t : Tab.values()) {
			float cw = Ui.width(t.label, Ui.Font.SMALL) + 12;
			boolean active = tab == t;
			boolean over = Theme.inside(mouseX, mouseY, cx, cy, cx + cw, cy + 13);
			float act = anim("chip:" + t, active, 14), hv = anim("chipH:" + t, over, 16);
			if (act > 0.01f) Ui.round(g, cx, cy, cw, 13, 6.5f, Theme.alpha(Theme.CHIP, act * a));
			int color = Theme.mix(Theme.mix(Theme.MUTED, Theme.TEXT, hv), Theme.COPPER, act);
			Ui.text(g, t.label, cx + 6, cy + 3, Theme.alpha(color, a), Ui.Font.SMALL);
			hit(cx, cy, cx + cw, cy + 13, e -> {
				click();
				tab = t;
				scrollTarget = 0;
			});
			cx += cw + 3;
		}

		// search
		float sw = Math.max(70, Math.min(100, x + w - 8 - cx - 6)), sx = x + w - 8 - sw, sy = y + 3;
		boolean typing = !query.isEmpty();
		Ui.round(g, sx, sy, sw, 15, 5, Theme.alpha(0xE6FFFFFF, a), Theme.alpha(typing ? Theme.COPPER : Theme.LINE, a), 1);
		Ui.lucide(g, "search", sx + 5, sy + 3.5f, 8, Theme.alpha(typing ? Theme.TEXT : Theme.FAINT, a));
		boolean blink = (System.nanoTime() / 530_000_000L) % 2 == 0;
		String shown = typing ? query + (blink ? "|" : "") : "Search Mods";
		Ui.text(g, Ui.ellipsize(shown, Ui.Font.SMALL, Math.round(sw - 22)), sx + 16, sy + 4, Theme.alpha(typing ? Theme.TEXT : Theme.FAINT, a), Ui.Font.SMALL);
	}

	// ------------------------------------------------------------------ mods page

	private List<Module> filtered(Tab t) {
		String q = query.toLowerCase();
		List<Module> out = new ArrayList<>();
		for (Module m : modules().visible()) {
			boolean matches = switch (t) {
				case ALL -> true;
				case NEW -> NEW_MODS.contains(m.id);
				case FAVORITES -> m.favorite;
				case HUD -> m.category == Category.HUD;
				case UTILITY -> m.category == Category.UTILITY;
				case VISUAL -> m.category == Category.VISUAL;
			};
			if (!matches) continue;
			if (!q.isEmpty() && !m.name.toLowerCase().contains(q) && !m.description.toLowerCase().contains(q)) continue;
			out.add(m);
		}
		return out;
	}

	private void mods(GuiGraphicsExtractor g, int mouseX, int mouseY, float x, float y, float w, float h, float a) {
		float gx = x + 8, gy = y + 26, gw = w - 16, gh = h - 26 - 8;
		int cols = listView ? 1 : gw >= 560 ? 4 : 3;
		float cw = (gw - GAP * (cols - 1)) / cols;
		// three rows fit in the panel, like the reference layout
		float ch = listView ? 28 : Math.max(56, Math.min(cw * 0.62f, (gh - 2 * GAP) / 3f));
		List<Module> list = filtered(tab);
		int rows = (list.size() + cols - 1) / cols;
		maxScroll = Math.max(0, rows * (ch + GAP) - GAP - gh);
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		g.enableScissor(Math.round(gx - 2), Math.round(gy), Math.round(gx + gw + 2), Math.round(gy + gh));
		boolean inGrid = mouseY >= gy && mouseY < gy + gh;
		for (int i = 0; i < list.size(); i++) {
			float cx = gx + (i % cols) * (cw + GAP);
			float cy = gy + (i / cols) * (ch + GAP) - scroll;
			if (cy + ch < gy || cy > gy + gh) continue;
			if (listView) listRow(g, mouseX, mouseY, list.get(i), cx, cy, cw, ch, a, inGrid, gy, gy + gh);
			else card(g, mouseX, mouseY, list.get(i), cx, cy, cw, ch, a, inGrid, gy, gy + gh);
		}
		g.disableScissor();
		if (list.isEmpty()) {
			String empty = tab == Tab.FAVORITES ? "No favorites yet. Click a heart to add one." : query.isEmpty() ? "Nothing here yet" : "No mods match \"" + query + "\"";
			Ui.centered(g, empty, gx + gw / 2, gy + 40, Theme.alpha(Theme.MUTED, a), Ui.Font.MEDIUM);
		}
		scrollbar(g, gx + gw + 3, gy, gh, a);
	}

	private void scrollbar(GuiGraphicsExtractor g, float x, float y, float h, float a) {
		if (maxScroll <= 0) return;
		float thumb = Math.max(24, h * h / (h + maxScroll));
		float ty = y + (h - thumb) * (scroll / maxScroll);
		Ui.round(g, x, ty, 2.5f, thumb, 1.25f, Theme.alpha(0x401F2A22, a));
	}

	/** Whole-card hit first, so the heart and gear (registered later) win their own clicks. */
	private void cardHit(Module m, float x, float y, float w, float h, float clipTop, float clipBottom) {
		if (y + h > clipTop && y < clipBottom) hit(x, Math.max(y, clipTop), x + w, Math.min(y + h, clipBottom), e -> {
			click();
			if (e.button() == 1 && !m.settings().isEmpty()) openSettings(m);
			else m.toggle();
		});
	}

	private void heart(GuiGraphicsExtractor g, int mouseX, int mouseY, Module m, float x, float y, float a, boolean inView, float clipTop, float clipBottom) {
		boolean over = inView && Theme.inside(mouseX, mouseY, x - 3, y - 3, x + 12, y + 12);
		int color = m.favorite ? Theme.COPPER : over ? Theme.TEXT : Theme.ICON_OFF;
		Ui.lucide(g, m.favorite ? "heart_fill" : "heart", x, y, 9, Theme.alpha(color, a));
		hearts.put(m.id, new float[] {x + 4.5f, y + 4.5f});
		if (y - 3 >= clipTop && y + 12 <= clipBottom) hit(x - 3, y - 3, x + 12, y + 12, e -> {
			click();
			m.favorite = !m.favorite;
			TuffConfig.save(modules());
		});
	}

	private void gear(GuiGraphicsExtractor g, int mouseX, int mouseY, Module m, float x, float y, float a, float clipTop, float clipBottom) {
		boolean over = Theme.inside(mouseX, mouseY, x - 3, y - 3, x + 12, y + 12);
		Ui.lucide(g, "settings", x, y, 9, Theme.alpha(over ? Theme.TEXT : Theme.MUTED, a));
		if (y - 3 >= clipTop && y + 12 <= clipBottom) hit(x - 3, y - 3, x + 12, y + 12, e -> {
			click();
			openSettings(m);
		});
	}

	private void card(GuiGraphicsExtractor g, int mouseX, int mouseY, Module m, float x, float y, float w, float h, float a, boolean inView, float clipTop, float clipBottom) {
		boolean over = inView && Theme.inside(mouseX, mouseY, x, y, x + w, y + h);
		if (over) hovered = m;
		float hv = anim("card:" + m.id, over, 14);
		float on = anim("on:" + m.id, m.isEnabled(), 12);
		Ui.round(g, x, y, w, h, 4, Theme.alpha(Theme.mix(Theme.CARD, Theme.CARD_HOVER, hv), a), Theme.alpha(Theme.mix(Theme.GLASS_RIM, Theme.alpha(Theme.COPPER, 0.45f), hv), a), 1);
		cardHit(m, x, y, w, h, clipTop, clipBottom);

		if (NEW_MODS.contains(m.id)) {
			float bw = Ui.width("NEW", Ui.Font.TINY) + 7;
			Ui.round(g, x + 5, y + 5, bw, 8, 2, Theme.alpha(Theme.BADGE_NEW, a));
			Ui.text(g, "NEW", x + 8.5f, y + 6, Theme.alpha(0xFFFFFFFF, a), Ui.Font.TINY);
		}
		heart(g, mouseX, mouseY, m, x + w - 14, y + 5, a, inView, clipTop, clipBottom);
		if (hv > 0.3f && !m.settings().isEmpty()) gear(g, mouseX, mouseY, m, x + w - 27, y + 5, a * hv, clipTop, clipBottom);

		// centre: a live preview for HUD mods in a world, otherwise a big line icon
		float areaTop = y + 12, areaBottom = y + h - 17;
		if (!preview(g, m, x + w / 2, (areaTop + areaBottom) / 2, w - 18, areaBottom - areaTop, on, a)) {
			float size = Math.min(36, areaBottom - areaTop - 2);
			int color = Theme.mix(Theme.ICON_OFF, Theme.COPPER, on);
			Ui.lucide(g, ICONS.getOrDefault(m.id, "grid"), x + w / 2 - size / 2, (areaTop + areaBottom) / 2 - size / 2, size, Theme.alpha(color, a));
		}

		// name and switch
		Ui.text(g, Ui.ellipsize(m.name, Ui.Font.NAME, Math.round(w - 34)), x + 6, y + h - 13.5f, Theme.alpha(Theme.mix(Theme.MUTED, Theme.TEXT, on), a), Ui.Font.NAME);
		float sx = x + w - 6 - 20, sy = y + h - 14;
		Ui.pillSwitch(g, sx, sy, on, a);
		switches.put(m.id, new float[] {sx + 10, sy + 5});
	}

	/** Draws a HUD module's own preview scaled into the card; false when there is nothing to show. */
	private boolean preview(GuiGraphicsExtractor g, Module m, float cx, float cy, float maxW, float maxH, float on, float a) {
		if (!(m instanceof HudModule hud) || minecraft.player == null || a < 0.5f) return false;
		float pw = Math.max(1, hud.width()), ph = Math.max(1, hud.height());
		float s = Math.min(1.4f, Math.min(maxW / pw, maxH / ph));
		// HUD text is light, made for the world, so previews sit on a small dark tile
		Ui.round(g, cx - pw * s / 2 - 3, cy - ph * s / 2 - 3, pw * s + 6, ph * s + 6, 4, Theme.alpha(0xE62A3A30, a));
		g.pose().pushMatrix();
		g.pose().translate(cx - pw * s / 2, cy - ph * s / 2);
		g.pose().scale(s, s);
		try {
			hud.render(g, minecraft.font, true);
		} catch (RuntimeException e) {
			g.pose().popMatrix();
			return false;
		}
		g.pose().popMatrix();
		if (on < 0.99f) Ui.round(g, cx - pw * s / 2 - 1, cy - ph * s / 2 - 1, pw * s + 2, ph * s + 2, 2, Theme.alpha(0xFFF6F4EE, 0.6f * (1 - on) * a));
		return true;
	}

	private void listRow(GuiGraphicsExtractor g, int mouseX, int mouseY, Module m, float x, float y, float w, float h, float a, boolean inView, float clipTop, float clipBottom) {
		boolean over = inView && Theme.inside(mouseX, mouseY, x, y, x + w, y + h);
		if (over) hovered = m;
		float hv = anim("card:" + m.id, over, 14);
		float on = anim("on:" + m.id, m.isEnabled(), 12);
		Ui.round(g, x, y, w, h, 4, Theme.alpha(Theme.mix(Theme.CARD, Theme.CARD_HOVER, hv), a), Theme.alpha(Theme.mix(Theme.GLASS_RIM, Theme.alpha(Theme.COPPER, 0.45f), hv), a), 1);
		cardHit(m, x, y, w, h, clipTop, clipBottom);
		Ui.lucide(g, ICONS.getOrDefault(m.id, "grid"), x + 8, y + 6, 16, Theme.alpha(Theme.mix(Theme.ICON_OFF, Theme.COPPER, on), a));
		float nx = x + 32;
		Ui.text(g, m.name, nx, y + 4.5f, Theme.alpha(Theme.mix(Theme.MUTED, Theme.TEXT, on), a), Ui.Font.SEMIBOLD);
		if (NEW_MODS.contains(m.id)) {
			float bx = nx + Ui.width(m.name, Ui.Font.SEMIBOLD) + 5;
			Ui.round(g, bx, y + 5.5f, Ui.width("NEW", Ui.Font.TINY) + 7, 8, 2, Theme.alpha(Theme.BADGE_NEW, a));
			Ui.text(g, "NEW", bx + 3.5f, y + 6.5f, Theme.alpha(0xFFFFFFFF, a), Ui.Font.TINY);
		}
		Ui.text(g, Ui.ellipsize(m.description, Ui.Font.SMALL, Math.round(w - 32 - 80)), nx, y + 16, Theme.alpha(Theme.MUTED, a), Ui.Font.SMALL);
		float sx = x + w - 8 - 20, sy = y + h / 2 - 5;
		Ui.pillSwitch(g, sx, sy, on, a);
		switches.put(m.id, new float[] {sx + 10, sy + 5});
		heart(g, mouseX, mouseY, m, sx - 18, y + h / 2 - 4.5f, a, inView, clipTop, clipBottom);
		if (!m.settings().isEmpty()) gear(g, mouseX, mouseY, m, sx - 33, y + h / 2 - 4.5f, a * (0.4f + 0.6f * hv), clipTop, clipBottom);
	}

	// ------------------------------------------------------------------ settings drawer

	private void drawer(GuiGraphicsExtractor g, int mouseX, int mouseY, float x, float y, float w, float h, float a) {
		Module m = drawer;
		float dw = Math.min(230, w * 0.42f);
		float ease = 1 - (1 - drawerAnim) * (1 - drawerAnim);
		float dx = x + w - dw * ease;
		drawerLeft = dx;
		hit(x, y, dx, y + h, e -> drawer = null);
		g.enableScissor(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
		Ui.round(g, x + 1, y - 12, w - 2, h + 11, 11, Theme.alpha(0x8CF6F4EE, ease * a));
		Ui.shadow(g, dx, y, dw, h, 16, 0.2f * ease);
		Ui.round(g, dx, y, dw + 12, h, 12, Theme.alpha(0xFAFBFAF6, a), Theme.alpha(Theme.GLASS_RIM, a), 1);
		hit(dx, y, x + w, y + h, e -> { });

		float px = dx + 14, pw = dw - 28;
		float cy = y + 14;
		Ui.lucide(g, ICONS.getOrDefault(m.id, "grid"), px, cy + 1, 12, Theme.alpha(Theme.COPPER, a));
		Ui.text(g, Ui.ellipsize(m.name, Ui.Font.BOLD, Math.round(pw - 40)), px + 18, cy + 2, Theme.alpha(Theme.TEXT, a), Ui.Font.BOLD);
		float bx = dx + dw - 14 - 16;
		boolean closeOver = Theme.inside(mouseX, mouseY, bx, cy - 2, bx + 16, cy + 14);
		Ui.lucide(g, "close", bx + 2, cy, 12, Theme.alpha(closeOver ? Theme.TEXT : Theme.MUTED, a));
		hit(bx, cy - 2, bx + 16, cy + 14, e -> drawer = null);
		cy += 20;
		for (String line : wrap(m.description, Ui.Font.BODY, Math.round(pw))) {
			Ui.text(g, line, px, cy, Theme.alpha(Theme.MUTED, a), Ui.Font.BODY);
			cy += 11.5f;
		}
		cy += 6;

		float top = cy, bottom = y + h - 10;
		g.enableScissor(Math.round(dx), Math.round(top), Math.round(dx + dw), Math.round(bottom));
		cy -= drawerScroll;
		cy = row(g, mouseX, mouseY, px, cy, pw, "Enabled", m.isEnabled(), m::toggle, a, top, bottom);
		for (Setting<?> setting : m.settings()) {
			switch (setting) {
				case BoolSetting b -> cy = row(g, mouseX, mouseY, px, cy, pw, b.name, b.get(), b::toggle, a, top, bottom);
				case ChoiceSetting c -> cy = choice(g, mouseX, mouseY, px, cy, pw, c, a, top, bottom);
				case NumberSetting n -> cy = slider(g, mouseX, mouseY, px, cy, pw, n, a, top, bottom);
			}
		}
		float content = cy + drawerScroll - top;
		drawerMax = Math.max(0, content - (bottom - top));
		g.disableScissor();
		g.disableScissor();
	}

	private float drawerMax;

	private float row(GuiGraphicsExtractor g, int mouseX, int mouseY, float x, float y, float w, String label, boolean value, Runnable toggle, float a, float top, float bottom) {
		Ui.round(g, x, y, w, 26, 7, Theme.alpha(0xFFEEF1EB, a));
		Ui.text(g, label, x + 9, y + 9, Theme.alpha(Theme.TEXT, a), Ui.Font.MEDIUM);
		Ui.toggle(g, x + w - 9 - 22, y + 7, anim("row:" + drawer.id + label, value, 14), a);
		if (y + 26 > top && y < bottom) hit(x, Math.max(y, top), x + w, Math.min(y + 26, bottom), e -> {
			click();
			toggle.run();
		});
		return y + 30;
	}

	private float choice(GuiGraphicsExtractor g, int mouseX, int mouseY, float x, float y, float w, ChoiceSetting c, float a, float top, float bottom) {
		Ui.text(g, c.name, x + 2, y + 2, Theme.alpha(Theme.TEXT, a), Ui.Font.MEDIUM);
		float cx = x, cy = y + 14;
		for (int i = 0; i < c.options.length; i++) {
			String opt = c.options[i];
			float pw = Ui.width(opt, Ui.Font.MEDIUM) + 14;
			if (cx + pw > x + w) {
				cx = x;
				cy += 20;
			}
			boolean selected = c.get() == i;
			boolean over = Theme.inside(mouseX, mouseY, cx, cy, cx + pw, cy + 17);
			float act = anim("chip:" + drawer.id + c.id + i, selected, 16);
			Ui.round(g, cx, cy, pw, 17, 6, Theme.alpha(Theme.mix(over ? 0xFFE3E8E1 : 0xFFEEF1EB, Theme.COPPER, act), a));
			Ui.text(g, opt, cx + 7, cy + 5, Theme.alpha(Theme.mix(Theme.TEXT, Theme.INK, act), a), Ui.Font.MEDIUM);
			final int index = i;
			if (cy + 17 > top && cy < bottom) hit(cx, Math.max(cy, top), cx + pw, Math.min(cy + 17, bottom), e -> {
				click();
				c.set(index);
				if (drawer == modules().ui) modules().ui.applyAccent();
			});
			cx += pw + 4;
		}
		return cy + 24;
	}

	private float slider(GuiGraphicsExtractor g, int mouseX, int mouseY, float x, float y, float w, NumberSetting n, float a, float top, float bottom) {
		Ui.text(g, n.name, x + 2, y + 2, Theme.alpha(Theme.TEXT, a), Ui.Font.MEDIUM);
		Ui.right(g, n.display(), x + w - 2, y + 2, Theme.alpha(Theme.COPPER_HI, a), Ui.Font.SEMIBOLD);
		float ty = y + 18, frac = (float) n.fraction();
		Ui.round(g, x + 2, ty, w - 4, 4, 2, Theme.alpha(Theme.OFF, a));
		Ui.round(g, x + 2, ty, Math.max(4, (w - 4) * frac), 4, 2, Theme.alpha(Theme.COPPER, a));
		float kx = x + 2 + (w - 4) * frac;
		Ui.circle(g, kx, ty + 2.4f, dragging == n ? 5.5f : 4.5f, Theme.alpha(0x2A1E3C28, a));
		Ui.circle(g, kx, ty + 2, dragging == n ? 5.5f : 4.5f, Theme.alpha(0xFFFFFFFF, a));
		if (ty + 8 > top && ty - 4 < bottom) hit(x, ty - 6, x + w, ty + 10, e -> {
			dragging = n;
			dragX1 = x + 2;
			dragX2 = x + w - 2;
			updateSlider(clickX);
		});
		return y + 32;
	}

	private void updateSlider(double mouseX) {
		if (dragging == null) return;
		double t = Math.max(0, Math.min(1, (mouseX - dragX1) / (dragX2 - dragX1)));
		dragging.set(dragging.min + t * (dragging.max - dragging.min));
	}

	private static List<String> wrap(String text, Ui.Font font, int width) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (Ui.width(next, font) > width && !line.isEmpty()) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		if (!line.isEmpty()) lines.add(line.toString());
		return lines;
	}

	// ------------------------------------------------------------------ theme page

	private void theme(GuiGraphicsExtractor g, int mouseX, int mouseY, float mx, float y, float mw, float h, float a) {
		float px = mx + 18, pw = mw - 36, cy = y + 52;
		Ui.text(g, "Accent", px, cy, Theme.alpha(Theme.TEXT, a), Ui.Font.SEMIBOLD);
		cy += 14;
		Theme.Accent[] accents = Theme.Accent.values();
		int cols = accents.length;
		float sw = (pw - 6 * (cols - 1)) / cols;
		for (int i = 0; i < accents.length; i++) {
			Theme.Accent acc = accents[i];
			float sx = px + (i % cols) * (sw + 6), sy = cy + (i / cols) * 52;
			boolean selected = modules().ui.accent.get() == i;
			boolean over = Theme.inside(mouseX, mouseY, sx, sy, sx + sw, sy + 46);
			float act = anim("acc:" + i, selected, 14), hv = anim("accH:" + i, over, 16);
			Ui.round(g, sx, sy, sw, 46, 9, Theme.alpha(Theme.mix(0xB3FFFFFF, 0xF2FFFFFF, hv), a), Theme.alpha(Theme.mix(Theme.GLASS_RIM, acc.base, act), a), 1);
			Ui.circle(g, sx + sw / 2, sy + 17, 9, Theme.alpha(acc.base, a));
			Ui.round(g, sx + sw / 2 - 6, sy + 10, 12, 5, 2.5f, Theme.alpha(0x40FFFFFF, a));
			if (act > 0.05f) Ui.lucide(g, "check", sx + sw / 2 - 5, sy + 12, 10, Theme.alpha(0xFFFFFFFF, act * a));
			Ui.centered(g, acc.label, sx + sw / 2, sy + 32, Theme.alpha(Theme.mix(Theme.MUTED, Theme.TEXT, act), a), Ui.Font.MEDIUM);
			final int index = i;
			hit(sx, sy, sx + sw, sy + 46, e -> {
				click();
				modules().ui.accent.set(index);
				modules().ui.applyAccent();
			});
		}
		cy += ((accents.length + cols - 1) / cols) * 52 + 10;
		cy = optionRow(g, mouseX, mouseY, px, cy, pw, "Background blur", "Blur the game behind Glade menus.", modules().ui.blur.get(), modules().ui.blur::toggle, a);
		cy = optionRow(g, mouseX, mouseY, px, cy, pw, "Menu animations", "Smooth transitions, hovers and scrolling.", modules().ui.animations.get(), modules().ui.animations::toggle, a);
	}

	private float optionRow(GuiGraphicsExtractor g, int mouseX, int mouseY, float x, float y, float w, String label, String hint, boolean value, Runnable toggle, float a) {
		boolean over = Theme.inside(mouseX, mouseY, x, y, x + w, y + 34);
		float hv = anim("opt:" + label, over, 16);
		Ui.round(g, x, y, w, 34, 9, Theme.alpha(Theme.mix(0xB3FFFFFF, 0xF2FFFFFF, hv), a), Theme.alpha(Theme.GLASS_RIM, a), 1);
		Ui.text(g, label, x + 12, y + 8, Theme.alpha(Theme.TEXT, a), Ui.Font.SEMIBOLD);
		Ui.text(g, Ui.ellipsize(hint, Ui.Font.BODY, Math.round(w - 58)), x + 12, y + 20, Theme.alpha(Theme.MUTED, a), Ui.Font.BODY);
		Ui.toggle(g, x + w - 12 - 22, y + 11, anim("optT:" + label, value, 14), a);
		hit(x, y, x + w, y + 34, e -> {
			click();
			toggle.run();
		});
		return y + 40;
	}

	private float actionRow(GuiGraphicsExtractor g, int mouseX, int mouseY, float x, float y, float w, String label, String hint, String button, Runnable action, float a) {
		Ui.round(g, x, y, w, 34, 9, Theme.alpha(0xB3FFFFFF, a), Theme.alpha(Theme.GLASS_RIM, a), 1);
		Ui.text(g, label, x + 12, y + 8, Theme.alpha(Theme.TEXT, a), Ui.Font.SEMIBOLD);
		float bw = Ui.width(button, Ui.Font.SEMIBOLD) + 18, bx = x + w - 10 - bw, by = y + 8;
		Ui.text(g, Ui.ellipsize(hint, Ui.Font.BODY, Math.round(w - 32 - bw)), x + 12, y + 20, Theme.alpha(Theme.MUTED, a), Ui.Font.BODY);
		boolean over = Theme.inside(mouseX, mouseY, bx, by, bx + bw, by + 18);
		Ui.glassButton(g, bx, by, bw, 18, anim("act:" + label, over, 16), a);
		Ui.centered(g, button, bx + bw / 2, by + 5, Theme.alpha(Theme.TEXT, a), Ui.Font.SEMIBOLD);
		hit(bx, by, bx + bw, by + 18, e -> {
			click();
			action.run();
		});
		return y + 40;
	}

	// ------------------------------------------------------------------ settings page

	private void settings(GuiGraphicsExtractor g, int mouseX, int mouseY, float mx, float y, float mw, float h, float a) {
		float px = mx + 18, pw = mw - 36, cy = y + 52;
		var title = modules().titleScreen;
		cy = optionRow(g, mouseX, mouseY, px, cy, pw, "Glade title screen", "Use the Glade main menu instead of the vanilla one.", title.isEnabled(), title::toggle, a);
		cy = optionRow(g, mouseX, mouseY, px, cy, pw, "Animated background", "Drifting clouds, a moving sun and swaying trees.", title.animate.get(), title.animate::toggle, a);
		cy = actionRow(g, mouseX, mouseY, px, cy, pw, "Keybinds", "Menu is Right Shift, zoom is C by default.", "Open", () -> minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options)), a);
		cy = actionRow(g, mouseX, mouseY, px, cy, pw, "HUD layout", "Move every HUD module back to its default spot.", "Reset", () -> {
			for (HudModule m : modules().hud()) m.resetPosition();
			TuffConfig.save(modules());
		}, a);
		if (cy < y + h - 22) Ui.text(g, "Settings are saved to config/tuff/config.json", px, y + h - 17, Theme.alpha(Theme.FAINT, a), Ui.Font.BODY);
	}

	// ------------------------------------------------------------------ packs page

	private void loadPacks() {
		PackRepository repo = minecraft.getResourcePackRepository();
		repo.reload();
		enabledPacks = new ArrayList<>(repo.getSelectedIds());
		java.util.Collections.reverse(enabledPacks);
		availablePacks = new ArrayList<>();
		for (String id : repo.getAvailableIds()) if (!enabledPacks.contains(id)) availablePacks.add(id);
		packsDirty = false;
	}

	private void applyPacks() {
		PackRepository repo = minecraft.getResourcePackRepository();
		List<String> bottomFirst = new ArrayList<>(enabledPacks);
		java.util.Collections.reverse(bottomFirst);
		repo.setSelected(bottomFirst);
		minecraft.options.updateResourcePacks(repo);
		packsDirty = false;
	}

	private void packs(GuiGraphicsExtractor g, int mouseX, int mouseY, float mx, float y, float mw, float h, float a) {
		if (enabledPacks == null) loadPacks();
		PackRepository repo = minecraft.getResourcePackRepository();
		float px = mx + 18, pw = mw - 36;
		float colW = (pw - 10) / 2, top = y + 52, listTop = top + 16, bottom = y + h - 34;
		Ui.text(g, "Enabled", px, top, Theme.alpha(Theme.TEXT, a), Ui.Font.SEMIBOLD);
		Ui.text(g, "Available", px + colW + 10, top, Theme.alpha(Theme.TEXT, a), Ui.Font.SEMIBOLD);
		int rows = Math.max(enabledPacks.size(), availablePacks.size());
		maxScroll = Math.max(0, rows * 32 - (bottom - listTop));
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		g.enableScissor(Math.round(px - 2), Math.round(listTop), Math.round(px + pw + 2), Math.round(bottom));
		for (int i = 0; i < enabledPacks.size(); i++) {
			packRow(g, mouseX, mouseY, repo, enabledPacks.get(i), px, listTop + i * 32 - scroll, colW, true, i, a, listTop, bottom);
		}
		for (int i = 0; i < availablePacks.size(); i++) {
			packRow(g, mouseX, mouseY, repo, availablePacks.get(i), px + colW + 10, listTop + i * 32 - scroll, colW, false, i, a, listTop, bottom);
		}
		g.disableScissor();

		float by = y + h - 28;
		String more = "Open pack folder & more";
		float mwBtn = Ui.width(more, Ui.Font.SEMIBOLD) + 20;
		boolean overMore = Theme.inside(mouseX, mouseY, px, by, px + mwBtn, by + 20);
		Ui.glassButton(g, px, by, mwBtn, 20, anim("packsMore", overMore, 16), a);
		Ui.centered(g, more, px + mwBtn / 2, by + 6, Theme.alpha(Theme.TEXT, a), Ui.Font.SEMIBOLD);
		hit(px, by, px + mwBtn, by + 20, e -> {
			click();
			if (packsDirty) applyPacks();
			minecraft.gui.setScreen(new PackSelectionScreen(repo, r -> {
				minecraft.options.updateResourcePacks(r);
				enabledPacks = null;
				minecraft.gui.setScreen(this);
			}, minecraft.getResourcePackDirectory(), Component.literal("Resource packs")));
		});
		String apply = packsDirty ? "Apply changes" : "Up to date";
		float aw = Ui.width(apply, Ui.Font.SEMIBOLD) + 22, ax = px + pw - aw;
		boolean overApply = packsDirty && Theme.inside(mouseX, mouseY, ax, by, ax + aw, by + 20);
		if (packsDirty) Ui.accentButton(g, ax, by, aw, 20, anim("apply", overApply, 16), a);
		else Ui.glassButton(g, ax, by, aw, 20, 0, a * 0.6f);
		Ui.centered(g, apply, ax + aw / 2, by + 6, Theme.alpha(packsDirty ? Theme.INK : Theme.MUTED, a), Ui.Font.SEMIBOLD);
		if (packsDirty) hit(ax, by, ax + aw, by + 20, e -> {
			click();
			applyPacks();
		});
	}

	private void packRow(GuiGraphicsExtractor g, int mouseX, int mouseY, PackRepository repo, String id, float x, float y, float w, boolean enabled, int index, float a, float top, float bottom) {
		if (y + 28 < top || y > bottom) return;
		Pack pack = repo.getPack(id);
		String title = pack == null ? id : pack.getTitle().getString();
		String desc = pack == null ? "" : pack.getDescription().getString().replace('\n', ' ');
		boolean required = pack != null && pack.isRequired();
		boolean fixed = pack != null && pack.isFixedPosition();
		boolean over = Theme.inside(mouseX, mouseY, x, y, x + w, y + 28) && mouseY >= top && mouseY < bottom;
		float hv = anim("pack:" + enabled + id, over, 16);
		Ui.round(g, x, y, w, 28, 8, Theme.alpha(Theme.mix(0xB3FFFFFF, 0xF2FFFFFF, hv), a), Theme.alpha(enabled ? Theme.alpha(Theme.COPPER, 0.4f) : Theme.GLASS_RIM, a), 1);
		float textW = w - 20 - (required ? Ui.width("Built-in", Ui.Font.BODY) + 8 : enabled ? 48 : 20);
		Ui.text(g, Ui.ellipsize(title, Ui.Font.SEMIBOLD, Math.round(textW)), x + 10, y + 6, Theme.alpha(Theme.TEXT, a), Ui.Font.SEMIBOLD);
		Ui.text(g, Ui.ellipsize(desc, Ui.Font.BODY, Math.round(textW)), x + 10, y + 17, Theme.alpha(Theme.MUTED, a), Ui.Font.BODY);
		if (required) {
			Ui.text(g, "Built-in", x + w - 10 - Ui.width("Built-in", Ui.Font.BODY), y + 11, Theme.alpha(Theme.FAINT, a), Ui.Font.BODY);
			return;
		}
		float bx = x + w - 22;
		iconButton(g, mouseX, mouseY, enabled ? "close" : "plus", bx, y + 7, a, top, bottom, () -> {
			if (enabled) {
				enabledPacks.remove(id);
				availablePacks.add(0, id);
			} else {
				availablePacks.remove(id);
				enabledPacks.add(0, id);
			}
			packsDirty = true;
		});
		if (enabled && !fixed) {
			if (index > 0) iconButton(g, mouseX, mouseY, "up", bx - 30, y + 7, a, top, bottom, () -> {
				java.util.Collections.swap(enabledPacks, index, index - 1);
				packsDirty = true;
			});
			if (index < enabledPacks.size() - 1) iconButton(g, mouseX, mouseY, "down", bx - 15, y + 7, a, top, bottom, () -> {
				java.util.Collections.swap(enabledPacks, index, index + 1);
				packsDirty = true;
			});
		}
	}

	private void iconButton(GuiGraphicsExtractor g, int mouseX, int mouseY, String icon, float x, float y, float a, float top, float bottom, Runnable action) {
		boolean over = Theme.inside(mouseX, mouseY, x - 2, y - 2, x + 14, y + 14) && mouseY >= top && mouseY < bottom;
		if (over) Ui.round(g, x - 2, y - 2, 16, 16, 4, Theme.alpha(Theme.COPPER, 0.1f * a));
		Ui.lucide(g, icon, x + 1, y + 1, 10, Theme.alpha(over ? Theme.COPPER_HI : Theme.MUTED, a));
		if (y + 14 > top && y < bottom) hit(x - 2, y - 2, x + 14, y + 14, e -> {
			click();
			action.run();
		});
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		float z = zoom();
		double x = event.x() / z, y = event.y() / z;
		clickX = x;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (Theme.inside(x, y, h.x1(), h.y1(), h.x2(), h.y2())) {
				h.action().accept(event);
				return true;
			}
		}
		if (!Theme.inside(x, y, wx(), wy(), wx() + ww(), wy() + wh())) {
			onClose();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging != null) {
			updateSlider(event.x() / zoom());
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
		if (drawer != null && mouseX / zoom() > drawerLeft) {
			drawerScroll = (float) Math.max(0, Math.min(drawerMax, drawerScroll - scrollY * 18));
		} else {
			scrollTarget = (float) Math.max(0, Math.min(maxScroll, scrollTarget - scrollY * 30));
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			if (drawer != null) {
				drawer = null;
				return true;
			}
			if (!query.isEmpty()) {
				query = "";
				return true;
			}
		}
		if (page == Page.MODS && drawer == null && event.key() == InputConstants.KEY_BACKSPACE && !query.isEmpty()) {
			query = query.substring(0, query.length() - 1);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (page == Page.MODS && drawer == null && event.isAllowedChatCharacter() && query.length() < 32) {
			if (query.isEmpty() && event.codepointAsString().isBlank()) return true;
			query += event.codepointAsString();
			scrollTarget = 0;
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public void onClose() {
		TuffConfig.save(modules());
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
