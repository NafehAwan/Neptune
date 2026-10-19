package net.tuffclient.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
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
import net.tuffclient.setting.ColorSetting;
import net.tuffclient.setting.NumberSetting;
import net.tuffclient.setting.Setting;
import net.tuffclient.setting.TextSetting;

/**
 * The Glade mod menu, drawn to the Grove Glass mockup: one frosted panel with
 * the leaf, the page title in Fraunces, filter chips and search, then a
 * two-column list of mods with an icon tile, name, short description and a
 * switch. A footer row reaches the Theme, Packs and Settings pages and the HUD
 * editor. Laid out in the mockup's 1280x720 space (see {@link Ui#mockup}).
 */
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
		ALL("All"), HUD("HUD"), UTILITY("Utility"), VISUAL("Visual"), FAVORITES("Favorites");

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
			Map.entry("hide_scoreboard", "scoreboard"), Map.entry("title_screen", "image"),
			Map.entry("compass", "compass"), Map.entry("biome_light", "biome"), Map.entry("item_counter", "backpack"),
			Map.entry("durability", "alert"), Map.entry("session_stats", "stats"), Map.entry("pack_display", "package"),
			Map.entry("day_time", "daytime"), Map.entry("waypoints", "flag"), Map.entry("screenshot_tools", "camera"),
			Map.entry("chat_plus", "chat"), Map.entry("auto_gg", "trophy"), Map.entry("auto_reconnect", "refresh"),
			Map.entry("freelook", "freelook"), Map.entry("clean_f3", "bug"), Map.entry("hit_color", "droplet"),
			Map.entry("block_outline", "box"), Map.entry("particles", "sparkles"), Map.entry("custom_sky", "sky"),
			Map.entry("nametags", "tag"), Map.entry("cosmetics", "award"), Map.entry("hit_sounds", "bell"),
			Map.entry("item_physics", "box"), Map.entry("old_animations", "sword"), Map.entry("low_fire", "flame"),
			Map.entry("small_shield", "shield"), Map.entry("hand_view", "hand"), Map.entry("time_weather", "daytime"));
	/** The short line under each mod name. */
	private static final Map<String, String> BLURBS = Map.ofEntries(
			Map.entry("zoom", "Hold C to zoom in"), Map.entry("fullbright", "See clearly at night"),
			Map.entry("crosshair", "Your own crosshair"), Map.entry("toggle_sprint", "Always sprint"),
			Map.entry("keystrokes", "Show WASD and clicks"), Map.entry("fps", "Frames per second"),
			Map.entry("cps", "Clicks per second"), Map.entry("armor", "Armor durability"),
			Map.entry("coordinates", "Position and facing"), Map.entry("ping", "Server latency"),
			Map.entry("effects", "Active effects"), Map.entry("clock", "Real-world time"),
			Map.entry("server", "Server address"), Map.entry("speed", "Movement speed"),
			Map.entry("memory", "Memory in use"), Map.entry("hide_scoreboard", "Hide the sidebar scores"),
			Map.entry("title_screen", "The Glade main menu"),
			Map.entry("compass", "Heading strip at the top"), Map.entry("biome_light", "Biome and light level"),
			Map.entry("item_counter", "Arrows, totems, pearls"), Map.entry("durability", "Warns before things break"),
			Map.entry("session_stats", "Time, kills, blocks mined"), Map.entry("pack_display", "Your resource pack"),
			Map.entry("day_time", "Day count and time"), Map.entry("waypoints", "Save places, press B"),
			Map.entry("screenshot_tools", "Open, copy or delete after F2"), Map.entry("chat_plus", "Timestamps, merge, copy"),
			Map.entry("auto_gg", "Says gg when a game ends"), Map.entry("auto_reconnect", "Rejoin after a disconnect"),
			Map.entry("freelook", "Hold Alt to look around"), Map.entry("clean_f3", "A tidy F3 screen"),
			Map.entry("hit_color", "Damage flash colour"), Map.entry("block_outline", "Outline colour and width"),
			Map.entry("particles", "More hit particles"), Map.entry("custom_sky", "Tint the sky and fog"),
			Map.entry("nametags", "Own nametag, backgrounds"), Map.entry("cosmetics", "Leaf badge and cape"),
			Map.entry("hit_sounds", "A sound when you hit"), Map.entry("item_physics", "Dropped items lie flat"),
			Map.entry("old_animations", "1.7 style animations"), Map.entry("low_fire", "Lower fire on screen"),
			Map.entry("small_shield", "Smaller shield and totem"), Map.entry("hand_view", "Move and scale your hand"),
			Map.entry("time_weather", "Your own time and weather"));
	/** List order, as in the mockup; anything else follows. */
	private static final List<String> ORDER = List.of("zoom", "fullbright", "crosshair", "toggle_sprint", "freelook", "waypoints", "keystrokes", "fps", "cps",
			"armor", "coordinates", "compass", "durability", "item_counter", "biome_light", "day_time", "session_stats", "ping", "effects", "clock",
			"server", "speed", "memory", "pack_display", "chat_plus", "screenshot_tools", "clean_f3", "auto_gg", "auto_reconnect", "hit_color",
			"block_outline", "particles", "hit_sounds", "custom_sky", "time_weather", "item_physics", "old_animations", "hand_view", "small_shield",
			"low_fire", "nametags", "cosmetics", "hide_scoreboard", "title_screen");

	// the panel: 940x604, centred, radius 26, padding 26 30
	private static final float PW = 940, PH = 604, PY = (720 - PH) / 2, PAD_X = 30, PAD_Y = 26;
	private static final float ROW = 66, GUTTER = 26, FOOT = 44;

	private final Screen parent;
	private final GroveBackground background = new GroveBackground();
	private final List<Hit> hits = new ArrayList<>();
	private final Map<String, Float> anim = new HashMap<>();
	private final Map<String, float[]> switches = new HashMap<>();
	private final Map<String, float[]> hearts = new HashMap<>();
	private Page page;
	private Tab tab = Tab.ALL;
	private String query = "";
	private float scroll;
	private float scrollTarget;
	private float maxScroll;
	private Module drawer;
	private float drawerAnim;
	private float drawerScroll;
	private float drawerMax;
	private float drawerLeft;
	private NumberSetting dragging;
	private float dragX1;
	private float dragX2;
	/** What a mouse drag moves (a slider, the colour wheel or a colour bar), in mockup space. */
	private BiConsumer<Double, Double> drag;
	/** The colour setting whose picker is open in the drawer. */
	private ColorSetting openColor;
	/** Hue, saturation and value being edited, kept so the hue survives greys and black. */
	private final Map<ColorSetting, float[]> hsv = new HashMap<>();
	/** The text field being typed into, if any, and the hex field of a colour. */
	private TextSetting focusedText;
	private ColorSetting editingHex;
	private String hexBuffer = "";
	private double clickY;
	private float open;
	private float pageFade = 1;
	private float dt;
	private long last = System.nanoTime();
	private List<String> enabledPacks;
	private List<String> availablePacks;
	private boolean packsDirty;
	private double clickX;
	private final Map<String, float[]> springs = new HashMap<>();
	/** Seconds since the list was last refilled; rows rise in one after another. */
	private float listAge = 10;
	private float chipX = -1, chipW, footX = -1, footW;

	public TuffMenuScreen(Screen parent) {
		this(parent, Page.MODS);
	}

	public TuffMenuScreen(Screen parent, Page page) {
		super(Component.literal("Glade"));
		this.parent = parent;
		this.page = page;
	}

	@Override
	protected void init() {
		Ui.clearTextCache();
		listAge = 0;
	}

	public void setPage(Page page) {
		if (this.page == page) return;
		this.page = page;
		listAge = 0;
		drawer = null;
		scroll = scrollTarget = 0;
		pageFade = 0;
		if (page == Page.PACKS) loadPacks();
	}

	/** Window-pixel centre of a mod's switch as last drawn, or null (used by tests). */
	public double[] switchPosition(String moduleId) {
		return toWindow(switches.get(moduleId));
	}

	/** Window-pixel centre of a mod's heart as last laid out, or null (used by tests). */
	public double[] heartPosition(String moduleId) {
		return toWindow(hearts.get(moduleId));
	}

	private double[] toWindow(float[] p) {
		if (p == null) return null;
		double k = zoom() * minecraft.getWindow().getGuiScale();
		return new double[] {p[0] * k, p[1] * k};
	}

	/** The menu has a single list layout now; kept so callers keep working. */
	public void setListView(boolean list) {
	}

	public void openSettings(Module module) {
		drawer = module;
		drawerScroll = 0;
		openColor = null;
	}

	/** Opens a module's settings with one colour's picker open (used by tests). */
	public void openColorPicker(Module module, String settingId) {
		openSettings(module);
		for (Setting<?> s : module.settings()) {
			if (s instanceof ColorSetting c && s.id.equals(settingId)) openColor = c;
		}
	}

	/** Scrolls the settings drawer (used by tests); clamped once drawn. */
	public void scrollDrawer(float amount) {
		drawerScroll = amount;
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

	/** A springy 0..1 value for switches; it overshoots a touch when flipped. */
	private float spring(String key, boolean on) {
		float target = on ? 1 : 0;
		float[] st = springs.computeIfAbsent(key, k -> new float[] {target, 0});
		if (!animations()) {
			st[0] = target;
			st[1] = 0;
			return target;
		}
		return Theme.spring(st, target, dt);
	}

	/** How far row {@code i} has risen into place (0..1), for the staggered list entrance. */
	private float rise(int i) {
		if (!animations()) return 1;
		float t = Math.max(0, Math.min(1, (listAge - i * 0.03f) / 0.32f));
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}

	private void click() {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	private void hit(float x1, float y1, float x2, float y2, Consumer<MouseButtonEvent> action) {
		hits.add(new Hit(x1, y1, x2, y2, action));
	}

	// ------------------------------------------------------------------ layout

	/** GUI units per mockup pixel. */
	private float zoom() {
		return Ui.mockup(height);
	}

	private float vw() {
		return width / zoom();
	}

	private float px() {
		return (vw() - PW) / 2;
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Gfx g = Gfx.of(graphics);
		if (minecraft.level == null) background.render(g, width, height, modules().titleScreen.animate.get());
		if (modules().ui.blur.get()) {
			// the forest has to sit in its own layer for the blur to reach it
			g.nextStratum();
			extractBlurredBackground(graphics);
			g.nextStratum();
			g.fill(0, 0, width, height, 0x0FFFFFFF);
		} else {
			g.fill(0, 0, width, height, 0x59EEF1EB);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int guiMouseX, int guiMouseY, float partialTick) {
		Gfx g = Gfx.of(graphics);
		float z = zoom();
		float mouseX = guiMouseX / z, mouseY = guiMouseY / z;
		long now = System.nanoTime();
		dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		open = animations() ? Theme.approach(open, 1, dt, 12) : 1;
		pageFade = animations() ? Theme.approach(pageFade, 1, dt, 14) : 1;
		scroll = animations() ? Theme.approach(scroll, scrollTarget, dt, 18) : scrollTarget;
		drawerAnim = Theme.approach(drawerAnim, drawer != null ? 1 : 0, dt, animations() ? 16 : 1000);
		listAge += dt;
		hits.clear();

		float s = 0.97f + 0.03f * open;
		Ui.zoom = z;
		g.push();
		g.scale(z, z);
		g.translate(vw() / 2f, 360);
		g.scale(s, s);
		g.translate(-vw() / 2f, -360);
		float a = open;

		float x = px(), y = PY;
		Ui.frost(g, x, y, PW, PH, 26, 0x85FFFFFF, 0.18f, a);
		hit(x, y, x + PW, y + PH, e -> { });

		header(g, mouseX, mouseY, x, y, a);
		float pa = a * pageFade, slide = (1 - pageFade) * 6;
		float top = y + PAD_Y + 34 + 20 + slide, bottom = y + PH - PAD_Y - FOOT;
		switch (page) {
			case MODS -> mods(g, mouseX, mouseY, x + PAD_X, top, PW - 2 * PAD_X, bottom - top, pa);
			case THEME -> theme(g, mouseX, mouseY, x + PAD_X, top, PW - 2 * PAD_X, pa);
			case PACKS -> packs(g, mouseX, mouseY, x + PAD_X, top, PW - 2 * PAD_X, bottom - top, pa);
			case SETTINGS -> settings(g, mouseX, mouseY, x + PAD_X, top, PW - 2 * PAD_X, pa);
		}
		footer(g, mouseX, mouseY, x + PAD_X, y + PH - PAD_Y - 32, PW - 2 * PAD_X, a);
		if (drawerAnim > 0.01f && drawer != null) drawer(g, mouseX, mouseY, x, y, a);
		else if (drawerAnim > 0.01f) drawerAnim = 0;

		g.pop();
		Ui.zoom = 1f;
	}

	/** Leaf, page title in Fraunces italic, then (on Mods) the filter chips and search. */
	private void header(Gfx g, float mouseX, float mouseY, float x, float y, float a) {
		float cy = y + PAD_Y + 17;
		float lx = x + PAD_X;
		Ui.image(g, EMBLEM, lx, cy - 16, 32, 32, 512, 512, Theme.alpha(0xFFFFFFFF, a));
		float tx = lx + 32 + 14;
		Ui.textAt(g, page.label, tx, cy - 30 * 1.233f / 2 + 30 * 0.978f, Theme.alpha(Theme.TEXT, a), Ui.Font.HEAD_30);
		float after = tx + Ui.width(page.label, Ui.Font.HEAD_30);
		if (page != Page.MODS) {
			String subtitle = switch (page) {
				case THEME -> "Accent colour and menu style";
				case PACKS -> "Resource packs, top of the list wins";
				default -> "Title screen, keybinds and HUD";
			};
			Ui.textAt(g, subtitle, after + 18, Ui.mid(cy, 12.5f), Theme.alpha(Theme.MUTED, a), Ui.Font.UI_12_5);
			return;
		}

		// chips: 28 tall, 12px padding, 4px apart; counts in Geist Mono
		float cx = after + 14 + 18;
		// the selected chip's pill slides between chips
		float targetX = 0, targetW = 0, scanX = cx;
		for (Tab t : Tab.values()) {
			String count = t == Tab.FAVORITES ? "" : String.valueOf(filtered(t, "").size());
			float w = 12 + Ui.width(t.label, Ui.Font.UI_12_5_M) + (count.isEmpty() ? 0 : 6 + Ui.width(count, Ui.Font.MONO_11_M)) + 12;
			if (t == tab) {
				targetX = scanX;
				targetW = w;
			}
			scanX += w + 4;
		}
		if (chipX < 0 || !animations()) {
			chipX = targetX;
			chipW = targetW;
		}
		chipX = Theme.approach(chipX, targetX, dt, 16);
		chipW = Theme.approach(chipW, targetW, dt, 16);
		Ui.shadowOutside(g, chipX, cy - 14, chipW, 28, 8, 0.08f * a);
		Ui.round(g, chipX, cy - 14, chipW, 28, 14, Theme.alpha(0xC8FFFFFF, a));
		for (Tab t : Tab.values()) {
			String count = t == Tab.FAVORITES ? "" : String.valueOf(filtered(t, "").size());
			float w = 12 + Ui.width(t.label, Ui.Font.UI_12_5_M) + (count.isEmpty() ? 0 : 6 + Ui.width(count, Ui.Font.MONO_11_M)) + 12;
			boolean active = tab == t;
			boolean over = Theme.inside(mouseX, mouseY, cx, cy - 14, cx + w, cy + 14);
			float act = anim("chip:" + t, active, 14), hv = anim("chipH:" + t, over, 16);
			if (hv > 0.01f && !active) Ui.round(g, cx, cy - 14, w, 28, 14, Theme.alpha(0x66FFFFFF, hv * a));
			int color = Theme.mix(Theme.MUTED, Theme.COPPER, act);
			Ui.textAt(g, t.label, cx + 12, Ui.mid(cy, 12.5f), Theme.alpha(color, a), Ui.Font.UI_12_5_M);
			if (!count.isEmpty()) Ui.textAt(g, count, cx + 12 + Ui.width(t.label, Ui.Font.UI_12_5_M) + 6, Ui.mid(cy, 11), Theme.alpha(color, 0.6f * a), Ui.Font.MONO_11_M);
			hit(cx, cy - 14, cx + w, cy + 14, e -> {
				click();
				if (tab != t) listAge = 0;
				tab = t;
				scrollTarget = 0;
			});
			cx += w + 4;
		}

		// search: 230 wide, 34 tall
		float sx = x + PW - PAD_X - 230, sy = cy - 17;
		boolean typing = !query.isEmpty();
		Ui.round(g, sx, sy, 230, 34, 10, Theme.alpha(0x80FFFFFF, a), Theme.alpha(typing ? Theme.alpha(Theme.COPPER, 0.5f) : 0xCCFFFFFF, a), 1);
		Ui.lucide(g, "search", sx + 12, cy - 7, 14, Theme.alpha(Theme.MUTED, 0.8f * a));
		boolean blink = (System.nanoTime() / 530_000_000L) % 2 == 0;
		String shown = typing ? query + (blink ? "|" : "") : "Search mods";
		Ui.textAt(g, Ui.ellipsize(shown, Ui.Font.UI_12_5, 180), sx + 34, Ui.mid(cy, 12.5f), Theme.alpha(typing ? Theme.TEXT : Theme.MUTED, a), Ui.Font.UI_12_5);
	}

	/** Bottom row: how many mods are on, and buttons for the other pages and the HUD editor. */
	private void footer(Gfx g, float mouseX, float mouseY, float x, float y, float w, float a) {
		Ui.round(g, x, y - 12, w, 1, 0.5f, Theme.alpha(0x99FFFFFF, a));
		long on = modules().visible().stream().filter(Module::isEnabled).count();
		Ui.textAt(g, on + " of " + modules().visible().size() + " on", x, Ui.mid(y + 16, 11.5f), Theme.alpha(Theme.MUTED, a), Ui.Font.MONO_11_5);
		float bx = x + w;
		String[][] items = {{"hud", "Edit HUD", "move"}, {"SETTINGS", "Settings", "settings"}, {"PACKS", "Packs", "package"}, {"THEME", "Theme", "palette"}, {"MODS", "Mods", "grid"}};
		float scan = x + w, tx = 0, tw = 0;
		for (String[] item : items) {
			float bw = 12 + 16 + 8 + Ui.width(item[1], Ui.Font.UI_12_5_M) + 12;
			scan -= bw;
			if (page.name().equals(item[0])) {
				tx = scan;
				tw = bw;
			}
			scan -= 4;
		}
		if (footX < 0 || !animations()) {
			footX = tx;
			footW = tw;
		}
		footX = Theme.approach(footX, tx, dt, 16);
		footW = Theme.approach(footW, tw, dt, 16);
		Ui.shadowOutside(g, footX, y, footW, 32, 8, 0.08f * a);
		Ui.round(g, footX, y, footW, 32, 10, Theme.alpha(0xC8FFFFFF, a));
		for (String[] item : items) {
			float bw = 12 + 16 + 8 + Ui.width(item[1], Ui.Font.UI_12_5_M) + 12;
			bx -= bw;
			boolean active = !item[0].equals("hud") && page.name().equals(item[0]);
			boolean over = Theme.inside(mouseX, mouseY, bx, y, bx + bw, y + 32);
			float act = anim("foot:" + item[0], active, 14), hv = anim("footH:" + item[0], over, 16);
			if (hv > 0.01f && !active) Ui.round(g, bx, y, bw, 32, 10, Theme.alpha(0x66FFFFFF, hv * a));
			int color = Theme.mix(Theme.mix(Theme.MUTED, Theme.TEXT, hv), Theme.COPPER, act);
			Ui.lucide(g, item[2], bx + 12, y + 8, 16, Theme.alpha(color, a));
			Ui.textAt(g, item[1], bx + 12 + 16 + 8, Ui.mid(y + 16, 12.5f), Theme.alpha(color, a), Ui.Font.UI_12_5_M);
			hit(bx, y, bx + bw, y + 32, e -> {
				click();
				if (item[0].equals("hud")) minecraft.gui.setScreen(new HudEditorScreen(this));
				else setPage(Page.valueOf(item[0]));
			});
			bx -= 4;
		}
	}

	// ------------------------------------------------------------------ mods page

	private List<Module> filtered(Tab t, String q) {
		String query = q.toLowerCase();
		List<Module> out = new ArrayList<>();
		for (Module m : modules().visible()) {
			boolean matches = switch (t) {
				case ALL -> true;
				case FAVORITES -> m.favorite;
				case HUD -> m.category == Category.HUD;
				case UTILITY -> m.category == Category.UTILITY;
				case VISUAL -> m.category == Category.VISUAL;
			};
			if (!matches) continue;
			if (!query.isEmpty() && !m.name.toLowerCase().contains(query) && !m.description.toLowerCase().contains(query)) continue;
			out.add(m);
		}
		out.sort((p, q2) -> Integer.compare(order(p), order(q2)));
		return out;
	}

	private static int order(Module m) {
		int i = ORDER.indexOf(m.id);
		return i < 0 ? ORDER.size() : i;
	}

	private void mods(Gfx g, float mouseX, float mouseY, float x, float y, float w, float h, float a) {
		List<Module> list = filtered(tab, query);
		float cw = (w - GUTTER) / 2;
		int rows = (list.size() + 1) / 2;
		maxScroll = Math.max(0, rows * ROW - h);
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		boolean inList = mouseY >= y && mouseY < y + h && drawer == null;
		g.enableScissor(Math.round(x - 10), Math.round(y), Math.round(x + w + 10), Math.round(y + h));
		for (int i = 0; i < list.size(); i++) {
			float rx = x + (i % 2) * (cw + GUTTER);
			float ry = y + (i / 2) * ROW - scroll;
			if (ry + ROW < y || ry > y + h) continue;
			float in = rise(i);
			row(g, mouseX, mouseY, list.get(i), rx, ry + (1 - in) * 12, cw, a * in, inList && in > 0.95f, y, y + h);
		}
		g.disableScissor();
		if (list.isEmpty()) {
			String empty = tab == Tab.FAVORITES ? "No favorites yet. Hover a mod and click its heart." : query.isEmpty() ? "Nothing here yet" : "No mods match \"" + query + "\"";
			Ui.centeredAt(g, empty, x + w / 2, y + 60, Theme.alpha(Theme.MUTED, a), Ui.Font.UI_14);
		}
		if (maxScroll > 0) {
			float thumb = Math.max(40, h * h / (h + maxScroll));
			float ty = y + (h - thumb) * (scroll / maxScroll);
			Ui.round(g, x + w + 12, ty, 3, thumb, 1.5f, Theme.alpha(0x401F2A22, a));
		}
	}

	/** One mod: 66 tall, a 40px icon tile, name and blurb, a heart and gear on hover, and the switch. */
	private void row(Gfx g, float mouseX, float mouseY, Module m, float x, float y, float w, float a, boolean inView, float clipTop, float clipBottom) {
		boolean over = inView && Theme.inside(mouseX, mouseY, x, y, x + w, y + ROW);
		float hv = anim("row:" + m.id, over, 14);
		float on = anim("on:" + m.id, m.isEnabled(), 12);
		boolean visible = y + ROW > clipTop && y < clipBottom;
		if (hv > 0.01f) Ui.round(g, x - 8, y + 4, w + 16, ROW - 8, 12, Theme.alpha(0x40FFFFFF, hv * a));
		Ui.round(g, x, y + ROW - 1, w, 1, 0.5f, Theme.alpha(0x99FFFFFF, a));
		if (visible) hit(x, Math.max(y, clipTop), x + w, Math.min(y + ROW, clipBottom), e -> {
			click();
			if (e.button() == 1 && !m.settings().isEmpty()) openSettings(m);
			else m.toggle();
		});

		Ui.round(g, x, y + 13, 40, 40, 12, Theme.alpha(Theme.mix(0x80FFFFFF, 0xD9E4EFE6, on), a));
		Ui.lucide(g, ICONS.getOrDefault(m.id, "grid"), x + 12, y + 25, 16, Theme.alpha(Theme.mix(0xFF9AA39B, Theme.COPPER, on), a));
		float tx = x + 54;
		Ui.textAt(g, Ui.ellipsize(m.name, Ui.Font.UI_14_SB, (int) (w - 54 - 110)), tx, y + 16.6f + 14 * 1.038f, Theme.alpha(Theme.TEXT, a), Ui.Font.UI_14_SB);
		String blurb = BLURBS.getOrDefault(m.id, m.description);
		Ui.textAt(g, Ui.ellipsize(blurb, Ui.Font.UI_12, (int) (w - 54 - 110)), tx, y + 16.6f + 17.64f + 12 * 1.038f, Theme.alpha(Theme.MUTED, a), Ui.Font.UI_12);

		// switch: 34x20
		float sx = x + w - 34, sy = y + 23;
		float knob = spring("sw:" + m.id, m.isEnabled());
		Ui.round(g, sx, sy, 34, 20, 10, Theme.alpha(Theme.mix(0x99FFFFFF, Theme.COPPER, on), a), Theme.alpha(Theme.mix(0xCCFFFFFF, Theme.COPPER, on), a), 1);
		// the knob stretches a little while it travels
		float stretch = Math.min(4, Math.abs(springs.get("sw:" + m.id)[1]) * 0.02f);
		Ui.round(g, sx + 3 + 14 * knob - stretch / 2, sy + 3, 14 + stretch, 14, 7, Theme.alpha(Theme.mix(Theme.MUTED, 0xFFFFFFFF, on), a));
		switches.put(m.id, new float[] {sx + 17, sy + 10});

		// heart (and gear when there are settings), shown on hover or when favorited
		float hx = sx - 30, hy = y + 26;
		hearts.put(m.id, new float[] {hx + 7, hy + 7});
		boolean overHeart = inView && Theme.inside(mouseX, mouseY, hx - 4, hy - 4, hx + 18, hy + 18);
		float heartA = Math.max(hv, m.favorite ? 1 : 0);
		if (heartA > 0.01f) Ui.lucide(g, m.favorite ? "heart_fill" : "heart", hx, hy, 14, Theme.alpha(m.favorite ? Theme.COPPER : overHeart ? Theme.TEXT : Theme.MUTED, heartA * a));
		if (visible && hy - 4 >= clipTop && hy + 18 <= clipBottom) hit(hx - 4, hy - 4, hx + 18, hy + 18, e -> {
			click();
			m.favorite = !m.favorite;
			TuffConfig.save(modules());
		});
		if (!m.settings().isEmpty() && hv > 0.3f) {
			float gx = hx - 26;
			boolean overGear = Theme.inside(mouseX, mouseY, gx - 4, hy - 4, gx + 18, hy + 18);
			Ui.lucide(g, "settings", gx, hy, 14, Theme.alpha(overGear ? Theme.TEXT : Theme.MUTED, hv * a));
			if (visible) hit(gx - 4, hy - 4, gx + 18, hy + 18, e -> {
				click();
				openSettings(m);
			});
		}
	}

	// ------------------------------------------------------------------ settings drawer

	private void drawer(Gfx g, float mouseX, float mouseY, float x, float y, float a) {
		Module m = drawer;
		float ease = 1 - (1 - drawerAnim) * (1 - drawerAnim);
		float dw = 320;
		float dx = x + PW - 14 - dw * ease, dy = y + 14, dh = PH - 28;
		drawerLeft = dx;
		Ui.round(g, x, y, PW, PH, 26, Theme.alpha(0x73F6F4EE, ease * a));
		hit(x, y, x + PW, y + PH, e -> drawer = null);
		Ui.frost(g, dx, dy, dw, dh, 20, 0xF2FBFBF8, 0.2f, a * ease);
		hit(dx, dy, dx + dw, dy + dh, e -> { });

		float px = dx + 22, pw = dw - 44;
		float cy = dy + 24;
		Ui.round(g, px, cy, 40, 40, 12, Theme.alpha(0xD9E4EFE6, a));
		Ui.lucide(g, ICONS.getOrDefault(m.id, "grid"), px + 12, cy + 12, 16, Theme.alpha(Theme.COPPER, a));
		Ui.textAt(g, Ui.ellipsize(m.name, Ui.Font.HEAD_22, (int) (pw - 90)), px + 54, Ui.mid(cy + 20, 22) - 2, Theme.alpha(Theme.TEXT, a), Ui.Font.HEAD_22);
		float bx = dx + dw - 22 - 20;
		boolean closeOver = Theme.inside(mouseX, mouseY, bx - 4, cy + 6, bx + 24, cy + 34);
		Ui.lucide(g, "close", bx, cy + 10, 20, Theme.alpha(closeOver ? Theme.TEXT : Theme.MUTED, a));
		hit(bx - 4, cy + 6, bx + 24, cy + 34, e -> drawer = null);
		cy += 56;
		for (String line : wrap(m.description, Ui.Font.UI_12_5, (int) pw)) {
			Ui.textAt(g, line, px, cy + 12.5f * 1.038f, Theme.alpha(Theme.MUTED, a), Ui.Font.UI_12_5);
			cy += 12.5f * 1.4f;
		}
		cy += 14;

		float top = cy, bottom = dy + dh - 18;
		g.enableScissor(Math.round(dx), Math.round(top), Math.round(dx + dw), Math.round(bottom));
		cy -= drawerScroll;
		cy = toggleRow(g, px, cy, pw, "Enabled", m.isEnabled(), m::toggle, "drawer:" + m.id, a, top, bottom);
		HudModule hud = m instanceof HudModule h ? h : null;
		for (Setting<?> setting : m.settings()) {
			if (hud != null && hud.isStyle(setting) || !setting.visible()) continue;
			cy = setting(g, mouseX, mouseY, px, cy, pw, m, setting, a, top, bottom);
		}
		cy = extras(g, mouseX, mouseY, px, cy, pw, m, a, top, bottom);
		if (hud != null) {
			cy += 18;
			Ui.spaced(g, "STYLE", px, cy + 10.5f * 1.005f - 7, Theme.alpha(Theme.MUTED, a), Ui.Font.MONO_10_5, 1.26f);
			cy += 14;
			cy = looks(g, mouseX, mouseY, px, cy, pw, hud, a, top, bottom);
			for (Setting<?> setting : m.settings()) {
				if (!hud.isStyle(setting) || !setting.visible()) continue;
				cy = setting(g, mouseX, mouseY, px, cy, pw, m, setting, a, top, bottom);
			}
		}
		drawerMax = Math.max(0, cy + drawerScroll - top - (bottom - top));
		drawerScroll = Math.min(drawerScroll, drawerMax);
		g.disableScissor();
	}

	private float setting(Gfx g, float mouseX, float mouseY, float px, float cy, float pw, Module m, Setting<?> setting, float a, float top, float bottom) {
		return switch (setting) {
			case BoolSetting b -> toggleRow(g, px, cy, pw, b.name, b.get(), b::toggle, "drawer:" + m.id + b.id, a, top, bottom);
			case ChoiceSetting c -> choice(g, mouseX, mouseY, px, cy, pw, c, a, top, bottom);
			case NumberSetting n -> slider(g, px, cy, pw, n, a, top, bottom);
			case ColorSetting c -> colorRow(g, mouseX, mouseY, px, cy, pw, c, a, top, bottom);
			case TextSetting t -> textRow(g, mouseX, mouseY, px, cy, pw, t, a, top, bottom);
		};
	}

	/** One-click looks for this HUD element (each module keeps its own). */
	private float looks(Gfx g, float mouseX, float mouseY, float x, float y, float w, HudModule hud, float a, float top, float bottom) {
		Ui.textAt(g, "Quick look", x, y + 14 + 13.5f * 1.038f - 7, Theme.alpha(Theme.TEXT, a), Ui.Font.UI_13_5_SB);
		float cx = x, cy = y + 32;
		for (String look : new String[] {"Default", "Clean", "Boxed", "Outline"}) {
			float bw = 12 + Ui.width(look, Ui.Font.UI_12_5_M) + 12;
			boolean over = Theme.inside(mouseX, mouseY, cx, cy, cx + bw, cy + 28);
			Ui.round(g, cx, cy, bw, 28, 14, Theme.alpha(over ? 0xFFE6EBE4 : 0xFFEFF1EC, a));
			Ui.textAt(g, look, cx + 12, Ui.mid(cy + 14, 12.5f), Theme.alpha(Theme.TEXT, a), Ui.Font.UI_12_5_M);
			if (cy + 28 > top && cy < bottom) hit(cx, cy, cx + bw, cy + 28, e -> {
				click();
				hud.applyLook(look);
			});
			cx += bw + 6;
		}
		cy += 28;
		Ui.round(g, x, cy + 13, w, 1, 0.5f, Theme.alpha(Theme.alpha(0xFF1F2A22, 0.08f), a));
		return cy + 14;
	}

	/** Module-specific panels below the settings (the waypoint list). */
	private float extras(Gfx g, float mouseX, float mouseY, float x, float y, float w, Module m, float a, float top, float bottom) {
		if (m instanceof net.tuffclient.module.util.WaypointsModule wp) return waypointList(g, mouseX, mouseY, x, y, w, wp, a, top, bottom);
		return y;
	}

	private float waypointList(Gfx g, float mouseX, float mouseY, float x, float y, float w, net.tuffclient.module.util.WaypointsModule wp, float a, float top, float bottom) {
		y += 18;
		Ui.spaced(g, "WAYPOINTS HERE", x, y + 10.5f * 1.005f - 7, Theme.alpha(Theme.MUTED, a), Ui.Font.MONO_10_5, 1.26f);
		y += 18;
		var list = wp.here();
		if (list.isEmpty()) {
			Ui.textAt(g, "None yet. Press " + wp.addKeyName() + " in game to add one.", x, y + 14, Theme.alpha(Theme.MUTED, a), Ui.Font.UI_12_5);
			return y + 28;
		}
		for (var p : list) {
			boolean over = Theme.inside(mouseX, mouseY, x, y, x + w, y + 40);
			if (over) Ui.round(g, x - 6, y + 2, w + 12, 36, 10, Theme.alpha(0x59FFFFFF, a));
			Ui.circle(g, x + 7, y + 20, 6, Theme.alpha(p.color | 0xFF000000, p.visible ? a : a * 0.35f));
			Ui.textAt(g, Ui.ellipsize(p.name, Ui.Font.UI_13_5_SB, (int) (w - 110)), x + 22, y + 18, Theme.alpha(Theme.TEXT, p.visible ? a : a * 0.5f), Ui.Font.UI_13_5_SB);
			Ui.textAt(g, p.x + " " + p.y + " " + p.z, x + 22, y + 32, Theme.alpha(Theme.MUTED, a), Ui.Font.MONO_10_5);
			float ex = x + w - 50, dx2 = x + w - 20;
			Ui.lucide(g, p.visible ? "eye" : "eye_off", ex, y + 13, 14, Theme.alpha(Theme.mix(Theme.MUTED, Theme.TEXT, over ? 1 : 0), a));
			Ui.lucide(g, "trash", dx2, y + 13, 14, Theme.alpha(Theme.mix(Theme.MUTED, Theme.DANGER, over ? 1 : 0), a));
			if (y + 40 > top && y < bottom) {
				hit(ex - 6, y + 6, ex + 20, y + 34, e -> {
					click();
					p.visible = !p.visible;
					wp.saveWaypoints();
				});
				hit(dx2 - 6, y + 6, dx2 + 20, y + 34, e -> {
					click();
					wp.remove(p);
				});
			}
			y += 40;
		}
		return y;
	}

	/** Checkerboard behind see-through colours. */
	private static void checker(Gfx g, float x, float y, float w, float h, float a) {
		int cell = 5;
		for (int yy = 0; yy * cell < h; yy++) {
			for (int xx = 0; xx * cell < w; xx++) {
				int c = (xx + yy) % 2 == 0 ? 0xFFFFFFFF : 0xFFD5D9D3;
				float x1 = x + xx * cell, y1 = y + yy * cell;
				fillF(g, x1, y1, Math.min(x + w, x1 + cell), Math.min(y + h, y1 + cell), Theme.alpha(c, a));
			}
		}
	}

	/** A filled rectangle at float coordinates (through the pose, so it doesn't snap). */
	private static void fillF(Gfx g, float x1, float y1, float x2, float y2, int color) {
		if (x2 <= x1 || y2 <= y1) return;
		g.push();
		g.translate(x1, y1);
		g.scale((x2 - x1) / 64f, (y2 - y1) / 64f);
		g.fill(0, 0, 64, 64, color);
		g.pop();
	}

	private float[] hsvOf(ColorSetting c) {
		float[] state = hsv.get(c);
		int argb = c.get();
		if (state == null || ColorSetting.fromHsv(state[0], state[1], state[2], argb >>> 24) != argb) {
			float[] fresh = ColorSetting.toHsv(argb);
			// keep the old hue for greys, where hue is meaningless
			if (state != null && fresh[1] < 0.001f) fresh[0] = state[0];
			state = fresh;
			hsv.put(c, state);
		}
		return state;
	}

	private void applyHsv(ColorSetting c, float[] state, int alpha) {
		c.set(ColorSetting.fromHsv(state[0], state[1], state[2], alpha));
		hsv.put(c, state);
	}

	private static final int[] SWATCHES = {0xFFFFFFFF, 0xFF1F2A22, 0xFF2F6B4F, 0xFF3BA57E, 0xFF3D8BD6, 0xFF8A62D6, 0xFFD25A75, 0xFFD0413D, 0xFFC9733E, 0xFFE3B23C};

	/** A colour: label, hex and swatch; click to open the wheel picker under it. */
	private float colorRow(Gfx g, float mouseX, float mouseY, float x, float y, float w, ColorSetting c, float a, float top, float bottom) {
		boolean open = openColor == c;
		Ui.textAt(g, c.name, x, Ui.mid(y + 22, 13.5f), Theme.alpha(Theme.TEXT, a), Ui.Font.UI_13_5_SB);
		float sw = 30, sh = 20, sx = x + w - sw, sy = y + 12;
		Ui.round(g, sx - 1, sy - 1, sw + 2, sh + 2, 7, Theme.alpha(0x26000000, a));
		checker(g, sx, sy, sw, sh, a);
		Ui.round(g, sx, sy, sw, sh, 6, Theme.alpha(c.argb(), a));
		Ui.outline(g, sx, sy, sw, sh, 6, Theme.alpha(0x33000000, a));
		String label = c.chroma ? "Chroma" : c.display();
		Ui.rightAt(g, label, sx - 10, Ui.mid(y + 22, 11.5f), Theme.alpha(Theme.MUTED, a), Ui.Font.MONO_11_5);
		Ui.lucide(g, "chevron", x + Ui.width(c.name, Ui.Font.UI_13_5_SB) + 6, y + 16, 12, Theme.alpha(Theme.MUTED, a * 0.8f));
		if (y + 44 > top && y < bottom) hit(x, Math.max(y, top), x + w, Math.min(y + 44, bottom), e -> {
			click();
			openColor = open ? null : c;
			editingHex = null;
		});
		float ny = y + 44;
		if (!open) {
			Ui.round(g, x, ny - 1, w, 1, 0.5f, Theme.alpha(Theme.alpha(0xFF1F2A22, 0.08f), a));
			return ny;
		}
		return picker(g, mouseX, mouseY, x, ny, w, c, a, top, bottom);
	}

	/** The colour wheel, brightness and opacity bars, preview with hex, chroma and swatches. */
	private float picker(Gfx g, float mouseX, float mouseY, float x, float y, float w, ColorSetting c, float a, float top, float bottom) {
		float[] st = hsvOf(c);
		int alpha = c.get() >>> 24;
		boolean live = y + 200 > top && y < bottom;

		// wheel: hue around, saturation outwards; tinted grey for brightness
		float size = 132, R = size / 2, wcx = x + R, wcy = y + R;
		int grey = Math.round(st[2] * 255);
		Ui.circle(g, wcx, wcy + 1.5f, R + 1, Theme.alpha(0x22000000, a));
		Ui.image(g, WHEEL, x, y, size, size, 256, 256, Theme.alpha(0xFF000000 | grey << 16 | grey << 8 | grey, a));
		double ang = st[0] * Math.PI * 2;
		float kx = wcx + (float) Math.cos(ang) * st[1] * R, ky = wcy - (float) Math.sin(ang) * st[1] * R;
		Ui.circle(g, kx, ky + 1, 8, Theme.alpha(0x40000000, a));
		Ui.circle(g, kx, ky, 7.5f, Theme.alpha(0xFFFFFFFF, a));
		Ui.circle(g, kx, ky, 5.5f, Theme.alpha(c.get() | 0xFF000000, a));
		if (live) hit(x, Math.max(y, top), x + size, Math.min(y + size, bottom), e -> {
			drag = (mx, my) -> {
				double dx = mx - wcx, dy = my - wcy;
				float[] s2 = hsvOf(c).clone();
				s2[0] = (float) (((Math.atan2(-dy, dx) / (Math.PI * 2)) % 1 + 1) % 1);
				s2[1] = (float) Math.min(1, Math.hypot(dx, dy) / R);
				c.chroma = false;
				applyHsv(c, s2, c.get() >>> 24);
			};
			drag.accept(clickX, clickY);
		});

		// right column: preview with hex, chroma, swatches
		float rx = x + size + 16, rw = w - size - 16;
		checker(g, rx, y, rw, 34, a);
		Ui.round(g, rx, y, rw, 34, 9, Theme.alpha(c.argb(), a));
		Ui.outline(g, rx, y, rw, 34, 9, Theme.alpha(0x33000000, a));
		boolean editing = editingHex == c;
		String hex = editing ? hexBuffer + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : " ") : c.display();
		Ui.round(g, rx + 6, y + 8, rw - 12, 18, 6, Theme.alpha(editing ? 0xF5FFFFFF : 0xD9FFFFFF, a));
		Ui.centeredAt(g, hex, rx + rw / 2, Ui.mid(y + 17, 11.5f), Theme.alpha(Theme.TEXT, a), Ui.Font.MONO_11_5);
		if (live) hit(rx, y, rx + rw, y + 34, e -> {
			click();
			editingHex = c;
			hexBuffer = c.display().substring(1);
			focusedText = null;
		});

		float cy = y + 44;
		float on = anim("chroma:" + c.id + System.identityHashCode(c), c.chroma, 14);
		Ui.round(g, rx, cy, 30, 18, 9, Theme.alpha(Theme.mix(0xFFE9ECE6, Theme.COPPER, on), a));
		Ui.circle(g, rx + 9 + 12 * on, cy + 9, 6, Theme.alpha(Theme.mix(Theme.MUTED, 0xFFFFFFFF, on), a));
		Ui.textAt(g, "Chroma", rx + 38, Ui.mid(cy + 9, 12.5f), Theme.alpha(Theme.TEXT, a), Ui.Font.UI_12_5_M);
		if (live) hit(rx, cy, rx + rw, cy + 18, e -> {
			click();
			c.chroma = !c.chroma;
		});

		cy += 28;
		float gap = (rw - 5 * 18) / 4;
		for (int i = 0; i < SWATCHES.length; i++) {
			float qx = rx + (i % 5) * (18 + gap), qy = cy + (i / 5) * 24;
			int color = SWATCHES[i];
			boolean picked = (c.get() | 0xFF000000) == color;
			Ui.circle(g, qx + 9, qy + 9, picked ? 9.5f : 9, Theme.alpha(picked ? Theme.COPPER : 0x33000000, a));
			Ui.circle(g, qx + 9, qy + 9, 8, Theme.alpha(color, a));
			if (live) hit(qx, qy, qx + 18, qy + 18, e -> {
				click();
				c.chroma = false;
				c.set((color & 0xFFFFFF) | (c.get() & 0xFF000000));
			});
		}
		float resetY = cy + 50;
		boolean overReset = Theme.inside(mouseX, mouseY, rx, resetY - 4, rx + rw, resetY + 14);
		Ui.textAt(g, "Reset", rx, Ui.mid(resetY + 5, 12), Theme.alpha(overReset ? Theme.TEXT : Theme.MUTED, a), Ui.Font.UI_12);
		if (live) hit(rx, resetY - 4, rx + 60, resetY + 14, e -> {
			click();
			c.reset();
			hsv.remove(c);
		});

		// brightness and opacity bars
		float by = y + size + 14;
		bar(g, x, by, w, st[2], 0xFF000000, ColorSetting.fromHsv(st[0], st[1], 1, 255), false, a);
		if (live) hit(x - 4, by - 4, x + w + 4, by + 16, e -> {
			drag = (mx, my) -> {
				float[] s2 = hsvOf(c).clone();
				s2[2] = (float) Math.max(0, Math.min(1, (mx - x) / w));
				applyHsv(c, s2, c.get() >>> 24);
			};
			drag.accept(clickX, clickY);
		});
		float end = by + 12;
		if (c.alpha) {
			float oy = by + 22;
			bar(g, x, oy, w, alpha / 255f, 0, c.get() | 0xFF000000, true, a);
			if (live) hit(x - 4, oy - 4, x + w + 4, oy + 16, e -> {
				drag = (mx, my) -> {
					int na = (int) Math.round(Math.max(0, Math.min(1, (mx - x) / w)) * 255);
					c.set((c.get() & 0xFFFFFF) | na << 24);
				};
				drag.accept(clickX, clickY);
			});
			end = oy + 12;
		}
		if (c.chroma) {
			float wy = end + 14;
			float won = anim("wave:" + System.identityHashCode(c), c.wave, 14);
			Ui.round(g, x, wy, 30, 18, 9, Theme.alpha(Theme.mix(0xFFE9ECE6, Theme.COPPER, won), a));
			Ui.circle(g, x + 9 + 12 * won, wy + 9, 6, Theme.alpha(Theme.mix(Theme.MUTED, 0xFFFFFFFF, won), a));
			Ui.textAt(g, "Wave", x + 38, Ui.mid(wy + 9, 12.5f), Theme.alpha(Theme.TEXT, a), Ui.Font.UI_12_5_M);
			if (live) hit(x, wy, x + 90, wy + 18, e -> {
				click();
				c.wave = !c.wave;
			});
			String[] speeds = {"Slow", "Normal", "Fast"};
			float sx = x + w;
			for (int i = speeds.length - 1; i >= 0; i--) {
				float bw = 10 + Ui.width(speeds[i], Ui.Font.UI_12_5_M) + 10;
				sx -= bw;
				boolean picked = c.speed == i;
				Ui.round(g, sx, wy - 3, bw, 24, 12, Theme.alpha(picked ? Theme.COPPER : 0xFFEFF1EC, a));
				Ui.textAt(g, speeds[i], sx + 10, Ui.mid(wy + 9, 12.5f), Theme.alpha(picked ? Theme.INK : Theme.TEXT, a), Ui.Font.UI_12_5_M);
				final int index = i;
				if (live) hit(sx, wy - 3, sx + bw, wy + 21, e -> {
					click();
					c.speed = index;
				});
				sx -= 4;
			}
			end = wy + 18;
		}
		Ui.round(g, x, end + 13, w, 1, 0.5f, Theme.alpha(Theme.alpha(0xFF1F2A22, 0.08f), a));
		return end + 14;
	}

	private static final Identifier WHEEL = Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "textures/gui/color_wheel.png");
	private static final Identifier RAMP = Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "textures/gui/ramp.png");

	/** A 12-tall bar fading from {@code from} to {@code to}, with a knob at {@code t}. */
	private void bar(Gfx g, float x, float y, float w, float t, int from, int to, boolean checkered, float a) {
		if (checkered) checker(g, x, y, w, 12, a);
		else fillF(g, x, y, x + w, y + 12, Theme.alpha(from, a));
		Ui.image(g, RAMP, x, y, w, 12, 256, 4, Theme.alpha(to, a));
		Ui.outline(g, x, y, w, 12, 3, Theme.alpha(0x33000000, a));
		float kx = x + w * t;
		Ui.circle(g, kx, y + 6.8f, 8, Theme.alpha(0x40000000, a));
		Ui.circle(g, kx, y + 6, 7.5f, Theme.alpha(0xFFFFFFFF, a));
		Ui.circle(g, kx, y + 6, 5, Theme.alpha(Theme.mix(from, to, t) | 0xFF000000, a));
	}

	/** A text field. */
	private float textRow(Gfx g, float mouseX, float mouseY, float x, float y, float w, TextSetting t, float a, float top, float bottom) {
		Ui.textAt(g, t.name, x, y + 14 + 13.5f * 1.038f - 7, Theme.alpha(Theme.TEXT, a), Ui.Font.UI_13_5_SB);
		float fy = y + 30;
		boolean focused = focusedText == t;
		Ui.round(g, x, fy, w, 30, 9, Theme.alpha(focused ? 0xF2FFFFFF : 0x99FFFFFF, a), Theme.alpha(focused ? Theme.alpha(Theme.COPPER, 0.6f) : 0x33000000, a), 1);
		String shown = t.get() + (focused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
		Ui.textAt(g, Ui.ellipsize(shown, Ui.Font.UI_12_5, (int) (w - 20)), x + 10, Ui.mid(fy + 15, 12.5f), Theme.alpha(Theme.TEXT, a), Ui.Font.UI_12_5);
		if (fy + 30 > top && fy < bottom) hit(x, fy, x + w, fy + 30, e -> {
			focusedText = t;
			editingHex = null;
		});
		return y + 72;
	}

	/** A switch row: label on the left, switch on the right, a hairline under it. */
	private float toggleRow(Gfx g, float x, float y, float w, String label, boolean value, Runnable toggle, String key, float a, float top, float bottom) {
		float on = anim("tog:" + key + label, value, 14);
		Ui.textAt(g, label, x, Ui.mid(y + 22, 13.5f), Theme.alpha(Theme.TEXT, a), Ui.Font.UI_13_5_SB);
		float sx = x + w - 34, sy = y + 12;
		Ui.round(g, sx, sy, 34, 20, 10, Theme.alpha(Theme.mix(0xFFE9ECE6, Theme.COPPER, on), a), Theme.alpha(Theme.mix(0xFFDDE2DA, Theme.COPPER, on), a), 1);
		Ui.circle(g, sx + 10 + 14 * on, sy + 10, 7, Theme.alpha(Theme.mix(Theme.MUTED, 0xFFFFFFFF, on), a));
		Ui.round(g, x, y + 44 - 1, w, 1, 0.5f, Theme.alpha(Theme.alpha(0xFF1F2A22, 0.08f), a));
		if (y + 44 > top && y < bottom) hit(x, Math.max(y, top), x + w, Math.min(y + 44, bottom), e -> {
			click();
			toggle.run();
		});
		return y + 44;
	}

	private float choice(Gfx g, float mouseX, float mouseY, float x, float y, float w, ChoiceSetting c, float a, float top, float bottom) {
		Ui.textAt(g, c.name, x, y + 14 + 13.5f * 1.038f - 7, Theme.alpha(Theme.TEXT, a), Ui.Font.UI_13_5_SB);
		float cx = x, cy = y + 32;
		for (int i = 0; i < c.options.length; i++) {
			String opt = c.options[i];
			float pw = 12 + Ui.width(opt, Ui.Font.UI_12_5_M) + 12;
			if (cx + pw > x + w) {
				cx = x;
				cy += 34;
			}
			boolean selected = c.get() == i;
			boolean over = Theme.inside(mouseX, mouseY, cx, cy, cx + pw, cy + 28);
			float act = anim("choice:" + c.id + i, selected, 16);
			Ui.round(g, cx, cy, pw, 28, 14, Theme.alpha(Theme.mix(over ? 0xFFE6EBE4 : 0xFFEFF1EC, Theme.COPPER, act), a));
			Ui.textAt(g, opt, cx + 12, Ui.mid(cy + 14, 12.5f), Theme.alpha(Theme.mix(Theme.TEXT, Theme.INK, act), a), Ui.Font.UI_12_5_M);
			final int index = i;
			if (cy + 28 > top && cy < bottom) hit(cx, Math.max(cy, top), cx + pw, Math.min(cy + 28, bottom), e -> {
				click();
				c.set(index);
				if (drawer == modules().ui) modules().ui.applyAccent();
			});
			cx += pw + 6;
		}
		return cy + 28 + 14;
	}

	private float slider(Gfx g, float x, float y, float w, NumberSetting n, float a, float top, float bottom) {
		Ui.textAt(g, n.name, x, y + 14 + 13.5f * 1.038f - 7, Theme.alpha(Theme.TEXT, a), Ui.Font.UI_13_5_SB);
		Ui.rightAt(g, n.display(), x + w, y + 14 + 11.5f * 1.005f - 7, Theme.alpha(Theme.COPPER, a), Ui.Font.MONO_11_5);
		float ty = y + 38, frac = (float) n.fraction();
		Ui.round(g, x, ty, w, 4, 2, Theme.alpha(0xFFDDE2DA, a));
		Ui.round(g, x, ty, Math.max(4, w * frac), 4, 2, Theme.alpha(Theme.COPPER, a));
		float kx = x + w * frac;
		Ui.circle(g, kx, ty + 2.6f, dragging == n ? 8.5f : 7.5f, Theme.alpha(0x331E3C28, a));
		Ui.circle(g, kx, ty + 2, dragging == n ? 8 : 7, Theme.alpha(0xFFFFFFFF, a));
		if (ty + 10 > top && ty - 10 < bottom) hit(x - 6, ty - 10, x + w + 6, ty + 14, e -> {
			dragging = n;
			dragX1 = x;
			dragX2 = x + w;
			drag = (mx, my) -> updateSlider(mx);
			updateSlider(clickX);
		});
		return y + 56;
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

	// ------------------------------------------------------------------ theme and settings pages

	/** A settings row in the list style: title, hint, and a switch or a button on the right. */
	private float optionRow(Gfx g, float mouseX, float mouseY, float x, float y, float w, String label, String hint, Boolean value, String button, Runnable action, float a) {
		boolean over = Theme.inside(mouseX, mouseY, x, y, x + w, y + ROW);
		float hv = anim("opt:" + label, over, 14);
		if (hv > 0.01f) Ui.round(g, x - 8, y + 4, w + 16, ROW - 8, 12, Theme.alpha(0x40FFFFFF, hv * a));
		Ui.round(g, x, y + ROW - 1, w, 1, 0.5f, Theme.alpha(0x99FFFFFF, a));
		Ui.textAt(g, label, x, y + 16.6f + 14 * 1.038f, Theme.alpha(Theme.TEXT, a), Ui.Font.UI_14_SB);
		Ui.textAt(g, Ui.ellipsize(hint, Ui.Font.UI_12, (int) (w - 160)), x, y + 16.6f + 17.64f + 12 * 1.038f, Theme.alpha(Theme.MUTED, a), Ui.Font.UI_12);
		if (value != null) {
			float on = anim("optT:" + label, value, 14);
			float sx = x + w - 34, sy = y + 23;
			Ui.round(g, sx, sy, 34, 20, 10, Theme.alpha(Theme.mix(0x99FFFFFF, Theme.COPPER, on), a), Theme.alpha(Theme.mix(0xCCFFFFFF, Theme.COPPER, on), a), 1);
			Ui.circle(g, sx + 10 + 14 * on, sy + 10, 7, Theme.alpha(Theme.mix(Theme.MUTED, 0xFFFFFFFF, on), a));
			hit(x, y, x + w, y + ROW, e -> {
				click();
				action.run();
			});
		} else {
			float bw = 18 + Ui.width(button, Ui.Font.UI_13_5_SB) + 18, bx = x + w - bw, by = y + 14;
			boolean overB = Theme.inside(mouseX, mouseY, bx, by, bx + bw, by + 38);
			Ui.round(g, bx, by, bw, 38, 10, Theme.alpha(overB ? 0xE6FFFFFF : 0x8CFFFFFF, a), Theme.alpha(0xBFFFFFFF, a), 1);
			Ui.centeredAt(g, button, bx + bw / 2, Ui.mid(by + 19, 13.5f), Theme.alpha(Theme.TEXT, a), Ui.Font.UI_13_5_SB);
			hit(bx, by, bx + bw, by + 38, e -> {
				click();
				action.run();
			});
		}
		return y + ROW;
	}

	private void theme(Gfx g, float mouseX, float mouseY, float x, float y, float w, float a) {
		Ui.spaced(g, "ACCENT", x, y + 10.5f * 1.005f - 7, Theme.alpha(Theme.MUTED, a), Ui.Font.MONO_10_5, 1.26f);
		Theme.Accent[] accents = Theme.Accent.values();
		float sw = (w - 10 * (accents.length - 1)) / accents.length, sy = y + 22;
		for (int i = 0; i < accents.length; i++) {
			Theme.Accent acc = accents[i];
			float sx = x + i * (sw + 10);
			boolean selected = modules().ui.accent.get() == i;
			boolean over = Theme.inside(mouseX, mouseY, sx, sy, sx + sw, sy + 84);
			float act = anim("acc:" + i, selected, 14), hv = anim("accH:" + i, over, 16);
			Ui.round(g, sx, sy, sw, 84, 14, Theme.alpha(Theme.mix(0x66FFFFFF, 0xCCFFFFFF, Math.max(act, hv)), a), Theme.alpha(Theme.mix(0xB3FFFFFF, acc.base, act), a), 1);
			Ui.circle(g, sx + sw / 2, sy + 32, 15, Theme.alpha(acc.base, a));
			if (act > 0.05f) Ui.lucide(g, "check", sx + sw / 2 - 8, sy + 24, 16, Theme.alpha(0xFFFFFFFF, act * a));
			Ui.centeredAt(g, acc.label, sx + sw / 2, sy + 68, Theme.alpha(Theme.mix(Theme.MUTED, Theme.TEXT, act), a), Ui.Font.UI_12_5_M);
			final int index = i;
			hit(sx, sy, sx + sw, sy + 84, e -> {
				click();
				modules().ui.accent.set(index);
				modules().ui.applyAccent();
			});
		}
		float cy = sy + 84 + 18;
		cy = optionRow(g, mouseX, mouseY, x, cy, w, "Background blur", "Blur the game behind Glade menus.", modules().ui.blur.get(), null, modules().ui.blur::toggle, a);
		optionRow(g, mouseX, mouseY, x, cy, w, "Menu animations", "Smooth transitions, hovers and scrolling.", modules().ui.animations.get(), null, modules().ui.animations::toggle, a);
	}

	private void settings(Gfx g, float mouseX, float mouseY, float x, float y, float w, float a) {
		var title = modules().titleScreen;
		float cy = y - 10;
		cy = optionRow(g, mouseX, mouseY, x, cy, w, "Glade title screen", "Use the Glade main menu instead of the vanilla one.", title.isEnabled(), null, title::toggle, a);
		cy = optionRow(g, mouseX, mouseY, x, cy, w, "Animated background", "Drifting clouds, a moving sun and swaying trees.", title.animate.get(), null, title.animate::toggle, a);
		cy = optionRow(g, mouseX, mouseY, x, cy, w, "Keybinds", "Menu is Right Shift, zoom is C by default.", null, "Open", () -> minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options)), a);
		cy = optionRow(g, mouseX, mouseY, x, cy, w, "HUD layout", "Move every HUD module back to its default spot.", null, "Reset", () -> {
			for (HudModule m : modules().hud()) m.resetPosition();
			TuffConfig.save(modules());
		}, a);
		Ui.textAt(g, "Settings are saved to config/tuff/config.json", x, cy + 30, Theme.alpha(Theme.MUTED, a), Ui.Font.MONO_11_5);
	}

	// ------------------------------------------------------------------ packs page

	private void loadPacks() {
		PackRepository repo = minecraft.getResourcePackRepository();
		repo.reload();
		enabledPacks = new ArrayList<>(repo.getSelectedIds());
		Collections.reverse(enabledPacks);
		availablePacks = new ArrayList<>();
		for (String id : repo.getAvailableIds()) if (!enabledPacks.contains(id)) availablePacks.add(id);
		packsDirty = false;
	}

	private void applyPacks() {
		PackRepository repo = minecraft.getResourcePackRepository();
		List<String> bottomFirst = new ArrayList<>(enabledPacks);
		Collections.reverse(bottomFirst);
		repo.setSelected(bottomFirst);
		minecraft.options.updateResourcePacks(repo);
		packsDirty = false;
	}

	private void packs(Gfx g, float mouseX, float mouseY, float x, float y, float w, float h, float a) {
		if (enabledPacks == null) loadPacks();
		PackRepository repo = minecraft.getResourcePackRepository();
		float colW = (w - GUTTER) / 2, listTop = y + 22, bottom = y + h - 50;
		Ui.spaced(g, "ENABLED", x, y + 10.5f * 1.005f - 7, Theme.alpha(Theme.MUTED, a), Ui.Font.MONO_10_5, 1.26f);
		Ui.spaced(g, "AVAILABLE", x + colW + GUTTER, y + 10.5f * 1.005f - 7, Theme.alpha(Theme.MUTED, a), Ui.Font.MONO_10_5, 1.26f);
		int rows = Math.max(enabledPacks.size(), availablePacks.size());
		maxScroll = Math.max(0, rows * 58 - (bottom - listTop));
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		g.enableScissor(Math.round(x - 10), Math.round(listTop), Math.round(x + w + 10), Math.round(bottom));
		for (int i = 0; i < enabledPacks.size(); i++) packRow(g, mouseX, mouseY, repo, enabledPacks.get(i), x, listTop + i * 58 - scroll, colW, true, i, a, listTop, bottom);
		for (int i = 0; i < availablePacks.size(); i++) packRow(g, mouseX, mouseY, repo, availablePacks.get(i), x + colW + GUTTER, listTop + i * 58 - scroll, colW, false, i, a, listTop, bottom);
		g.disableScissor();

		float by = y + h - 38;
		String more = "Open pack folder & more";
		float mw = 18 + Ui.width(more, Ui.Font.UI_13_5_SB) + 18;
		boolean overMore = Theme.inside(mouseX, mouseY, x, by, x + mw, by + 38);
		Ui.round(g, x, by, mw, 38, 10, Theme.alpha(overMore ? 0xE6FFFFFF : 0x8CFFFFFF, a), Theme.alpha(0xBFFFFFFF, a), 1);
		Ui.centeredAt(g, more, x + mw / 2, Ui.mid(by + 19, 13.5f), Theme.alpha(Theme.TEXT, a), Ui.Font.UI_13_5_SB);
		hit(x, by, x + mw, by + 38, e -> {
			click();
			if (packsDirty) applyPacks();
			minecraft.gui.setScreen(new PackSelectionScreen(repo, r -> {
				minecraft.options.updateResourcePacks(r);
				enabledPacks = null;
				minecraft.gui.setScreen(this);
			}, minecraft.getResourcePackDirectory(), Component.literal("Resource packs")));
		});
		String apply = packsDirty ? "Apply changes" : "Up to date";
		float aw = 18 + Ui.width(apply, Ui.Font.UI_13_5_SB) + 18, ax = x + w - aw;
		boolean overApply = packsDirty && Theme.inside(mouseX, mouseY, ax, by, ax + aw, by + 38);
		if (packsDirty) Ui.round(g, ax, by, aw, 38, 10, Theme.alpha(overApply ? Theme.COPPER_HI : Theme.COPPER, a));
		else Ui.round(g, ax, by, aw, 38, 10, Theme.alpha(0x59FFFFFF, a), Theme.alpha(0x99FFFFFF, a), 1);
		Ui.centeredAt(g, apply, ax + aw / 2, Ui.mid(by + 19, 13.5f), Theme.alpha(packsDirty ? Theme.INK : Theme.MUTED, a), Ui.Font.UI_13_5_SB);
		if (packsDirty) hit(ax, by, ax + aw, by + 38, e -> {
			click();
			applyPacks();
		});
	}

	private void packRow(Gfx g, float mouseX, float mouseY, PackRepository repo, String id, float x, float y, float w, boolean enabled, int index, float a, float top, float bottom) {
		if (y + 58 < top || y > bottom) return;
		Pack pack = repo.getPack(id);
		String title = pack == null ? id : pack.getTitle().getString();
		String desc = pack == null ? "" : pack.getDescription().getString().replace('\n', ' ');
		boolean required = pack != null && pack.isRequired();
		boolean fixed = pack != null && pack.isFixedPosition();
		boolean over = Theme.inside(mouseX, mouseY, x, y, x + w, y + 58) && mouseY >= top && mouseY < bottom;
		float hv = anim("pack:" + enabled + id, over, 16);
		if (hv > 0.01f) Ui.round(g, x - 8, y + 4, w + 16, 50, 12, Theme.alpha(0x40FFFFFF, hv * a));
		Ui.round(g, x, y + 57, w, 1, 0.5f, Theme.alpha(0x99FFFFFF, a));
		float textW = w - (required ? Ui.width("Built-in", Ui.Font.UI_12) + 16 : enabled ? 90 : 40);
		Ui.textAt(g, Ui.ellipsize(title, Ui.Font.UI_13_5_SB, (int) textW), x, y + 13 + 13.5f * 1.038f, Theme.alpha(Theme.TEXT, a), Ui.Font.UI_13_5_SB);
		Ui.textAt(g, Ui.ellipsize(desc, Ui.Font.UI_11_5, (int) textW), x, y + 13 + 17 + 11.5f * 1.038f, Theme.alpha(Theme.MUTED, a), Ui.Font.UI_11_5);
		if (required) {
			Ui.rightAt(g, "Built-in", x + w, Ui.mid(y + 29, 12), Theme.alpha(Theme.MUTED, a), Ui.Font.UI_12);
			return;
		}
		float bx = x + w - 24;
		iconButton(g, mouseX, mouseY, enabled ? "close" : "plus", bx, y + 17, a, top, bottom, () -> {
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
			if (index > 0) iconButton(g, mouseX, mouseY, "up", bx - 56, y + 17, a, top, bottom, () -> {
				Collections.swap(enabledPacks, index, index - 1);
				packsDirty = true;
			});
			if (index < enabledPacks.size() - 1) iconButton(g, mouseX, mouseY, "down", bx - 28, y + 17, a, top, bottom, () -> {
				Collections.swap(enabledPacks, index, index + 1);
				packsDirty = true;
			});
		}
	}

	private void iconButton(Gfx g, float mouseX, float mouseY, String icon, float x, float y, float a, float top, float bottom, Runnable action) {
		boolean over = Theme.inside(mouseX, mouseY, x, y, x + 24, y + 24) && mouseY >= top && mouseY < bottom;
		if (over) Ui.round(g, x, y, 24, 24, 8, Theme.alpha(0xB3FFFFFF, a));
		Ui.lucide(g, icon, x + 5, y + 5, 14, Theme.alpha(over ? Theme.COPPER : Theme.MUTED, a));
		if (y + 24 > top && y < bottom) hit(x, y, x + 24, y + 24, e -> {
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
		clickY = y;
		// a click anywhere ends typing; a click on a field starts it again below
		focusedText = null;
		if (editingHex != null) commitHex();
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (Theme.inside(x, y, h.x1(), h.y1(), h.x2(), h.y2())) {
				h.action().accept(event);
				return true;
			}
		}
		if (!Theme.inside(x, y, px(), PY, px() + PW, PY + PH)) {
			onClose();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (drag != null) {
			drag.accept(event.x() / zoom(), event.y() / zoom());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		drag = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (drawer != null && mouseX / zoom() > drawerLeft) {
			drawerScroll = (float) Math.max(0, Math.min(drawerMax, drawerScroll - scrollY * 30));
		} else {
			scrollTarget = (float) Math.max(0, Math.min(maxScroll, scrollTarget - scrollY * 50));
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (focusedText != null) {
			if (event.isEscape() || event.key() == InputConstants.KEY_RETURN) focusedText = null;
			else if (event.key() == InputConstants.KEY_BACKSPACE && !focusedText.get().isEmpty()) {
				focusedText.set(focusedText.get().substring(0, focusedText.get().length() - 1));
			}
			return true;
		}
		if (editingHex != null) {
			if (event.isEscape()) editingHex = null;
			else if (event.key() == InputConstants.KEY_RETURN) commitHex();
			else if (event.key() == InputConstants.KEY_BACKSPACE && !hexBuffer.isEmpty()) hexBuffer = hexBuffer.substring(0, hexBuffer.length() - 1);
			return true;
		}
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
		if (focusedText != null) {
			if (event.isAllowedChatCharacter()) focusedText.set(focusedText.get() + event.codepointAsString());
			return true;
		}
		if (editingHex != null) {
			String ch = event.codepointAsString();
			if (ch.matches("[0-9a-fA-F]") && hexBuffer.length() < 8) hexBuffer += ch.toUpperCase();
			return true;
		}
		if (page == Page.MODS && drawer == null && event.isAllowedChatCharacter() && query.length() < 32) {
			if (query.isEmpty() && event.codepointAsString().isBlank()) return true;
			query += event.codepointAsString();
			scrollTarget = 0;
			return true;
		}
		return super.charTyped(event);
	}

	private void commitHex() {
		Integer parsed = ColorSetting.parse(hexBuffer);
		if (parsed != null && editingHex != null) {
			// six digits keep the current opacity
			int value = hexBuffer.length() == 8 ? parsed : (parsed & 0xFFFFFF) | (editingHex.get() & 0xFF000000);
			editingHex.chroma = false;
			editingHex.set(value);
			hsv.remove(editingHex);
		}
		editingHex = null;
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
