package net.tuffclient.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

/** The Tuff menu: sidebar navigation with Mods, Theme, Packs and Settings pages. */
public final class TuffMenuScreen extends Screen {
	public enum Page {
		MODS("Mods", "grid"), THEME("Theme", "brush"), PACKS("Packs", "image"), SETTINGS("Settings", "sliders");

		final String label;
		final String icon;

		Page(String label, String icon) {
			this.label = label;
			this.icon = icon;
		}
	}

	private enum Tab {
		ALL("All"), ENABLED("Enabled"), HUD("HUD"), UTILITY("Utility"), VISUAL("Visual");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private record Hit(float x1, float y1, float x2, float y2, Consumer<MouseButtonEvent> action) {
	}

	private static final Identifier EMBLEM = Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "textures/gui/title/emblem.png");
	private static final Map<String, String> ICONS = Map.ofEntries(
			Map.entry("fps", "gauge"), Map.entry("cps", "mouse"), Map.entry("keystrokes", "keyboard"),
			Map.entry("armor", "shirt"), Map.entry("coordinates", "pin"), Map.entry("ping", "signal"),
			Map.entry("effects", "potion"), Map.entry("clock", "clock"), Map.entry("server", "globe"),
			Map.entry("speed", "bolt"), Map.entry("memory", "chip"), Map.entry("zoom", "search"),
			Map.entry("fullbright", "sun"), Map.entry("toggle_sprint", "sprint"), Map.entry("crosshair", "crosshair"),
			Map.entry("hide_scoreboard", "list"), Map.entry("title_screen", "image"));
	private static final int CARD_H = 90;
	/** The menu is laid out in a space about this many units tall, whatever the GUI scale. */
	private static final float VIRTUAL_HEIGHT = 400;
	private static final int GAP = 8;
	private static final int SIDEBAR = 112;

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private final Map<String, Float> anim = new HashMap<>();
	private final TitleBackground background = new TitleBackground();
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

	public TuffMenuScreen(Screen parent) {
		this(parent, Page.MODS);
	}

	public TuffMenuScreen(Screen parent, Page page) {
		super(Component.literal("Tuff"));
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
		float unit = Math.max(1, Math.min(gs, height * gs / VIRTUAL_HEIGHT));
		return unit / gs;
	}

	private float vw() {
		return width / zoom();
	}

	private float vh() {
		return height / zoom();
	}

	private float ww() {
		return Math.max(360, Math.min(620, vw() * 0.9f));
	}

	private float wh() {
		return Math.max(220, Math.min(370, vh() * 0.88f));
	}

	private float wx() {
		return (vw() - ww()) / 2;
	}

	private float wy() {
		return (vh() - wh()) / 2;
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level == null) {
			background.render(g, width, height, mouseX, mouseY, TuffClient.modules().titleScreen.animate.get());
			g.fill(0, 0, width, height, 0x8A080908);
		} else if (modules().ui.blur.get()) {
			extractBlurredBackground(g);
			g.fill(0, 0, width, height, 0x66000000);
		} else {
			g.fill(0, 0, width, height, 0xB0000000);
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

		float x = wx(), y = wy(), w = ww(), h = wh();
		float s = 0.96f + 0.04f * open;
		Ui.zoom = z;
		g.pose().pushMatrix();
		g.pose().scale(z, z);
		g.pose().translate(vw() / 2f, vh() / 2f);
		g.pose().scale(s, s);
		g.pose().translate(-vw() / 2f, -vh() / 2f);
		float a = open;

		Ui.shadow(g, x, y, w, h, 22, 0.55f * a);
		Ui.round(g, x, y, w, h, 12, Theme.alpha(0xF5161816, a), Theme.alpha(0x24FFFFFF, a), 1);
		sidebar(g, mouseX, mouseY, x, y, h, a);

		float mx = x + SIDEBAR, mw = w - SIDEBAR;
		float pa = a * pageFade;
		float slide = (1 - pageFade) * 6;
		switch (page) {
			case MODS -> mods(g, mouseX, mouseY, mx, y + slide, mw, h, pa);
			case THEME -> theme(g, mouseX, mouseY, mx, y + slide, mw, h, pa);
			case PACKS -> packs(g, mouseX, mouseY, mx, y + slide, mw, h, pa);
			case SETTINGS -> settings(g, mouseX, mouseY, mx, y + slide, mw, h, pa);
		}
		header(g, mouseX, mouseY, mx, y, mw, a);
		if (drawerAnim > 0.01f && drawer != null) drawer(g, mouseX, mouseY, x, y, w, h, a);
		else if (drawerAnim > 0.01f) drawerAnim = 0;
		g.pose().popMatrix();
		Ui.zoom = 1f;
	}

	private void sidebar(GuiGraphicsExtractor g, int mouseX, int mouseY, float x, float y, float h, float a) {
		Ui.round(g, x + 1, y + 1, SIDEBAR, h - 2, 11, Theme.alpha(0xFF101210, a));
		g.fill(Math.round(x + SIDEBAR - 12), Math.round(y + 1), Math.round(x + SIDEBAR), Math.round(y + h - 1), Theme.alpha(0xFF101210, a));
		g.fill(Math.round(x + SIDEBAR), Math.round(y + 12), Math.round(x + SIDEBAR + 1), Math.round(y + h - 12), Theme.alpha(0x16FFFFFF, a));

		Ui.image(g, EMBLEM, x + 12, y + 12, 22, 22, 512, 512, Theme.alpha(0xFFFFFFFF, a));
		Ui.spaced(g, "TUFF", x + 40, y + 13, Theme.alpha(Theme.TEXT, a), Ui.Font.BOLD, 1.5f);
		Ui.text(g, "Client", x + 40, y + 24, Theme.alpha(Theme.MUTED, a), Ui.Font.BODY);

		float ny = y + 48;
		for (Page p : Page.values()) {
			navItem(g, mouseX, mouseY, x + 8, ny, p.label, p.icon, page == p, a, () -> setPage(p));
			ny += 25;
		}
		navItem(g, mouseX, mouseY, x + 8, ny, "Edit HUD", "layout", false, a, () -> minecraft.gui.setScreen(new HudEditorScreen(this)));

		// player card
		float cy = y + h - 38;
		String name = minecraft.getUser().getName();
		long on = modules().visible().stream().filter(Module::isEnabled).count();
		Ui.round(g, x + 8, cy, SIDEBAR - 16, 30, 8, Theme.alpha(0xFF1B1E1C, a));
		Ui.circle(g, x + 23, cy + 15, 8, Theme.alpha(Theme.COPPER, a));
		Ui.centered(g, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), x + 23, cy + 11.5f, Theme.alpha(Theme.INK, a), Ui.Font.BOLD);
		Ui.text(g, Ui.ellipsize(name, Ui.Font.SEMIBOLD, SIDEBAR - 52), x + 36, cy + 6, Theme.alpha(Theme.TEXT, a), Ui.Font.SEMIBOLD);
		Ui.circle(g, x + 38.5f, cy + 21.5f, 2, Theme.alpha(Theme.ONLINE, a));
		Ui.text(g, on + "/" + modules().visible().size() + " mods", x + 43, cy + 18, Theme.alpha(Theme.MUTED, a), Ui.Font.BODY);
	}

	private void navItem(GuiGraphicsExtractor g, int mouseX, int mouseY, float x, float y, String label, String icon, boolean active, float a, Runnable action) {
		float w = SIDEBAR - 16, h = 22;
		boolean over = Theme.inside(mouseX, mouseY, x, y, x + w, y + h);
		float hv = anim("nav:" + label, over, 16);
		float act = anim("navA:" + label, active, 14);
		if (act > 0.01f) Ui.round(g, x, y, w, h, 7, Theme.alpha(Theme.COPPER, 0.16f * act * a));
		if (hv > 0.01f && !active) Ui.round(g, x, y, w, h, 7, Theme.alpha(0xFFFFFFFF, 0.05f * hv * a));
		if (act > 0.01f) Ui.round(g, x, y + 5, 2.5f, h - 10, 1.25f, Theme.alpha(Theme.COPPER, act * a));
		int color = Theme.mix(Theme.mix(Theme.MUTED, Theme.TEXT, hv), Theme.COPPER_HI, act);
		Ui.icon(g, icon, x + 9, y + 5, 12, Theme.alpha(color, a));
		Ui.text(g, label, x + 28, y + 7, Theme.alpha(Theme.mix(Theme.mix(Theme.MUTED, Theme.TEXT, hv), Theme.TEXT, act), a), Ui.Font.SEMIBOLD);
		hit(x, y, x + w, y + h, e -> {
			click();
			action.run();
		});
	}

	private void header(GuiGraphicsExtractor g, int mouseX, int mouseY, float mx, float y, float mw, float a) {
		String subtitle = switch (page) {
			case MODS -> {
				long on = modules().visible().stream().filter(Module::isEnabled).count();
				yield on + " of " + modules().visible().size() + " enabled";
			}
			case THEME -> "Accent colour and menu style";
			case PACKS -> "Resource packs, top of the list wins";
			case SETTINGS -> "Title screen, keybinds and HUD";
		};
		Ui.text(g, page.label, mx + 18, y + 13, Theme.alpha(Theme.TEXT, a), Ui.Font.TITLE);
		Ui.text(g, subtitle, mx + 18, y + 31, Theme.alpha(Theme.MUTED, a), Ui.Font.BODY);

		// close button
		float cx = mx + mw - 16 - 20, cy = y + 13;
		boolean over = Theme.inside(mouseX, mouseY, cx, cy, cx + 20, cy + 20);
		float hv = anim("close", over, 16);
		Ui.round(g, cx, cy, 20, 20, 6, Theme.alpha(Theme.mix(0xFF1F2220, 0xFF2C302D, hv), a), Theme.alpha(Theme.mix(0x1EFFFFFF, 0x40FFFFFF, hv), a), 1);
		Ui.icon(g, "close", cx + 4, cy + 4, 12, Theme.alpha(Theme.mix(Theme.MUTED, Theme.TEXT, hv), a));
		hit(cx, cy, cx + 20, cy + 20, e -> onClose());

		if (page == Page.MODS) {
			float sw = Math.min(150, mw * 0.32f), sx = cx - 8 - sw;
			boolean typing = !query.isEmpty();
			Ui.round(g, sx, cy, sw, 20, 6, Theme.alpha(0xFF1D201E, a), Theme.alpha(typing ? Theme.COPPER : 0x1EFFFFFF, a), 1);
			Ui.icon(g, "search", sx + 6, cy + 4, 12, Theme.alpha(typing ? Theme.COPPER_HI : Theme.FAINT, a));
			boolean blink = (System.nanoTime() / 530_000_000L) % 2 == 0;
			String shown = typing ? query + (blink ? "|" : "") : "Search mods…";
			Ui.text(g, Ui.ellipsize(shown, Ui.Font.BODY, Math.round(sw - 30)), sx + 23, cy + 6.5f, Theme.alpha(typing ? Theme.TEXT : Theme.FAINT, a), Ui.Font.BODY);
		}
	}

	// ------------------------------------------------------------------ mods page

	private List<Module> filtered(Tab t) {
		String q = query.toLowerCase();
		List<Module> out = new ArrayList<>();
		for (Module m : modules().visible()) {
			boolean matches = switch (t) {
				case ALL -> true;
				case ENABLED -> m.isEnabled();
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

	private void mods(GuiGraphicsExtractor g, int mouseX, int mouseY, float mx, float y, float mw, float h, float a) {
		// tabs
		float tx = mx + 18, ty = y + 48;
		for (Tab t : Tab.values()) {
			String count = String.valueOf(filtered(t).size());
			float tw = Ui.width(t.label, Ui.Font.SEMIBOLD) + Ui.width(count, Ui.Font.BODY) + 20;
			boolean active = tab == t;
			boolean over = Theme.inside(mouseX, mouseY, tx, ty, tx + tw, ty + 18);
			float act = anim("tab:" + t, active, 14), hv = anim("tabH:" + t, over, 16);
			if (act > 0.01f) Ui.round(g, tx, ty, tw, 18, 6, Theme.alpha(Theme.COPPER, 0.18f * act * a));
			int color = Theme.mix(Theme.mix(Theme.MUTED, Theme.TEXT, hv), Theme.COPPER_HI, act);
			Ui.text(g, t.label, tx + 8, ty + 5, Theme.alpha(color, a), Ui.Font.SEMIBOLD);
			Ui.text(g, count, tx + 12 + Ui.width(t.label, Ui.Font.SEMIBOLD), ty + 5.5f, Theme.alpha(Theme.mix(Theme.FAINT, Theme.COPPER, act), a), Ui.Font.BODY);
			hit(tx, ty, tx + tw, ty + 18, e -> {
				tab = t;
				scrollTarget = 0;
			});
			tx += tw + 4;
		}

		float gx = mx + 18, gy = y + 74, gw = mw - 36, gh = h - 74 - 26;
		int cols = gw >= 460 ? 4 : 3;
		float cw = (gw - GAP * (cols - 1)) / cols;
		List<Module> list = filtered(tab);
		int rows = (list.size() + cols - 1) / cols;
		maxScroll = Math.max(0, rows * (CARD_H + GAP) - GAP - gh);
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		g.enableScissor(Math.round(gx - 4), Math.round(gy), Math.round(gx + gw + 4), Math.round(gy + gh));
		for (int i = 0; i < list.size(); i++) {
			float cx = gx + (i % cols) * (cw + GAP);
			float cy = gy + (i / cols) * (CARD_H + GAP) - scroll;
			if (cy + CARD_H < gy || cy > gy + gh) continue;
			boolean visible = mouseY >= gy && mouseY < gy + gh;
			card(g, mouseX, mouseY, list.get(i), cx, cy, cw, a, visible);
		}
		g.disableScissor();
		if (list.isEmpty()) {
			Ui.centered(g, query.isEmpty() ? "Nothing here yet" : "No mods match \"" + query + "\"", gx + gw / 2, gy + 40, Theme.alpha(Theme.MUTED, a), Ui.Font.MEDIUM);
		}
		scrollbar(g, gx + gw + 6, gy, gh, a);

		float fy = y + h - 17;
		String hint = "Click to toggle  ·  Right-click or the gear for options  ·  Type to search";
		String mc = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
		String version = "Tuff Client  ·  " + mc;
		boolean both = Ui.width(hint, Ui.Font.BODY) + 16 + Ui.width(version, Ui.Font.BODY) <= mw - 36;
		Ui.text(g, Ui.ellipsize(hint, Ui.Font.BODY, Math.round(mw - 36)), mx + 18, fy, Theme.alpha(Theme.FAINT, a), Ui.Font.BODY);
		if (both) Ui.right(g, version, mx + mw - 18, fy, Theme.alpha(Theme.FAINT, a), Ui.Font.BODY);
	}

	private void scrollbar(GuiGraphicsExtractor g, float x, float y, float h, float a) {
		if (maxScroll <= 0) return;
		float thumb = Math.max(24, h * h / (h + maxScroll));
		float ty = y + (h - thumb) * (scroll / maxScroll);
		Ui.round(g, x, ty, 3, thumb, 1.5f, Theme.alpha(0x40FFFFFF, a));
	}

	private void card(GuiGraphicsExtractor g, int mouseX, int mouseY, Module m, float x, float y, float w, float a, boolean inView) {
		boolean over = inView && Theme.inside(mouseX, mouseY, x, y, x + w, y + CARD_H);
		float hv = anim("card:" + m.id, over, 14);
		float on = anim("on:" + m.id, m.isEnabled(), 14);
		float lift = hv * 1.5f;
		int fill = Theme.mix(Theme.mix(0xFF1A1D1B, 0xFF1F2320, on), 0xFF252926, hv);
		int border = Theme.mix(0x14FFFFFF, Theme.alpha(Theme.COPPER, 0.55f), hv * 0.8f);
		Ui.round(g, x, y - lift, w, CARD_H, 9, Theme.alpha(fill, a), Theme.alpha(border, a), 1);
		Ui.icon(g, ICONS.getOrDefault(m.id, "cube"), x + w / 2 - 12, y - lift + 10, 24, Theme.alpha(Theme.mix(0xFF6B7069, Theme.COPPER, on), a));
		Ui.centered(g, Ui.ellipsize(m.name, Ui.Font.SEMIBOLD, Math.round(w - 14)), x + w / 2, y - lift + 39, Theme.alpha(Theme.TEXT, a), Ui.Font.SEMIBOLD);
		List<String> lines = wrap(m.description, Ui.Font.BODY, Math.round(w - 16));
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			String line = lines.get(i);
			if (i == 1 && lines.size() > 2) line = Ui.ellipsize(String.join(" ", lines.subList(1, lines.size())), Ui.Font.BODY, Math.round(w - 16));
			Ui.centered(g, line, x + w / 2, y - lift + 51 + i * 10, Theme.alpha(Theme.MUTED, a), Ui.Font.BODY);
		}
		g.fill(Math.round(x + 9), Math.round(y - lift + 72), Math.round(x + w - 9), Math.round(y - lift + 73), Theme.alpha(0x14FFFFFF, a));
		boolean hasSettings = !m.settings().isEmpty();
		if (hasSettings) {
			float gx = x + 9, gy = y - lift + 76;
			boolean gearOver = inView && Theme.inside(mouseX, mouseY, gx - 3, gy - 2, gx + 15, gy + 14);
			Ui.icon(g, "gear", gx, gy, 12, Theme.alpha(gearOver ? Theme.COPPER_HI : Theme.mix(Theme.FAINT, Theme.MUTED, hv), a));
			if (inView) hit(gx - 3, gy - 2, gx + 15, gy + 14, e -> {
				click();
				openSettings(m);
			});
		}
		Ui.toggle(g, x + w - 9 - 22, y - lift + 76, on, a);
		if (inView) hit(x, y, x + w, y + CARD_H, e -> {
			click();
			if (e.button() == 1 && hasSettings) openSettings(m);
			else m.toggle();
		});
	}

	// ------------------------------------------------------------------ settings drawer

	private void drawer(GuiGraphicsExtractor g, int mouseX, int mouseY, float x, float y, float w, float h, float a) {
		Module m = drawer;
		float dw = Math.min(210, (w - SIDEBAR) * 0.55f);
		float ease = 1 - (1 - drawerAnim) * (1 - drawerAnim);
		float dx = x + w - dw * ease;
		g.fill(Math.round(x + SIDEBAR + 1), Math.round(y + 1), Math.round(x + w - 1), Math.round(y + h - 1), Theme.alpha(0x66000000, ease * a));
		hit(x + SIDEBAR, y, dx, y + h, e -> drawer = null);
		g.enableScissor(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
		Ui.shadow(g, dx, y, dw, h, 16, 0.5f * ease);
		Ui.round(g, dx, y, dw + 12, h, 12, Theme.alpha(0xFF1A1D1B, a), Theme.alpha(0x22FFFFFF, a), 1);
		hit(dx, y, x + w, y + h, e -> { });

		float px = dx + 14, pw = dw - 28;
		float cy = y + 14;
		Ui.icon(g, ICONS.getOrDefault(m.id, "cube"), px, cy + 1, 12, Theme.alpha(Theme.COPPER, a));
		Ui.text(g, Ui.ellipsize(m.name, Ui.Font.BOLD, Math.round(pw - 40)), px + 18, cy + 2, Theme.alpha(Theme.TEXT, a), Ui.Font.BOLD);
		float bx = dx + dw - 14 - 16;
		boolean closeOver = Theme.inside(mouseX, mouseY, bx, cy - 2, bx + 16, cy + 14);
		Ui.icon(g, "close", bx + 2, cy, 12, Theme.alpha(closeOver ? Theme.TEXT : Theme.MUTED, a));
		hit(bx, cy - 2, bx + 16, cy + 14, e -> drawer = null);
		cy += 20;
		for (String line : wrap(m.description, Ui.Font.BODY, Math.round(pw))) {
			Ui.text(g, line, px, cy, Theme.alpha(Theme.MUTED, a), Ui.Font.BODY);
			cy += 10;
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
		Ui.round(g, x, y, w, 26, 7, Theme.alpha(0xFF222623, a));
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
			Ui.round(g, cx, cy, pw, 17, 6, Theme.alpha(Theme.mix(over ? 0xFF2C302D : 0xFF222623, Theme.alpha(Theme.COPPER, 0.9f), act), a));
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
		Ui.round(g, x + 2, ty, w - 4, 4, 2, Theme.alpha(0xFF2F3430, a));
		Ui.round(g, x + 2, ty, Math.max(4, (w - 4) * frac), 4, 2, Theme.alpha(Theme.COPPER, a));
		float kx = x + 2 + (w - 4) * frac;
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
		int cols = 6;
		float sw = (pw - 6 * (cols - 1)) / cols;
		for (int i = 0; i < accents.length; i++) {
			Theme.Accent acc = accents[i];
			float sx = px + (i % cols) * (sw + 6), sy = cy + (i / cols) * 52;
			boolean selected = modules().ui.accent.get() == i;
			boolean over = Theme.inside(mouseX, mouseY, sx, sy, sx + sw, sy + 46);
			float act = anim("acc:" + i, selected, 14), hv = anim("accH:" + i, over, 16);
			Ui.round(g, sx, sy, sw, 46, 9, Theme.alpha(Theme.mix(0xFF1B1E1C, 0xFF232724, hv), a), Theme.alpha(Theme.mix(0x14FFFFFF, acc.base, act), a), 1 + act * 0.5f);
			Ui.circle(g, sx + sw / 2, sy + 17, 9, Theme.alpha(acc.base, a));
			Ui.round(g, sx + sw / 2 - 6, sy + 10, 12, 5, 2.5f, Theme.alpha(0x40FFFFFF, a));
			if (act > 0.05f) Ui.icon(g, "check", sx + sw / 2 - 6, sy + 11, 12, Theme.alpha(0xFFFFFFFF, act * a));
			Ui.centered(g, acc.label, sx + sw / 2, sy + 32, Theme.alpha(Theme.mix(Theme.MUTED, Theme.TEXT, act), a), Ui.Font.MEDIUM);
			final int index = i;
			hit(sx, sy, sx + sw, sy + 46, e -> {
				click();
				modules().ui.accent.set(index);
				modules().ui.applyAccent();
			});
		}
		cy += ((accents.length + cols - 1) / cols) * 52 + 10;
		cy = optionRow(g, mouseX, mouseY, px, cy, pw, "Background blur", "Blur the game behind Tuff menus.", modules().ui.blur.get(), modules().ui.blur::toggle, a);
		cy = optionRow(g, mouseX, mouseY, px, cy, pw, "Menu animations", "Smooth transitions, hovers and scrolling.", modules().ui.animations.get(), modules().ui.animations::toggle, a);
	}

	private float optionRow(GuiGraphicsExtractor g, int mouseX, int mouseY, float x, float y, float w, String label, String hint, boolean value, Runnable toggle, float a) {
		boolean over = Theme.inside(mouseX, mouseY, x, y, x + w, y + 34);
		float hv = anim("opt:" + label, over, 16);
		Ui.round(g, x, y, w, 34, 9, Theme.alpha(Theme.mix(0xFF1B1E1C, 0xFF212522, hv), a), Theme.alpha(0x12FFFFFF, a), 1);
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
		Ui.round(g, x, y, w, 34, 9, Theme.alpha(0xFF1B1E1C, a), Theme.alpha(0x12FFFFFF, a), 1);
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
		cy = optionRow(g, mouseX, mouseY, px, cy, pw, "Tuff title screen", "Use the Tuff main menu instead of the vanilla one.", title.isEnabled(), title::toggle, a);
		cy = optionRow(g, mouseX, mouseY, px, cy, pw, "Animated background", "Parallax, glowing bulbs and embers on the title screen.", title.animate.get(), title.animate::toggle, a);
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
		Ui.round(g, x, y, w, 28, 8, Theme.alpha(Theme.mix(0xFF1B1E1C, 0xFF222623, hv), a), Theme.alpha(enabled ? Theme.alpha(Theme.COPPER, 0.35f) : 0x12FFFFFF, a), 1);
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
			if (index < enabledPacks.size() - 1) iconButton(g, mouseX, mouseY, "chevron", bx - 15, y + 7, a, top, bottom, () -> {
				java.util.Collections.swap(enabledPacks, index, index + 1);
				packsDirty = true;
			});
		}
	}

	private void iconButton(GuiGraphicsExtractor g, int mouseX, int mouseY, String icon, float x, float y, float a, float top, float bottom, Runnable action) {
		boolean over = Theme.inside(mouseX, mouseY, x - 2, y - 2, x + 14, y + 14) && mouseY >= top && mouseY < bottom;
		if (over) Ui.round(g, x - 2, y - 2, 16, 16, 4, Theme.alpha(0x22FFFFFF, a));
		Ui.icon(g, icon, x, y, 12, Theme.alpha(over ? Theme.COPPER_HI : Theme.MUTED, a));
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
		if (drawer != null && mouseX / zoom() > wx() + ww() - 210) {
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
